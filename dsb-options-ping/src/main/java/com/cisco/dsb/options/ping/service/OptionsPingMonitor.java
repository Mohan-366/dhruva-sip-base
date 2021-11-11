package com.cisco.dsb.options.ping.service;

import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.stack.dto.ServerGroupElement;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.options.ping.sip.OptionsPingTransaction;
import com.cisco.dsb.proxy.handlers.OptionsPingResponseListener;
import com.cisco.dsb.proxy.sip.ProxyPacketProcessor;
import com.cisco.dsb.trunk.dto.StaticServer;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.PostConstruct;
import javax.sip.InvalidArgumentException;
import javax.sip.PeerUnavailableException;
import javax.sip.ResponseEvent;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@CustomLog
@Component
public class OptionsPingMonitor implements OptionsPingResponseListener {
  @Autowired ProxyPacketProcessor proxyPacketProcessor;
  @Autowired OptionsPingTransaction optionsPingTransaction;
  Map<String, Boolean> elementStatus = new HashMap<>();

  @PostConstruct
  public void initOptionsPing() {
    logger.info("Initializing the OPTIONS Ping module");
    init(getServerGroupMap());
  }

  private Map<String, StaticServer> getServerGroupMap() {
    ServerGroupElement sge1 =
        com.cisco.dsb.common.sip.stack.dto.ServerGroupElement.builder()
            .ipAddress("10.78.98.54")
            .port(5060)
            .qValue(0.9f)
            .weight(-1)
            .transport(Transport.TCP)
            .build();
    ServerGroupElement sge2 =
        ServerGroupElement.builder()
            .ipAddress("10.78.98.54")
            .port(5061)
            .qValue(0.9f)
            .weight(-1)
            .transport(Transport.TCP)
            .build();
    List<ServerGroupElement> sgeList = Arrays.asList(sge2);
    StaticServer server1 =
        StaticServer.builder()
            .networkName("TCPNetwork")
            .serverGroupName("SG1")
            .elements(sgeList)
            .sgPolicy("global")
            .build();
    Map<String, StaticServer> map = new HashMap<>();
    map.put(server1.getServerGroupName(), server1);
    return map;
  }

  public void init(Map<String, StaticServer> map) {
    proxyPacketProcessor.registerOptionsListener(this);
    startMonitoring(map);
  }

  private void startMonitoring(Map<String, StaticServer> map) {
    Iterator<Entry<String, StaticServer>> itr = map.entrySet().iterator();

    logger.info("Starting pings!!");
    while (itr.hasNext()) {
      Map.Entry<String, StaticServer> entry = itr.next();
      pingPipeLine(
          entry.getValue().getNetworkName(), entry.getValue().getElements(), 30000, 5000, 500);
    }
  }

  private void pingPipeLine(
      String network,
      List<ServerGroupElement> list,
      int upInterval,
      int downInterval,
      int pingTimeOut) {
    Flux<ServerGroupElement> upElements =
        Flux.fromIterable(list)
            .filter(
                e -> {
                  Boolean status =
                      elementStatus.get(
                          network
                              + ":"
                              + e.getIpAddress()
                              + ":"
                              + e.getPort()
                              + e.getTransport().name());
                  if (status == null || status) {
                    return true;
                  }
                  return false;
                })
            .repeatWhen(
                completed ->
                    completed.delayElements(
                        Duration.ofMillis(upInterval), Schedulers.boundedElastic()));

    Flux<ServerGroupElement> downElements =
        Flux.fromIterable(list)
            .filter(
                e -> {
                  Boolean status =
                      elementStatus.get(
                          network
                              + ":"
                              + e.getIpAddress()
                              + ":"
                              + e.getPort()
                              + e.getTransport().name());
                  if (status != null && !status) {
                    return true;
                  }
                  return false;
                })
            .repeatWhen(
                completed ->
                    completed.delayElements(
                        Duration.ofMillis(downInterval), Schedulers.boundedElastic()));
    Flux.merge(upElements, downElements)
        .flatMap(
            m -> {
              return sendPingRequest(network, (ServerGroupElement) m, downInterval, pingTimeOut);
            })
        .subscribe(
            response ->
                logger.info("KALPA: {} Resposne: {}", Thread.currentThread().getName(), response));
  }

  private Mono<SIPResponse> sendPingRequest(
      String network, ServerGroupElement element, int downInterval, int pingTimeout) {

    String key =
        network
            + ":"
            + element.getIpAddress()
            + ":"
            + element.getPort()
            + element.getTransport().name();

    return Mono.defer(() -> Mono.fromFuture(createAndSendRequest(network, element)))
        .publishOn(Schedulers.boundedElastic())
        .timeout(Duration.ofMillis(pingTimeout))
        .doOnError(
            throwable -> {
              logger.error(
                  "KALPA: error {} happened for element: {} ", element, throwable.getMessage());
            })
        .retryWhen(Retry.fixedDelay(1, Duration.ofMillis(downInterval)))
        .doOnError(
            throwable -> {
              logger.info(
                  "KALPA: All Ping attempts failed for element {}. Error: {} Making status as down.",
                  element,
                  throwable.getMessage());
              elementStatus.put(key, false);
            })
        .onErrorResume(
            throwable -> {
              logger.info("KALPA: Resuming pipeline. Error Handled");
              return Mono.empty();
            })
        .doOnNext(
            n -> {
              if (!elementStatus.containsKey(key) || !elementStatus.get(key)) {
                logger.info(
                    "KALPA: {} : Making status as up for element {}",
                    Thread.currentThread().getName(),
                    element);
                elementStatus.put(key, true);
              }
            });
  }

  private CompletableFuture<SIPResponse> createAndSendRequest(
      String network, ServerGroupElement element) {
    Optional<DhruvaNetwork> optionalDhruvaNetwork = DhruvaNetwork.getNetwork(network);
    DhruvaNetwork dhruvaNetwork = optionalDhruvaNetwork.orElseGet(DhruvaNetwork::getDefault);
    Optional<SipProvider> optionalSipProvider =
        DhruvaNetwork.getProviderFromNetwork(dhruvaNetwork.getName());

    SipProvider sipProvider;
    try {
      if (optionalSipProvider.isPresent()) {
        sipProvider = optionalSipProvider.get();
      } else {
        throw new DhruvaRuntimeException(
            ErrorCode.REQUEST_NO_PROVIDER,
            String.format(
                "unable to find provider for outbound request with network:"
                    + dhruvaNetwork.getName()));
      }

      SIPRequest sipRequest = getRequest(element, sipProvider, dhruvaNetwork);
      return optionsPingTransaction.proxySendOutBoundRequest(
          sipRequest, dhruvaNetwork, sipProvider);
    } catch (SipException
        | ParseException
        | InvalidArgumentException
        | UnknownHostException
        | DhruvaRuntimeException e) {
      CompletableFuture<SIPResponse> errResponse = new CompletableFuture<>();
      errResponse.completeExceptionally(e);
      return errResponse;
    }
  }

  private SIPRequest getRequest(
      ServerGroupElement element, SipProvider sipProvider, DhruvaNetwork dhruvaNetwork)
      throws PeerUnavailableException, ParseException, InvalidArgumentException,
          UnknownHostException {
    SipFactory sipFactory = SipFactory.getInstance();
    MessageFactory messageFactory = sipFactory.createMessageFactory();
    HeaderFactory headerFactory = sipFactory.createHeaderFactory();

    ToHeader toHeader =
        JainSipHelper.createToHeader("pingTo", "pingTo", element.getIpAddress(), null);
    StringBuffer sb = new StringBuffer(80);
    sb.append("sip:" + element.getIpAddress() + ":" + element.getPort());
    SipURI requestUri = JainSipHelper.createSipURI(sb.toString());
    sb.setLength(0);

    FromHeader fromHeader =
        JainSipHelper.createFromHeader(
            "dsb",
            "dsb",
            dhruvaNetwork.getListenPoint().getHostIPAddress()
                + ":"
                + dhruvaNetwork.getListenPoint().getPort(),
            "xyz");

    sb.append("dsb@" + element.getIpAddress() + ":" + element.getPort());
    SipURI contactURI = JainSipHelper.createSipURI(sb.toString());
    sb.setLength(0);

    CallIdHeader callIdHeader;
    if (sipProvider != null) {
      callIdHeader = sipProvider.getNewCallId();
    } else {
      sb.append(
          dhruvaNetwork.getName()
              + ":"
              + element.getIpAddress()
              + ":"
              + dhruvaNetwork.getTransport().name());
      String callId = sb.toString();
      callIdHeader = (CallIdHeader) headerFactory.createHeader("Call-ID", callId);
      sb.setLength(0);
    }
    CSeqHeader cSeqHeader = headerFactory.createCSeqHeader((long) 1, Request.OPTIONS);
    MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

    String protocol = dhruvaNetwork.getTransport().name();
    List<ViaHeader> viaHeaders = new ArrayList<>();

    ViaHeader viaHeader =
        headerFactory.createViaHeader(
            dhruvaNetwork.getListenPoint().getHostIPAddress(),
            dhruvaNetwork.getListenPoint().getPort(),
            protocol,
            null);
    viaHeaders.add(viaHeader);

    SIPRequest sipRequest = new SIPRequest();
    sipRequest.setTo(toHeader);
    sipRequest.setFrom(fromHeader);
    sipRequest.setCallId(callIdHeader);
    sipRequest.setRequestURI(requestUri);
    sipRequest.setCSeq(cSeqHeader);
    sipRequest.setMethod(Request.OPTIONS);
    sipRequest.setMaxForwards(maxForwards);
    sipRequest.setVia(viaHeaders);
    sipRequest.setRemoteAddress(InetAddress.getByName(element.getIpAddress()));
    sipRequest.setRemotePort(element.getPort());
    return sipRequest;
  }

  @Override
  public void processResponse(ResponseEvent responseEvent) {

    SIPResponse response = (SIPResponse) responseEvent.getResponse();
    logger.info("Received OPTIONS Ping response {} from {} ", response, responseEvent.getSource());
    optionsPingTransaction.processResponse(responseEvent);
  }
}
