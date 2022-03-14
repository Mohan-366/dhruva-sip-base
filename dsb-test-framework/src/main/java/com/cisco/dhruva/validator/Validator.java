package com.cisco.dhruva.validator;

import static com.cisco.dhruva.util.TestLog.TEST_LOGGER;

import com.cisco.dhruva.input.TestInput.Type;
import com.cisco.dhruva.user.UAC;
import com.cisco.dhruva.user.UAS;
import com.cisco.dhruva.util.TestMessage;
import java.util.List;
import javax.sip.header.Header;
import org.cafesip.sipunit.SipRequest;
import org.testng.Assert;

public class Validator {
  private UAC uac;
  private List<UAS> uasList;

  public Validator(UAC uac, List<UAS> uasList) {
    this.uac = uac;
    this.uasList = uasList;
  }

  public void validate() throws Exception {
    System.out.println("Validating UAC");
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

  private void validate(List<TestMessage> testMessages) throws Exception {
    testMessages.forEach(
        testMessage -> {
          if (testMessage.getMessage().getValidation() == null
              || testMessage.getMessage().getValidation().isEmpty()) {
            return;
          }
          testMessage.getMessage().getValidation().entrySet().stream()
              .forEach(
                  entry -> {
                    String key = entry.getKey();
                    TEST_LOGGER.info(
                        "============ VALIDATING SIP MESSAGE ========:\n{}",
                        testMessage.getSipMessage().toString());
                    TEST_LOGGER.info(" ============ AGAINST ===============: {}", entry);
                    // since call flow is already validated that is why we are here, we won't
                    // validate it again.
                    if (!key.equals("responseCode") && !key.equals("reasonPhrase")) {
                      if (key.equals("requestUri")) {
                        if (testMessage.getMessage().getType().equals(Type.request)) {
                          SipRequest request = (SipRequest) testMessage.getSipMessage();
                          Assert.assertEquals(request.getRequestURI(), entry.getValue());
                          TEST_LOGGER.info("============ IT'S VALID ============");
                        }
                        return;
                      }
                      TEST_LOGGER.info("Validating header: {}", entry.getKey());
                      Header header =
                          testMessage.getSipMessage().getMessage().getHeader(entry.getKey());
                      if (header == null) {
                        TEST_LOGGER.error("No header found in the message for: {}", entry.getKey());
                        Assert.fail();
                      }
                      Assert.assertEquals(
                          header.toString().split(": ")[1].trim(),
                          entry.getValue().toString().trim());
                    }
                    TEST_LOGGER.info("============ IT'S VALID ============");
                  });
        });
  }
}
