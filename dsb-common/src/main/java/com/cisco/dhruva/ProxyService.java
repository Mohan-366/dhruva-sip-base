/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 * @author graivitt
 */

package com.cisco.dhruva;

import com.cisco.dhruva.bootstrap.DhruvaServer;
import com.cisco.dhruva.sip.controller.ControllerConfig;
import com.cisco.dhruva.sip.controller.ProxyControllerFactory;
import com.cisco.dhruva.sip.proxy.ProxyPacketProcessor;
import com.cisco.dhruva.sip.proxy.SipProxyManager;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.eventsink.RequestEventSink;
import com.cisco.dsb.eventsink.ResponseEventSink;
import com.cisco.dsb.service.MetricService;
import com.cisco.dsb.service.SipServerLocatorService;
import com.cisco.dsb.sip.bean.SIPListenPoint;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipStack;
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

  @Autowired RequestEventSink requestEventSink;

  @Autowired ResponseEventSink responseEventSink;

  @Autowired ProxyControllerFactory proxyControllerFactory;

  @Autowired SipProxyManager sipProxyManager;
  private static Consumer<ProxySIPRequest> requestConsumer;
  private static Consumer<ProxySIPResponse> responseConsumer;

  ConcurrentHashMap<String, SipStack> proxyStackMap = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() throws Exception {
    List<SIPListenPoint> sipListenPoints = dhruvaSIPConfigProperties.getListeningPoints();
    ArrayList<CompletableFuture> listenPointFutures = new ArrayList<CompletableFuture>();
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
    this.requestConsumer = requestConsumer;
    this.responseConsumer = responseConsumer;
  }

  /** placeholder for processing the RequestEvent from Stack */
  public Consumer<Mono<RequestEvent>> proxyRequestHandler =
      requestEventMono ->
          requestEventMono
              .mapNotNull(sipProxyManager.createProxySipRequest)
              .mapNotNull(sipProxyManager.createProxyController)
              .mapNotNull(sipProxyManager.validateRequest)
              // .onErrorResume()
              // .subscribeOn(Schedulers.fromExecutorService(StripedExecutorService))
              .subscribe(requestConsumer);
  // flux.parallel().runOn(Schedulers.fromExecutorService(StripEx)).ops
  /** placeholder for processing the ResponseEvent from Stack */
  public Consumer<Mono<ResponseEvent>> proxyResponseHandler =
      responsEventMono ->
          responsEventMono
              .mapNotNull(sipProxyManager.createProxySipResponse)
              .mapNotNull(sipProxyManager.toProxyController)
              .subscribe(responseConsumer);
}
