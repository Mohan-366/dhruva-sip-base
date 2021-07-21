package com.cisco.dhruva.sip.proxy;

import com.cisco.dsb.util.QuadFunction;
import com.cisco.dsb.util.TriFunction;
import gov.nist.javax.sip.message.SIPRequest;
import javax.sip.ServerTransaction;

public interface ProxyFactoryInterface {
  QuadFunction<
          ControllerInterface,
          ProxyParamsInterface,
          ServerTransaction,
          SIPRequest,
          ProxyStatelessTransaction>
      proxyTransaction();

  TriFunction<ProxyTransaction, ServerTransaction, SIPRequest, ProxyServerTransaction>
      proxyServerTransaction();
}
