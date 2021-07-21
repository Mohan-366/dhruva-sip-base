/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dhruva;

import com.cisco.dhruva.bootstrap.DhruvaServer;
import com.cisco.dhruva.sip.controller.ControllerConfig;
import com.cisco.dhruva.sip.controller.ProxyControllerFactory;
import com.cisco.dhruva.sip.proxy.ProxyPacketProcessor;
import com.cisco.dhruva.sip.proxy.SipProxyManager;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.service.MetricService;
import com.cisco.dsb.service.SipServerLocatorService;
import com.cisco.dsb.sip.bean.SIPListenPoint;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.util.SpringApplicationContext;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.sip.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProxyService {

  Logger logger = DhruvaLoggerFactory.getLogger(ProxyService.class);

  @Autowired DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

  @Autowired public MetricService metricsService;

  @Autowired SipServerLocatorService resolver;

  @Autowired DhruvaServer server;

  @Autowired ControllerConfig controllerConfig;

  @Autowired private ProxyPacketProcessor proxyPacketProcessor;

  @Autowired ProxyControllerFactory proxyControllerFactory;

  @Autowired SipProxyManager sipProxyManager;

  @Autowired DhruvaExecutorService dhruvaExecutorService;

  private static Consumer<ProxySIPRequest> requestConsumer;
  private static Consumer<ProxySIPResponse> responseConsumer;

  ConcurrentHashMap<String, SipStack> proxyStackMap = new ConcurrentHashMap<>();
  // Map of network and provider
  private Map<SipProvider, String> sipProvidertoNetworkMap = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() throws Exception {
    List<SIPListenPoint> sipListenPoints = dhruvaSIPConfigProperties.getListeningPoints();
    ArrayList<CompletableFuture> listenPointFutures = new ArrayList<CompletableFuture>();
    DhruvaNetwork.setDhruvaConfigProperties(dhruvaSIPConfigProperties);
    for (SIPListenPoint sipListenPoint : sipListenPoints) {
      logger.info("Trying to start proxy server on {} ", sipListenPoint);
      DhruvaNetwork networkConfig =
          DhruvaNetwork.createNetwork(sipListenPoint.getName(), sipListenPoint);
      // TODO change to functional style
      CompletableFuture<SipStack> listenPointFuture =
          server.startListening(
              sipListenPoint.getTransport(),
              networkConfig,
              InetAddress.getByName(sipListenPoint.getHostIPAddress()),
              sipListenPoint.getPort(),
              proxyPacketProcessor);

      listenPointFuture.whenComplete(
          (sipStack, throwable) -> {
            if (throwable == null) {
              proxyStackMap.putIfAbsent(sipListenPoint.getName(), sipStack);

              SipProvider sipProvider = null;
              Optional<SipProvider> optionalSipProvider = getSipProvider(sipStack, sipListenPoint);
              if (optionalSipProvider.isPresent()) {
                sipProvider = optionalSipProvider.get();
              } else {
                logger.error("sip provider is not set !!!");
                throw new RuntimeException(
                    "Unable to initialize stack properly, provider is not initialized!!");
              }
              sipProvidertoNetworkMap.put(sipProvider, networkConfig.getName());
              DhruvaNetwork.setSipProvider(networkConfig.getName(), sipProvider);
              try {
                logger.info("Server socket created for {}", sipStack);
                controllerConfig.addListenInterface(
                    networkConfig,
                    InetAddress.getByName(sipListenPoint.getHostIPAddress()),
                    sipListenPoint.getPort(),
                    sipListenPoint.getTransport(),
                    InetAddress.getByName(sipListenPoint.getHostIPAddress()),
                    sipListenPoint.shouldAttachExternalIP());

                if (sipListenPoint.isRecordRoute()) {
                  controllerConfig.addRecordRouteInterface(
                      InetAddress.getByName(sipListenPoint.getHostIPAddress()),
                      sipListenPoint.getPort(),
                      sipListenPoint.getTransport(),
                      networkConfig);
                }
              } catch (Exception e) {
                logger.error(
                    "Configuring Listenpoint in DsControllerConfig failed for ListenPoint  "
                        + sipStack,
                    e);
              }
            } else {
              // TODO: should Dhruva exit ? or generate an Alarm
              logger.error(
                  "Server socket creation failed for {} , error is {} ", sipStack, throwable);
            }
          });

      listenPointFutures.add(listenPointFuture);
    }

    listenPointFutures.forEach(CompletableFuture::join);

    dhruvaExecutorService =
        SpringApplicationContext.getAppContext().getBean(DhruvaExecutorService.class);
    dhruvaExecutorService.startScheduledExecutorService(ExecutorType.PROXY_CLIENT_TIMEOUT, 2);
  }

  public Optional<SipStack> getSipStack(String sipListenPointName) {
    return Optional.ofNullable(proxyStackMap.get(sipListenPointName));
  }

  /*
   This is with the assumption that per network has single stack and provider associated with it.
  */
  private Optional<SipProvider> getSipProvider(SipStack sipStack, SIPListenPoint sipListenPoint) {
    Iterator sipProviders = sipStack.getSipProviders();
    while (sipProviders.hasNext()) {
      SipProvider sipProvider = (SipProvider) sipProviders.next();
      ListeningPoint[] lps = sipProvider.getListeningPoints();
      for (ListeningPoint lp : lps) {
        if (sipListenPoint.getHostIPAddress().equals(lp.getIPAddress())
            && sipListenPoint.getPort() == lp.getPort()) {
          return Optional.of(sipProvider);
        }
      }
    }
    return Optional.empty();
  }

  @PreDestroy
  private void releaseServiceResources() {}

  /**
   * Applications should use register to get callback for ProxySIPRequest and ProxySIPResponse
   *
   * @param requestConsumer application should provide the behaviour to process the ProxySIPRequest
   * @param responseConsumer application should provide the behaviour to process the
   *     ProxySIPResponse
   */
  public void register(
      Consumer<ProxySIPRequest> requestConsumer, Consumer<ProxySIPResponse> responseConsumer) {
    ProxyService.requestConsumer = requestConsumer;
    ProxyService.responseConsumer = responseConsumer;
  }

  /** placeholder for processing the RequestEvent from Stack */
  public Consumer<Mono<RequestEvent>> proxyRequestHandler() {
    return requestEventMono -> requestPipeline(requestEventMono).subscribe(requestConsumer);
  }

  public Mono<ProxySIPRequest> requestPipeline(Mono<RequestEvent> requestEventMono) {
    return requestEventMono
        .mapNotNull(sipProxyManager.validate)
        .mapNotNull(sipProxyManager.createProxySipRequest())
        .mapNotNull(sipProxyManager.createProxyController());
  }
  // flux.parallel().runOn(Schedulers.fromExecutorService(StripEx)).ops
  /** placeholder for processing the ResponseEvent from Stack */
  public Consumer<Mono<ResponseEvent>> proxyResponseHandler() {
    return responsEventMono -> responsePipeline(responsEventMono).subscribe(responseConsumer);
  }

  public Mono<ProxySIPResponse> responsePipeline(Mono<ResponseEvent> responseEventMono) {
    return responseEventMono
        .mapNotNull(sipProxyManager.findProxyTransaction())
        .mapNotNull(sipProxyManager.processProxyTransaction());
  }
}
