package com.cisco.dsb.common.dns;

import org.xbill.DNS.Lookup;

interface LookupFactory {
  Lookup createLookup(String searchString, int type);
}
