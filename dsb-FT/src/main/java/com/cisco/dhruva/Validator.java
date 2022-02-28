package com.cisco.dhruva;

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
                System.out.println("Validating UAS");
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
                    System.out.println(
                        "Validating Sip Message: \n"
                            + testMessage.getSipMessage().toString()
                            + " against: \n"
                            + testMessage.getMessage().getValidation().entrySet());
                    if (!key.equals("responseCode") && !key.equals("reasonPhrase")) {
                      if (key.equals("requestUri")) {
                        if (testMessage.getMessage().getType().equals(Type.request)) {
                          SipRequest request = (SipRequest) testMessage.getSipMessage();
                          Assert.assertEquals(request.getRequestURI(), entry.getValue());
                        }
                        return;
                      }
                      System.out.println("Validating header: " + entry.getKey());
                      Header header =
                          testMessage.getSipMessage().getMessage().getHeader(entry.getKey());
                      if (header == null) {
                        System.out.println("No header found in the message for: " + entry.getKey());
                        Assert.fail();
                      }
                      Assert.assertEquals(
                          header.toString().split(": ")[1].trim(),
                          entry.getValue().toString().trim());
                    }
                    System.out.println("It's valid");
                  });
        });
  }
}
