package com.cisco.dhruva.validator;

import static com.cisco.dhruva.util.Constants.*;
import static org.cafesip.sipunit.SipAssert.assertHeaderContains;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.cisco.dhruva.input.TestInput;
import com.cisco.dhruva.input.TestInput.Type;
import com.cisco.dhruva.user.UAC;
import com.cisco.dhruva.user.UAS;
import com.cisco.dhruva.util.TestMessage;
import gov.nist.javax.sip.header.SIPHeader;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import javax.sip.header.Header;
import javax.sip.message.Message;
import org.cafesip.sipunit.SipMessage;
import org.cafesip.sipunit.SipRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Validator {

  public static final Logger TEST_LOGGER = LoggerFactory.getLogger(Validator.class);

  private UAC uac;
  private List<UAS> uasList;

  public Validator(UAC uac, List<UAS> uasList) {
    this.uac = uac;
    this.uasList = uasList;
  }

  public void validate() {
    TEST_LOGGER.info("Validating UAC");
    validate(uac.getTestMessages());
    uasList.stream()
        .forEach(
            uas -> {
              try {
                TEST_LOGGER.info("Validating UAS");
                validate(uas.getTestMessages());
              } catch (Exception e) {
                TEST_LOGGER.error(e.getMessage());
              }
            });
  }

  private void validate(List<TestMessage> testMessages) {
    testMessages.forEach(
        testMessage -> {
          TestInput.Message msg = testMessage.getMessage();
          SipMessage sipMsg = testMessage.getSipMessage();
          Map<String, Object> validationsToDo = msg.getValidation();
          if (validationsToDo == null || validationsToDo.isEmpty()) {
            return;
          }
          TEST_LOGGER.info("============ VALIDATING SIP MESSAGE ========\n{}", sipMsg.toString());
          validationsToDo.entrySet().stream()
              .forEach(
                  entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue().toString();
                    TEST_LOGGER.info("============ FOR ===============\n{}", entry);
                    // since call flow is already validated that is why we are here, we won't
                    // validate it again.
                    if (!key.equalsIgnoreCase(RESPONSE_CODE)
                        && !key.equalsIgnoreCase(REASON_PHRASE)) {
                      if (key.equalsIgnoreCase(REQUEST_URI)) {
                        if (msg.getType().equals(Type.request)) {
                          TEST_LOGGER.debug("Validating 'Request-uri'...");
                          SipRequest request = (SipRequest) sipMsg;
                          assertEquals(
                              request.getRequestURI(),
                              entry.getValue(),
                              "Request-uri validation failed");
                        }
                      } else if (key.equalsIgnoreCase(DIVERSION)) {
                        TEST_LOGGER.debug("Validating 'Diversion' header...");
                        Message message = sipMsg.getMessage();
                        ListIterator<SIPHeader> diversions = message.getHeaders(DIVERSION);
                        StringBuilder divFromMsgToValidate = new StringBuilder();
                        while (diversions.hasNext()) {
                          if (divFromMsgToValidate.length() == 0) {
                            divFromMsgToValidate.append(diversions.next().getHeaderValue());
                          }
                          divFromMsgToValidate
                              .append(",")
                              .append(diversions.next().getHeaderValue());
                        }
                        assertEquals(
                            divFromMsgToValidate.toString(),
                            value,
                            "Diversion header validation failed");
                      } else {
                        TEST_LOGGER.debug("Validating '{}' header...", key);
                        Header header = sipMsg.getMessage().getHeader(key);
                        assertNotNull(header, "No header found in the message for: " + key);
                        assertHeaderContains("Header assertion failed", sipMsg, key, value);
                      }
                    }
                    TEST_LOGGER.info("============ IT'S VALID ============");
                  });
        });
  }
}
