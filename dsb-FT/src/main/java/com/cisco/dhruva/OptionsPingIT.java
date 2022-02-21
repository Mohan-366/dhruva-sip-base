package com.cisco.dhruva;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.cisco.dhruva.util.DhruvaSipPhone;
import com.cisco.dhruva.util.Token;
import java.text.ParseException;
import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.header.CallIdHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.cafesip.sipunit.SipTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

public class OptionsPingIT extends DhruvaIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(OptionsPingIT.class);

  DhruvaTestProperties testPro = new DhruvaTestProperties();
  private String testHost = testPro.getTestAddress();
  private int testUdpPort = testPro.getTestUdpPort();
  private int testTcpPort = testPro.getTestTcpPort();
  private int testTlsPort = testPro.getTestTlsPort();

  private String dhruvaHost = testPro.getDhruvaHost();
  private int dhruvaUdpPort = testPro.getDhruvaUdpPort();
  private int dhruvaTcpPort = testPro.getDhruvaTcpPort();
  private int dhruvaTlsPort = testPro.getDhruvaTlsPort();

  private String optionsPingUrlUdp = dhruvaHost + Token.COLON + dhruvaUdpPort;
  private String optionsPingUrlTcp = dhruvaHost + Token.COLON + dhruvaTcpPort;
  private String optionsPingUrlTls = dhruvaHost + Token.COLON + dhruvaTlsPort;

  private int timeOutValue = 10000;

  int sendOptions(DhruvaSipPhone phone, String optionsReqUri)
      throws ParseException, InvalidArgumentException {

    Request option = phone.getParent().getMessageFactory().createRequest(optionsReqUri);

    AddressFactory addressFactory = phone.getParent().getAddressFactory();
    HeaderFactory headerFactory = phone.getParent().getHeaderFactory();
    CallIdHeader callIdHeader = phone.getParent().getSipProvider().getNewCallId();

    option.addHeader(callIdHeader);
    option.addHeader(headerFactory.createCSeqHeader((long) 1, Request.OPTIONS));
    option.addHeader(headerFactory.createFromHeader(phone.getAddress(), phone.generateNewTag()));

    Address toAddress =
        addressFactory.createAddress(
            addressFactory.createURI(Token.SIP_COLON + "service@" + optionsPingUrlTcp));
    option.addHeader(headerFactory.createToHeader(toAddress, null));

    option.addHeader(headerFactory.createContactHeader(phone.getAddress()));
    option.addHeader(headerFactory.createMaxForwardsHeader(1));

    LOGGER.info("Created option request is: " + option);
    SipTransaction transaction = phone.sendRequestWithTransaction(option, false, null);
    assertNotNull(transaction);

    ResponseEvent responseEvent = (ResponseEvent) phone.waitResponse(transaction, timeOutValue);
    assertNotNull(responseEvent);

    return responseEvent.getResponse().getStatusCode();
  }

  @Test
  void testOptionsPingTcp() throws Exception {
    DhruvaSipPhone phone =
        new DhruvaSipPhone(
            sipStackService.getSipStackTcp(),
            testHost,
            Token.TCP,
            testTcpPort,
            "sip:sipptest@" + testHost);
    String optionsReqUri =
        Request.OPTIONS + " " + Token.SIP_COLON + optionsPingUrlTcp + " SIP/2.0\r\n\r\n";

    assertEquals(sendOptions(phone, optionsReqUri), Response.OK);
  }
}
