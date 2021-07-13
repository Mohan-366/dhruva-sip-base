package com.cisco.dsb.sip.stack.dto;

import com.cisco.dsb.sip.enums.SipServiceType;
import com.cisco.dsb.transport.Transport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.javax.sip.stack.ClientAuthType;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SipListenPointTest {

  @Test(
      description =
          "Sip listen point construction from input json. Addition of new SipServiceType to existing set & its verification")
  public void getListenPointsFromJSON() throws JsonProcessingException {
    String json =
        "{ \"name\": \"jsonDefault\", \"alias\": \"aliasDefault\", \"ip\": \"1.1.1.1\", \"port\": 5061, \"transport\": \"TCP\", "
            + "\"type\": \"STANDARD\", \"attachExternalIP\": \"false\", \"contactPort\": 5060, \"clientAuth\": \"Default\", "
            + "\"sipServiceTypes\": [\"CMR\", \"CMR4_DIALIN\"]}";
    SipListenPoint slp = new ObjectMapper().readerFor(SampleListenPoint.class).readValue(json);
    Assert.assertEquals(slp.getName(), "jsonDefault");
    Assert.assertEquals(slp.getAlias(), "aliasDefault");
    Assert.assertEquals(slp.getIpAddress(), "1.1.1.1");
    Assert.assertEquals(slp.getPort(), 5061);
    Assert.assertEquals(slp.getTransport(), Transport.TCP);
    Assert.assertEquals(slp.getType(), SipListenPoint.Type.STANDARD);
    Assert.assertFalse(slp.shouldAttachExternalIP());
    Assert.assertEquals(slp.getContactPort(), 5060);
    Assert.assertEquals(slp.getClientAuth(), ClientAuthType.Default);
    Set<SipServiceType> expectedServiceTypes = new HashSet<>();
    expectedServiceTypes.add(SipServiceType.CMR);
    expectedServiceTypes.add(SipServiceType.CMR4_DIALIN);
    Assert.assertEquals(slp.getSipServiceTypes(), expectedServiceTypes);

    // add more service types (apart from those set during listenPoint creation) and verify the same
    Set<SipServiceType> extraServiceTypes = new HashSet<>();
    extraServiceTypes.add(SipServiceType.HURON);
    extraServiceTypes.add(SipServiceType.BROADCLOUD);
    slp.addServices(extraServiceTypes);
    expectedServiceTypes.addAll(extraServiceTypes);
    Assert.assertEquals(
        slp.getSipServiceTypes(), expectedServiceTypes); // Now, 4 service types should be there

    // check - if Listen point supports the mentioned service type
    Assert.assertTrue(slp.handlesService(SipServiceType.CMR4_DIALIN));
  }

  @Test(description = "sip listen point construction from input json with partial data")
  public void getListenPointsFromPartialJSON() throws JsonProcessingException {
    String json = "{ \"ip\": \"1.1.1.2\", \"port\": 5062 }";
    SipListenPoint slp = new ObjectMapper().readerFor(SampleListenPoint.class).readValue(json);
    Assert.assertNull(slp.getName());
    Assert.assertNull(slp.getAlias());
    Assert.assertEquals(slp.getIpAddress(), "1.1.1.2");
    Assert.assertEquals(slp.getPort(), 5062);
    Assert.assertNull(slp.getTransport());
    Assert.assertNull(slp.getType());
    Assert.assertFalse(slp.shouldAttachExternalIP());
    Assert.assertEquals(slp.getContactPort(), 5062);
    Assert.assertEquals(slp.getClientAuth(), ClientAuthType.Default);
    Assert.assertEquals(slp.getSipServiceTypes(), Collections.emptySet());

    // check - if Listen point(serviceType set is empty) supports the mentioned service type
    Assert.assertFalse(slp.handlesService(SipServiceType.CMR4_DIALIN));
  }

  @Test(
      description = "sip listen point construction from invalid input json",
      expectedExceptions = JsonProcessingException.class)
  public void getListenPointsFromInvalidJSON() throws JsonProcessingException {
    String json = "{ \"name\": \"invalidJson\", ip\": \"1.1.1.3\", \"port\": 5063 }";
    SipListenPoint slp = new ObjectMapper().readerFor(SampleListenPoint.class).readValue(json);
  }
}
