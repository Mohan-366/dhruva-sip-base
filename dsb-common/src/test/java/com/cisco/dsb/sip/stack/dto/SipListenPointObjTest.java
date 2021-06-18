package com.cisco.dsb.sip.stack.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SipListenPointObjTest {

  @Test(description = "sip listen point (name,ip,port) created using constructors")
  public void testSipListenPoints() {
    SipListenPointObj slp = new SipListenPointObj("default", "1.1.1.1", 5061);
    Assert.assertEquals(slp.getName(), "default");
    Assert.assertEquals(slp.getIpAddress(), "1.1.1.1");
    Assert.assertEquals(slp.getPort(), 5061);
  }

  @Test(description = "sip listen point constructed from input json")
  public void getListenPointsFromJSON() throws JsonProcessingException {
    String json = "{ \"name\": \"jsonDefault\", \"ip\": \"1.1.1.1\", \"port\": 5061 }";
    SipListenPointObj slp = new ObjectMapper().readerFor(SipListenPointObj.class).readValue(json);
    Assert.assertEquals(slp.getName(), "jsonDefault");
    Assert.assertEquals(slp.getIpAddress(), "1.1.1.1");
    Assert.assertEquals(slp.getPort(), 5061);
  }

  @Test(description = "sip listen point constructed from input json with partial data")
  public void getListenPointsFromPartialJSON() throws JsonProcessingException {
    String json = "{ \"ip\": \"1.1.1.2\", \"port\": 5062 }";
    SipListenPointObj slp = new ObjectMapper().readerFor(SipListenPointObj.class).readValue(json);
    Assert.assertEquals(slp.getName(), null);
    Assert.assertEquals(slp.getIpAddress(), "1.1.1.2");
    Assert.assertEquals(slp.getPort(), 5062);
  }

  @Test(
      description = "sip listen point constructed from invalid input json",
      expectedExceptions = JsonProcessingException.class)
  public void getListenPointsFromInvalidJSON() throws JsonProcessingException {
    String json = "{ \"name\": \"invalidJson\", ip\": \"1.1.1.3\", \"port\": 5063 }";
    SipListenPointObj slp = new ObjectMapper().readerFor(SipListenPointObj.class).readValue(json);
  }
}
