package com.cisco.dhruva.common.dns;

import org.xbill.DNS.Lookup;

interface LookupFactory {
  Lookup createLookup(String searchString, int type);
}
