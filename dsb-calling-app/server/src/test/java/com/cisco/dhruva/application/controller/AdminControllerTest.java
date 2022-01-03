package com.cisco.dhruva.application.controller;

import com.cisco.dsb.common.dns.DnsInjectionService;
import com.cisco.dsb.common.dto.OverrideSipRouting;
import com.cisco.dsb.common.sip.dto.InjectedDNSARecord;
import com.cisco.dsb.common.sip.dto.InjectedDNSSRVRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class AdminControllerTest {

  @DataProvider
  public Object[][] injectionData() {
    return new Object[][] {{true, true}, {true, false}, {false, true}, {false, false}};
  }

  @Test(dataProvider = "injectionData")
  public void testSetOverrideSipRouting(boolean arecord, boolean srv) {
    DnsInjectionService dnsInjectionService = Mockito.mock(DnsInjectionService.class);
    OverrideSipRouting overrideSipRouting;
    List<InjectedDNSARecord> arecords = new ArrayList<>();
    List<InjectedDNSSRVRecord> srvRecords = new ArrayList<>();
    String userId = "test";
    if (arecord) {
      if (srv) {
        overrideSipRouting = new OverrideSipRouting(arecords, srvRecords);
      } else {
        overrideSipRouting = new OverrideSipRouting(arecords, null);
      }
    } else {
      if (srv) {
        overrideSipRouting = new OverrideSipRouting(null, srvRecords);
      } else {
        overrideSipRouting = new OverrideSipRouting(null, null);
      }
    }

    AdminController adminController = new AdminController();
    adminController.setDnsInjectionService(dnsInjectionService);

    adminController.setOverrideSipRouting(overrideSipRouting, userId);

    Mockito.verify(dnsInjectionService, Mockito.times(1)).clear(userId);
    if (arecord) {
      Mockito.verify(dnsInjectionService, Mockito.times(1)).injectA(userId, arecords);
    } else {
      Mockito.verify(dnsInjectionService, Mockito.times(0)).injectA(userId, arecords);
    }
    if (srv) {
      Mockito.verify(dnsInjectionService, Mockito.times(1)).injectSRV(userId, srvRecords);
    } else {
      Mockito.verify(dnsInjectionService, Mockito.times(0)).injectSRV(userId, srvRecords);
    }
  }

  @Test
  public void testDelete() {
    DnsInjectionService dnsInjectionService = Mockito.mock(DnsInjectionService.class);
    AdminController adminController = new AdminController();
    adminController.setDnsInjectionService(dnsInjectionService);

    adminController.deleteOverrideSipRouting("test");

    Mockito.verify(dnsInjectionService, Mockito.times(1)).clear(ArgumentMatchers.eq("test"));
  }

  @Test
  public void testGet() throws Exception {
    DnsInjectionService dnsInjectionService = Mockito.mock(DnsInjectionService.class);
    AdminController adminController = new AdminController();
    adminController.setDnsInjectionService(dnsInjectionService);

    Callable<OverrideSipRouting> testResponse = adminController.getOverrideSipRouting("test");
    testResponse.call();
    Mockito.verify(dnsInjectionService, Mockito.times(1)).getInjectedA(ArgumentMatchers.eq("test"));
    Mockito.verify(dnsInjectionService, Mockito.times(1))
        .getInjectedSRV(ArgumentMatchers.eq("test"));
  }
}
