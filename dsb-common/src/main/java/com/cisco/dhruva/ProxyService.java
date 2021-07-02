/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 * @author graivitt
 */

package com.cisco.dhruva;

import com.cisco.dhruva.bootstrap.DhruvaServer;
import com.cisco.dhruva.sip.controller.ControllerConfig;
import com.cisco.dhruva.sip.proxy.ProxyPacketProcessor;
import com.cisco.dhruva.sip.proxy.sinks.DhruvaSink;
import com.cisco.dsb.common.CommonContext;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.messaging.MessageConvertor;
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
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.sip.*;
import javax.sip.message.Request;
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

  private static Consumer<ProxySIPRequest> requestConsumer;
  private static Consumer<ProxySIPResponse> responseConsumer;

  ConcurrentHashMap<String, SipStack> proxyStackMap = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() throws Exception {
    List<SIPListenPoint> sipListenPoints = dhruvaSIPConfigProperties.getListeningPoints();
    ArrayList<CompletableFuture> listenPointFutures = new ArrayList<CompletableFuture>();
    // proxyPacketProcessor = new ProxyPacketProcessor();
    // TODO use streams instead of for loop
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
    handleMessageFromApp();
  }

  public Optional<SipStack> getSipStack(String sipListenPointName) {
    return Optional.ofNullable(proxyStackMap.get(sipListenPointName));
  }

  private void handleMessageFromApp() {
    // TODO DSB Define right Message type for RouteResult
    DhruvaSink.routeResultSink
        .asFlux()
        .subscribe(
            dsipMessage -> {
              logger.info("Received msg from App: callId{}", dsipMessage.getCallId());
              try {
                if (dsipMessage.isRequest()) {
                  SIPRequest request =
                      MessageConvertor.convertDhruvaRequestMessageToJainSipMessage(
                          (ProxySIPRequest) dsipMessage);
                  if (!((SIPRequest) dsipMessage.getSIPMessage()).getMethod().equals(Request.ACK)) {
                    ClientTransaction clientTransaction =
                        ((ProxySIPRequest) dsipMessage)
                            .getProvider()
                            .getNewClientTransaction((Request) request.clone());
                    clientTransaction.setApplicationData(
                        dsipMessage.getContext().get(CommonContext.PROXY_CONTROLLER));
                    clientTransaction.sendRequest();
                  } else {
                    ((ProxySIPRequest) dsipMessage).getProvider().sendRequest(request);
                  }

                } else {
                  SIPResponse response =
                      MessageConvertor.convertDhruvaResponseMessageToJainSipMessage(
                          (ProxySIPResponse) dsipMessage);
                  ((ProxySIPResponse) dsipMessage).getProvider().sendResponse(response);
                }
              } catch (SipException exception) {
                exception.printStackTrace();
              }

              // dsipMessage.getProvider()
            });
  }

  @PreDestroy
  private void releaseServiceResources() {}

  /*
  Application Layer should call this function along with requestConsumer to process the request messages from
  proxylayer. Message format is DSIPRequestMessage
   */
  public static void registerForRequest(Consumer<ProxySIPRequest> appRequestConsumer) {
    requestConsumer = appRequestConsumer;

    // DhruvaSink.requestSink.asFlux().subscribe(requestConsumer);
  }
  /*
  Application Layer should call this function along with requestConsumer to process the request messages from
  proxylayer. Message format is DSIPResponseMessage
   */
  public static void registerForResponse(Consumer<ProxySIPResponse> appResponseConsumer) {
    responseConsumer = appResponseConsumer;
    // DhruvaSink.responseSink.asFlux().subscribe(responseConsumer);
  }

  private static Function<RequestEvent, Mono<RequestEvent>> filterProxyRequest =
      (requestEvent) -> {
        System.out.println("filtering request");

        return Mono.just(requestEvent);
      };

  private static Function<ResponseEvent, Mono<ResponseEvent>> filterProxyResponse =
      (responseEvent) -> {
        System.out.println("filtering response");

        return Mono.just(responseEvent);
      };

  private static Function<RequestEvent, ProxySIPRequest> createProxySipRequest =
      (fluxRequestEvent) -> {
        try {
          return MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(
              fluxRequestEvent.getRequest(),
              (SipProvider) fluxRequestEvent.getSource(),
              fluxRequestEvent.getServerTransaction(),
              new ExecutionContext());
        } catch (IOException e) {
          e.printStackTrace();
        }
        return null;
      };

  private static Function<ResponseEvent, ProxySIPResponse> createProxySipResponse =
      (fluxResponseEvent) -> {
        try {
          return MessageConvertor.convertJainSipResponseMessageToDhruvaMessage(
              fluxResponseEvent.getResponse(),
              (SipProvider) fluxResponseEvent.getSource(),
              fluxResponseEvent.getClientTransaction(),
              new ExecutionContext());
        } catch (IOException e) {
          e.printStackTrace();
        }
        return null;
      };

  public static Consumer<Mono<RequestEvent>> proxyRequestHandler =
      requestEventMono -> {
        requestEventMono
            .<RequestEvent>handle(
                (requestEvent, synchronousSink) -> {
                  // processing
                  try {
                    synchronousSink.next(requestEvent);
                  } catch (Exception e) {
                    synchronousSink.error(e.getCause());
                  }
                })
            .flatMap(filterProxyRequest)
            .mapNotNull(createProxySipRequest)
            .subscribe(requestConsumer);
      };

  public static Consumer<Mono<ResponseEvent>> proxyResponseHandler =
      responseMono -> {
        responseMono
            .<ResponseEvent>handle(
                (responseEvent, synchronousSink) -> {
                  // processing
                  try {
                    synchronousSink.next(responseEvent);
                  } catch (Exception e) {
                    synchronousSink.error(e.getCause());
                  }
                })
            .flatMap(filterProxyResponse)
            .mapNotNull(createProxySipResponse)
            .subscribe(responseConsumer);
      };
}
