package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.proxy.ControllerInterface;
import com.cisco.dsb.proxy.util.QuadFunction;
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
