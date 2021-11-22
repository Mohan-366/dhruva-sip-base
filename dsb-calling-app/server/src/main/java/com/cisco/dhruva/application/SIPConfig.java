package com.cisco.dhruva.application;

import java.util.HashMap;

public class SIPConfig {
  public static final String NETWORK_PSTN = "net_sp";
  public static final String NETWORK_B2B = "net_internal_b2b";
  public static final String NETWORK_CALLING_CORE = "net_internal_calling_core";
  public static final String[] B2B_A_RECORD = {"test.beech.com", "5060"};
  public static final String[] NS_A_RECORD = {"test1.ns.cisco.com", "6060"};
  public static final HashMap<String, String> dtg;

  static {
    dtg = new HashMap<>();
    dtg.put("CcpFusionUS", "peer1.pstn1.com");
    dtg.put("CcpFusionIN", "peer1.pstn2.com");
  }
  // Config related to NS and PSTN pool table
}
