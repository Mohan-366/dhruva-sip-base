package com.cisco.dsb.common.sip.util;

import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/** This interface can be used when sip apps wants to add additional custom headers in a sip msg */
public interface SipExtraHeaderGenerator {
  List<Pair<String, String>> getExtraHeaders();
}
