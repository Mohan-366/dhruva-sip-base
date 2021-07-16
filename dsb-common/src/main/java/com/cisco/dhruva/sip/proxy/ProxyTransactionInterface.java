package com.cisco.dhruva.sip.proxy;

import com.cisco.dsb.common.messaging.ProxySIPRequest;
import java.text.ParseException;
import javax.sip.SipException;

public interface ProxyTransactionInterface {

  void proxyTo(ProxySIPRequest request, ProxyCookie cookie, ProxyBranchParamsInterface params);

  void addProxyRecordRoute(ProxySIPRequest request, ProxyBranchParamsInterface params)
      throws SipException, ParseException;
}
