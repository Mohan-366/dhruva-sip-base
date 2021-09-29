package com.cisco.dhruva.application;

import com.cisco.dhruva.application.calltype.CallType;
import com.cisco.dhruva.application.constants.SipParamConstants;
import com.cisco.dhruva.application.exceptions.FilterTreeException;
import com.cisco.dhruva.application.filters.Filter;
import com.cisco.dsb.common.dns.DnsInjectionService;
import com.cisco.dsb.common.sip.dto.DNSInjectAction;
import com.cisco.dsb.common.sip.dto.InjectedDNSARecord;
import com.cisco.dsb.proxy.ProxyService;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyCookieImpl;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@CustomLog
public class DhruvaCallingApp {
  private ProxyService proxyService;
  private Filter filter;
  private DnsInjectionService dnsInjectionService;
  private ProxyAppConfig proxyAppConfig;

  @Autowired
  DhruvaCallingApp(
      ProxyService proxyService, Filter filter, DnsInjectionService dnsInjectionService) {
    this.proxyService = proxyService;
    this.filter = filter;
    this.dnsInjectionService = dnsInjectionService;
    init();
  }

  public void init() {
    proxyAppConfig =
        ProxyAppConfig.builder()
            ._1xx(false)
            ._2xx(true)
            ._3xx(true)
            ._4xx(true)
            ._5xx(true)
            ._6xx(true)
            .midDialog(false)
            .requestConsumer(getRequestConsumer())
            .responseConsumer(getResponseConsumer())
            .build();

    ImmutableList<CallType.CallTypes> interestedCallTypes =
        ImmutableList.of(
            CallType.CallTypes.DIAL_IN_PSTN,
            CallType.CallTypes.DIAL_IN_B2B,
            CallType.CallTypes.DIAL_OUT_WXC,
            CallType.CallTypes.DIAL_OUT_B2B);
    try {
      filter.register(interestedCallTypes);
    } catch (FilterTreeException e) {
      logger.error("Unable to add calltype to filter tree, exiting!!!", e);
      System.exit(-1);
    }
    // init dns injection service
    List<InjectedDNSARecord> injectedDNSARecords = new ArrayList<>();
    InjectedDNSARecord b2b =
        new InjectedDNSARecord(
            SIPConfig.B2B_A_RECORD[0], 3600, "127.0.0.1", DNSInjectAction.REPLACE);
    InjectedDNSARecord ns =
        new InjectedDNSARecord(
            SIPConfig.NS_A_RECORD[0], 3600, "127.0.0.1", DNSInjectAction.REPLACE);
    InjectedDNSARecord as1 =
        new InjectedDNSARecord("test1.as.com", 3600, "127.0.0.1", DNSInjectAction.REPLACE);
    InjectedDNSARecord as2 =
        new InjectedDNSARecord("test2.as.com", 3600, "127.0.0.1", DNSInjectAction.REPLACE);
    injectedDNSARecords.add(b2b);
    injectedDNSARecords.add(ns);
    injectedDNSARecords.add(as1);
    injectedDNSARecords.add(as2);
    dnsInjectionService.injectA(SipParamConstants.INJECTED_DNS_UUID, injectedDNSARecords);
    proxyService.register(proxyAppConfig);
  }

  private Consumer<ProxySIPRequest> getRequestConsumer() {
    return proxySIPRequest -> {
      Optional<CallType> callType = this.filter.filter(proxySIPRequest);
      if (callType.isPresent()) {
        CallType ct = callType.get();
        ct.processRequest().accept(Mono.just(proxySIPRequest));
      }
    };
  }

  private Consumer<ProxySIPResponse> getResponseConsumer() {
    return proxySIPResponse -> {
      ProxyCookieImpl cookie = (ProxyCookieImpl) proxySIPResponse.getCookie();
      CallType callType;
      if (cookie == null || (callType = (CallType) cookie.getCalltype()) == null) {
        logger.error("Calltype not present in cookie, proxying the response back to proxylayer");
        proxySIPResponse.proxy();
        return;
      }
      callType.processResponse().accept(Mono.just(proxySIPResponse));
    };
  }
}
