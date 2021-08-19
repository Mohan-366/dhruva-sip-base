/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dsb.common.service;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.proxy.bootstrap.DhruvaServer;
import com.cisco.dsb.proxy.controller.ControllerConfig;
import com.cisco.dsb.proxy.controller.ProxyControllerFactory;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyPacketProcessor;
import com.cisco.dsb.proxy.sip.SipProxyManager;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.sip.*;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@CustomLog
public class ProxyService {

  @Autowired DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

  @Autowired public MetricService metricsService;

  @Autowired SipServerLocatorService resolver;

  @Autowired DhruvaServer server;

  @Autowired ControllerConfig controllerConfig;

  @Autowired private ProxyPacketProcessor proxyPacketProcessor;

  @Autowired ProxyControllerFactory proxyControllerFactory;

  @Autowired SipProxyManager sipProxyManager;

  @Autowired DhruvaExecutorService dhruvaExecutorService;

  ConcurrentHashMap<String, SipStack> proxyStackMap = new ConcurrentHashMap<>();
  // Map of network and provider
  private Map<SipProvider, String> sipProvidertoNetworkMap = new ConcurrentHashMap<>();

  private List<String> allowedHeaders = new ArrayList<>();
  // Default ProxyConfig
  private ProxyAppConfig proxyAppConfig = ProxyAppConfig.builder().build();

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

              // We can always derive sip stack from a given provider
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
  }

  public Optional<SipStack> getSipStack(String sipListenPointName) {
    return Optional.ofNullable(proxyStackMap.get(sipListenPointName));
  }

  /*
   This is with the assumption that per network has single stack and provider associated with it.
  */
  public Optional<SipProvider> getSipProvider(SipStack sipStack, SIPListenPoint sipListenPoint) {
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
  public void releaseServiceResources() {
    for (SipStack sipStack : proxyStackMap.values()) {
      sipStack.stop();
    }
    DhruvaNetwork.clearSipProviderMap();
  }

  /**
   * Applications should use register to get callback for ProxySIPRequest and ProxySIPResponse
   *
   * @param appConfig application should provide the behaviours and interests
   */
  public void register(ProxyAppConfig appConfig) {
    this.proxyAppConfig = appConfig;
  }

  /** placeholder for processing the RequestEvent from Stack */
  public Consumer<Mono<RequestEvent>> proxyRequestHandler() {
    return requestEventMono ->
        requestPipeline(requestEventMono).subscribe(this.proxyAppConfig.getRequestConsumer());
  }

  public Mono<ProxySIPRequest> requestPipeline(Mono<RequestEvent> requestEventMono) {
    return requestEventMono
        .name("proxyRequest")
        .doOnNext(sipProxyManager.getManageLogAndMetricsForRequest())
        .mapNotNull(sipProxyManager.createServerTransactionAndProxySIPRequest())
        .mapNotNull(sipProxyManager.getProxyController(this.proxyAppConfig))
        .mapNotNull(sipProxyManager.validateRequest())
        .mapNotNull(sipProxyManager.proxyAppController(this.proxyAppConfig.isMidDialog()));
  }

  // flux.parallel().runOn(Schedulers.fromExecutorService(StripEx)).ops
  /** placeholder for processing the ResponseEvent from Stack */
  public Consumer<Mono<ResponseEvent>> proxyResponseHandler() {
    return responsEventMono ->
        responsePipeline(responsEventMono)
            .subscribe(
                proxySIPResponse ->
                    logger.error("ProxyResponseHandler(): This code should never hit!!!"));
  }

  public Mono<ProxySIPResponse> responsePipeline(Mono<ResponseEvent> responseEventMono) {
    return responseEventMono
        .name("proxyResponse")
        .doOnNext(sipProxyManager.getManageLogAndMetricsForResponse())
        .mapNotNull(sipProxyManager.findProxyTransaction())
        .mapNotNull(sipProxyManager.processProxyTransaction());
    //        .mapNotNull(
    //            proxySIPResponse ->
    //                sipProxyManager.processToApp(
    //                    proxySIPResponse,
    //                    this.appConfig.getInterest(proxySIPResponse.getResponseClass())));

    // .subscribe()
  }

  /** placeholder for processing the timeoutEvent from Stack */
  public Consumer<Mono<TimeoutEvent>> proxyTimeOutHandler() {
    return timeoutEventMono ->
        timeOutPipeline(timeoutEventMono)
            .subscribe(
                proxySIPResponse ->
                    logger.error("proxyTimeOutHandler(): This code should never hit!!!"));
  }

  public Mono<ProxySIPResponse> timeOutPipeline(Mono<TimeoutEvent> timeoutEventMono) {
    return timeoutEventMono.mapNotNull(sipProxyManager.handleProxyTimeoutEvent());
  }
}
