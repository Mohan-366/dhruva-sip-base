package com.cisco.dhruva.sip.proxy;

import com.cisco.dhruva.sip.proxy.errors.InternalProxyErrorException;
import gov.nist.javax.sip.message.SIPRequest;

import javax.sip.ServerTransaction;

@FunctionalInterface
public interface ProxyFactoryInterface {
    ProxyStatelessTransaction createProxyTransaction(
            ControllerInterface controller,
            ProxyParamsInterface config,
            ServerTransaction server,
            SIPRequest request)
            throws InternalProxyErrorException;
}
