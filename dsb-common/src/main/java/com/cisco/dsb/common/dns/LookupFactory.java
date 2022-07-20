package com.cisco.dsb.common.dns;

import org.xbill.DNS.Lookup;
import org.xbill.DNS.lookup.LookupSession;

interface LookupFactory {
  Lookup createLookup(String searchString, int type);

  LookupSession createLookupAsync(String searchString);
}
