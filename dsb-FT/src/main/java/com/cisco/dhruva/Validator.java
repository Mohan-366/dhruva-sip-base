package com.cisco.dhruva;

import static com.cisco.dhruva.util.FTLog.FT_LOGGER;

import com.cisco.dhruva.util.TestInput.Type;
import com.cisco.dhruva.util.TestMessage;
import com.cisco.dhruva.util.UAC;
import com.cisco.dhruva.util.UAS;
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
                FT_LOGGER.info("Validating UAS");
                validate(uas.getTestMessages());
              } catch (Exception e) {
                e.printStackTrace();
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
                    // since call flow is already validated that is why we are here, we won't
                    // validate it again.
                    FT_LOGGER.info(
                        "============ VALIDATING SIP MESSAGE ========:\n{}",
                        testMessage.getSipMessage().toString());
                    FT_LOGGER.info(" ============ AGAINST ===============: {}", entry);
                    if (!key.equals("responseCode") && !key.equals("reasonPhrase")) {
                      if (key.equals("requestUri")) {
                        if (testMessage.getMessage().getType().equals(Type.request)) {
                          SipRequest request = (SipRequest) testMessage.getSipMessage();
                          Assert.assertEquals(request.getRequestURI(), entry.getValue());
                        }
                        return;
                      }
                      FT_LOGGER.info("Validating header: {}", entry.getKey());
                      Header header =
                          testMessage.getSipMessage().getMessage().getHeader(entry.getKey());
                      if (header == null) {
                        FT_LOGGER.error("No header found in the message for: {}", entry.getKey());
                        Assert.fail();
                      }
                      Assert.assertEquals(
                          header.toString().split(": ")[1].trim(),
                          entry.getValue().toString().trim());
                    }
                    FT_LOGGER.info("============ IT'S VALID ============");
                  });
        });
  }
}
