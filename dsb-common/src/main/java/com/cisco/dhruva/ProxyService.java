/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 * @author graivitt
 */

package com.cisco.dhruva;

import com.cisco.dhruva.bootstrap.DhruvaServer;
import com.cisco.dhruva.sip.controller.ControllerConfig;
import com.cisco.dhruva.sip.proxy.ProxyPacketProcessor;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.service.MetricService;
import com.cisco.dsb.service.SipServerLocatorService;
import com.cisco.dsb.sip.bean.SIPListenPoint;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProxyService {

  Logger logger = DhruvaLoggerFactory.getLogger(ProxyService.class);

  @Autowired DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

  @Autowired public MetricService metricsService;

  @Autowired SipServerLocatorService resolver;

  @Autowired DhruvaServer server;

  @Autowired ControllerConfig controllerConfig;

  private ProxyPacketProcessor proxyPacketProcessor;

  @PostConstruct
  public void init() throws Exception {
    List<SIPListenPoint> sipListenPoints = dhruvaSIPConfigProperties.getListeningPoints();
    ArrayList<CompletableFuture> listenPointFutures = new ArrayList<CompletableFuture>();
    proxyPacketProcessor = new ProxyPacketProcessor();
    for (SIPListenPoint sipListenPoint : sipListenPoints) {

      logger.info("Trying to start proxy server on {} ", sipListenPoint);
      DhruvaNetwork networkConfig =
          DhruvaNetwork.createNetwork(sipListenPoint.getName(), sipListenPoint);

      CompletableFuture listenPointFuture =
          server.startListening(
              sipListenPoint.getTransport(),
              networkConfig,
              InetAddress.getByName(sipListenPoint.getHostIPAddress()),
              sipListenPoint.getPort(),
              proxyPacketProcessor);

      listenPointFuture.whenComplete(
          (channel, throwable) -> {
            if (throwable == null) {
              try {
                logger.info("Server socket created for {}", channel);
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
                        + channel,
                    e);
              }
            } else {
              // TODO: should Dhruva exit ? or generate an Alarm
              logger.error(
                  "Server socket creation failed for {} , error is {} ", channel, throwable);
            }
          });

      listenPointFutures.add(listenPointFuture);
    }

    listenPointFutures.forEach(CompletableFuture::join);
  }

  @PreDestroy
  private void releaseServiceResources() {}
}
