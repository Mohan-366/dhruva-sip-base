package com.cisco.dsb.options.ping.service;

import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.stack.dto.ServerGroupElement;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.log.event.Event;
import com.cisco.dsb.options.ping.sip.OptionsPingTransaction;
import com.cisco.dsb.options.ping.util.OptionsUtil;
import com.cisco.dsb.proxy.sip.ProxyPacketProcessor;
import com.cisco.dsb.trunk.dto.StaticServer;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.PostConstruct;
import javax.sip.InvalidArgumentException;
import javax.sip.SipException;
import javax.sip.SipProvider;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@CustomLog
@Component
@DependsOn("dhruvaExecutorService")
public class OptionsPingMonitor {

  @Autowired ProxyPacketProcessor proxyPacketProcessor;
  @Autowired OptionsPingTransaction optionsPingTransaction;

  protected ConcurrentMap<Integer, Boolean> elementStatus = new ConcurrentHashMap<>();
  private static final int THREAD_CAP = 20;
  private static final int QUEUE_TASK_CAP = 100;
  private static final String THREAD_NAME_PREFIX = "OPTIONS-PING";
  Scheduler optionsPingScheduler;

  @PostConstruct
  public void initOptionsPing() {
    init(getServerGroupMap(), getFailoverCodes());
  }

  private List<Integer> getFailoverCodes() {
    List<Integer> failoverCodes = new ArrayList<>();
    failoverCodes.add(503);
    return failoverCodes;
  }

  private Map<String, StaticServer> getServerGroupMap() {

    List<ServerGroupElement> sgeList = new ArrayList<>();
    for (int i = 5061; i < 5066; i++) {
      sgeList.add(
          ServerGroupElement.builder()
              .ipAddress("10.78.98.54")
              .port(i)
              .qValue(0.9f)
              .weight(-1)
              .transport(Transport.TCP)
              .build());
    }
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

  public void init(Map<String, StaticServer> map, List<Integer> failoverCodes) {
    proxyPacketProcessor.registerOptionsListener(optionsPingTransaction);
    optionsPingScheduler =
        Schedulers.newBoundedElastic(THREAD_CAP, QUEUE_TASK_CAP, THREAD_NAME_PREFIX);
    startMonitoring(map, failoverCodes);
  }

  private void startMonitoring(Map<String, StaticServer> map, List<Integer> failoverCodes) {
    Iterator<Entry<String, StaticServer>> itr = map.entrySet().iterator();
    logger.info("Starting OPTIONS pings!!");
    while (itr.hasNext()) {
      Map.Entry<String, StaticServer> entry = itr.next();
      pingPipeLine(
          entry.getValue().getNetworkName(),
          entry.getValue().getElements(),
          30000,
          5000,
          500,
          failoverCodes);
    }
  }

  private void pingPipeLine(
      String network,
      List<ServerGroupElement> list,
      int upInterval,
      int downInterval,
      int pingTimeOut,
      List<Integer> failoverCodes) {

    Flux<ServerGroupElement> upElements =
        Flux.fromIterable(list)
            .filter(
                e -> {
                  Boolean status = elementStatus.get(e.hashCode());
                  if (status == null || status) {
                    return true;
                  }
                  return false;
                })
            .repeatWhen(
                completed ->
                    completed.delayElements(Duration.ofMillis(upInterval), optionsPingScheduler));

    Flux<ServerGroupElement> downElements =
        Flux.fromIterable(list)
            .filter(
                e -> {
                  Boolean status = elementStatus.get(e.hashCode());
                  if (status != null && !status) {
                    return true;
                  }
                  return false;
                })
            .repeatWhen(
                completed ->
                    completed.delayElements(Duration.ofMillis(downInterval), optionsPingScheduler));
    Flux.merge(upElements, downElements)
        .flatMap(
            element -> {
              Boolean status = elementStatus.get(element.hashCode());
              if (status == null || status) {
                logger.debug("Sending ping to UP element: {}", element);
                return sendPingRequestToUpElement(
                    network,
                    (ServerGroupElement) element,
                    downInterval,
                    pingTimeOut,
                    failoverCodes);
              } else {
                logger.debug("Sending ping to DOWN element: {}", element);
                return sendPingRequestToDownElement(
                    network, (ServerGroupElement) element, pingTimeOut, failoverCodes);
              }
            })
        .subscribe();
  }

  /**
   * Separate methods to ping up elements and retries will be performed only in case of up elements.
   *
   * @param network
   * @param element
   * @param downInterval
   * @param pingTimeout
   * @param failoverCodes
   * @return
   */
  private Mono<SIPResponse> sendPingRequestToUpElement(
      String network,
      ServerGroupElement element,
      int downInterval,
      int pingTimeout,
      List<Integer> failoverCodes) {

    Integer key = element.hashCode();
    Boolean status = elementStatus.get(key);
    return Mono.defer(() -> Mono.fromFuture(createAndSendRequest(network, element)))
        .timeout(Duration.ofMillis(pingTimeout))
        .doOnError(
            throwable -> {
              logger.error(
                  "Error happened for UP element: {}. Element will be retried: {}",
                  element,
                  throwable.getMessage());
            })
        .retryWhen(
            Retry.fixedDelay(
                OptionsUtil.getNumRetry(element.getTransport()), Duration.ofMillis(downInterval)))
        .onErrorResume(
            throwable -> {
              logger.info(
                  "All Ping attempts failed for UP element: {}. Marking status as DOWN. Error: {} ",
                  element,
                  throwable.getMessage());
              Event.emitSGElementDownEvent(
                  null,
                  "All Ping attempts failed for element",
                  element.getIpAddress(),
                  element.getPort(),
                  element.getTransport(),
                  network);
              elementStatus.put(key, false);
              return Mono.empty();
            })
        .doOnNext(
            n -> {
              if (failoverCodes.stream().anyMatch(val -> val == n.getStatusCode())) {
                elementStatus.put(key, false);
                logger.info(
                    "{} received for UP element: {}. Marking it as DOWN.",
                    n.getStatusCode(),
                    element);
                Event.emitSGElementDownEvent(
                    n.getStatusCode(),
                    "Error response received for element",
                    element.getIpAddress(),
                    element.getPort(),
                    element.getTransport(),
                    network);
                elementStatus.put(key, false);
              } else if (status == null) {
                logger.info("Adding status as UP for element: {}", element);
                elementStatus.put(key, true);
              }
            });
  }

  /**
   * Separate methods to ping up elements and retries will not be performed in case of down
   * elements.
   *
   * @param network
   * @param element
   * @param pingTimeout
   * @param failoverCodes
   * @return
   */
  private Mono<SIPResponse> sendPingRequestToDownElement(
      String network, ServerGroupElement element, int pingTimeout, List<Integer> failoverCodes) {

    Integer key = element.hashCode();
    return Mono.defer(() -> Mono.fromFuture(createAndSendRequest(network, element)))
        .timeout(Duration.ofMillis(pingTimeout))
        .onErrorResume(
            throwable -> {
              logger.info(
                  "Error happened for element: {}. Error: {} Keeping status as DOWN.",
                  element,
                  throwable.getMessage());
              return Mono.empty();
            })
        .doOnNext(
            n -> {
              if (failoverCodes.stream().anyMatch(val -> val == n.getStatusCode())) {
                logger.info("503 received for element: {}. Keeping status as DOWN.", element);
              } else {
                logger.info("Marking status as UP for element: {}", element);
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

      SIPRequest sipRequest = OptionsUtil.getRequest(element, dhruvaNetwork, sipProvider);
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
}
