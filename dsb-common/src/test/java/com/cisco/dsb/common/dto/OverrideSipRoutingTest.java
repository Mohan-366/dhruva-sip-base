package com.cisco.dsb.common.dto;

import com.cisco.dsb.common.sip.dto.InjectedDNSARecord;
import com.cisco.dsb.common.sip.dto.InjectedDNSSRVRecord;
import java.util.ArrayList;
import org.testng.Assert;
import org.testng.annotations.Test;

public class OverrideSipRoutingTest {
  @Test
  void testConstructor() {
    OverrideSipRouting actualOverrideSipRouting = new OverrideSipRouting();
    ArrayList<InjectedDNSSRVRecord> injectedDNSSRVRecordList = new ArrayList<>();
    actualOverrideSipRouting.setDnsSRVRecords(injectedDNSSRVRecordList);
    Assert.assertSame(injectedDNSSRVRecordList, actualOverrideSipRouting.getDnsSRVRecords());
  }

  @Test
  void testConstructor2() {
    ArrayList<InjectedDNSARecord> injectedDNSARecordList = new ArrayList<>();
    ArrayList<InjectedDNSSRVRecord> injectedDNSSRVRecordList = new ArrayList<>();
    OverrideSipRouting actualOverrideSipRouting =
        new OverrideSipRouting(injectedDNSARecordList, injectedDNSSRVRecordList);
    Assert.assertSame(injectedDNSARecordList, actualOverrideSipRouting.getDnsARecords());
    Assert.assertSame(injectedDNSSRVRecordList, actualOverrideSipRouting.getDnsSRVRecords());
  }
}
