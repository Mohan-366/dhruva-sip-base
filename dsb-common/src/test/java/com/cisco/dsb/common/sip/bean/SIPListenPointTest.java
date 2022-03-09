package com.cisco.dsb.common.sip.bean;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.testng.annotations.Test;

public class SIPListenPointTest {

  @Test
  public void testEquals() {
    EqualsVerifier.simple().forClass(SIPListenPoint.class).withOnlyTheseFields("name").verify();
  }
}
