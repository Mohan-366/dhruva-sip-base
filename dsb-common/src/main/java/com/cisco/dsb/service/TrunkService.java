package com.cisco.dsb.service;

import com.cisco.dsb.common.messaging.models.AbstractSipRequest;
import com.cisco.dsb.loadbalancer.*;
import com.cisco.dsb.servergroups.DnsServerGroupUtil;
import com.cisco.dsb.servergroups.SG;
import com.cisco.dsb.sip.proxy.SipUtils;
import com.cisco.dsb.sip.util.EndPoint;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import gov.nist.javax.sip.message.SIPRequest;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TrunkService {

  SipServerLocatorService resolver;

  public TrunkService(SipServerLocatorService resolver) {
    this.resolver = resolver;
  }

  private static final Logger logger = DhruvaLoggerFactory.getLogger(TrunkService.class);


  public Mono<EndPoint> getElementMono(AbstractSipRequest request) {

    String reqUri = ((SIPRequest) request.getSIPMessage()).getRequestURI().toString();
    String uri = SipUtils.getHostPortion(reqUri);
    logger.info("Dynamic Server Group to be created for  {} ", uri);

    try {

      DnsServerGroupUtil dnsServerGroupUtil = new DnsServerGroupUtil(resolver);
      Mono<ServerGroupInterface> serverGroupInterfaceOptional =
              dnsServerGroupUtil.createDNSServerGroup(
                      uri, request.getNetwork(), Transport.TLS, SG.index_sgSgLbType_call_id);


      return serverGroupInterfaceOptional.
              mapNotNull(sg -> ( TrunkService.getLoadBalancer(uri, sg, request))).
              mapNotNull(x -> x.getServer().getEndPoint());
    }
     catch (Exception e) {
      e.printStackTrace();
    }
    return Mono.empty();
  }


  public static LBInterface getLoadBalancer(String uri, ServerGroupInterface sgi, AbstractSipRequest request) {

    try {
       return LBFactory.createLoadBalancer(uri, sgi, request);

    }catch(LBException ex) {
      logger.error("exception while creating loadbalancer " + ex.getMessage());
    }
      return null;
  }

}
