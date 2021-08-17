package com.cisco.dsb.proxy.util;

import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.RecordRoute;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.header.ViaList;
import java.security.SecureRandom;
import java.text.ParseException;
import javax.sip.InvalidArgumentException;

public class HeaderHelper {
  public static void addRandomVia(ViaList viaList, int count)
      throws ParseException, InvalidArgumentException {
    SecureRandom random = new SecureRandom();
    for (int i = 0; i < count; i++) {
      Via via1 = new Via();
      String host = String.valueOf(random.nextInt(100));
      via1.setHost("test" + host + ".webex.com");
      via1.setPort(5060);
      viaList.addFirst(via1);
    }
  }

  public static RecordRoute getRecordRoute(String User, String host, int port, String transport)
      throws ParseException {
    RecordRoute recordRoute = new RecordRoute();
    AddressImpl address = new AddressImpl();
    SipUri sipUri = new SipUri();
    sipUri.setUser(User);
    sipUri.setHost(host);
    sipUri.setPort(port);
    sipUri.setTransportParam(transport);
    address.setURI(sipUri);
    recordRoute.setAddress(address);
    return recordRoute;
  }
}
