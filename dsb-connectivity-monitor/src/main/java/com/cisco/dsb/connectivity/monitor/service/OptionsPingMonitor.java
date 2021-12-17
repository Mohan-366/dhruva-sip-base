package com.cisco.dsb.connectivity.monitor.service;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.util.log.event.Event;
import com.cisco.dsb.connectivity.monitor.sip.OptionsPingTransaction;
import com.cisco.dsb.connectivity.monitor.util.OptionsUtil;
import com.cisco.dsb.proxy.sip.ProxyPacketProcessor;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.time.Duration;
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
  @Autowired CommonConfigurationProperties commonConfigurationProperties;

  protected ConcurrentMap<Integer, Boolean> elementStatus = new ConcurrentHashMap<>();
  private static final int THREAD_CAP = 20; // TODO: add these as config properties
  private static final int QUEUE_TASK_CAP = 100;
  private static final String THREAD_NAME_PREFIX = "OPTIONS-PING";
  Scheduler optionsPingScheduler;

  @PostConstruct
  public void initOptionsPing() {
    init(commonConfigurationProperties.getServerGroups());
  }

  public void init(Map<String, ServerGroup> map) {
    proxyPacketProcessor.registerOptionsListener(optionsPingTransaction);
    optionsPingScheduler =
        Schedulers.newBoundedElastic(THREAD_CAP, QUEUE_TASK_CAP, THREAD_NAME_PREFIX);
    startMonitoring(map);
  }

  private void startMonitoring(Map<String, ServerGroup> map) {
    Iterator<Entry<String, ServerGroup>> itr = map.entrySet().iterator();
    logger.info("Starting OPTIONS pings!!");
    while (itr.hasNext()) {
      Map.Entry<String, ServerGroup> entry = itr.next();
      ServerGroup serverGroup = entry.getValue();
      if (entry.getValue().isPingOn()) {
        pingPipeLine(
            serverGroup.getNetworkName(),
            serverGroup.getElements(),
            serverGroup.getOptionsPingPolicy().getUpTimeInterval(),
            serverGroup.getOptionsPingPolicy().getDownTimeInterval(),
            serverGroup.getOptionsPingPolicy().getPingTimeOut(),
            serverGroup.getOptionsPingPolicy().getFailoverResponseCodes());
      }
    }
  }

  protected Flux<ServerGroupElement> upElementsFlux(List<ServerGroupElement> list, int upInterval) {
    return Flux.fromIterable(list)
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
                completed.delayElements(
                    Duration.ofMillis(upInterval),
                    Schedulers.newBoundedElastic(40, 100, "BE-Up-Elements")));
  }

  protected Flux<ServerGroupElement> downElementsFlux(
      List<ServerGroupElement> list, int downInterval) {
    return Flux.fromIterable(list)
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
                completed.delayElements(
                    Duration.ofMillis(downInterval),
                    Schedulers.newBoundedElastic(40, 100, "BE-Down-Elements")));
  }

  protected void pingPipeLine(
      String network,
      List<ServerGroupElement> list,
      int upInterval,
      int downInterval,
      int pingTimeOut,
      List<Integer> failoverCodes) {

    Flux<SIPResponse> upElementsResponse =
        upElementsFlux(list, upInterval)
            .flatMap(
                element -> {
                  return sendPingRequestToUpElement(
                      network, element, downInterval, pingTimeOut, failoverCodes);
                });

    Flux<SIPResponse> downElementsResponse =
        downElementsFlux(list, downInterval)
            .flatMap(
                element -> {
                  return sendPingRequestToDownElement(network, element, pingTimeOut, failoverCodes);
                });

    Flux.merge(upElementsResponse, downElementsResponse).subscribe();
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
  protected Mono<SIPResponse> sendPingRequestToUpElement(
      String network,
      ServerGroupElement element,
      int downInterval,
      int pingTimeout,
      List<Integer> failoverCodes) {

    logger.info("Sending ping to UP element: {}", element);
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
  protected Mono<SIPResponse> sendPingRequestToDownElement(
      String network, ServerGroupElement element, int pingTimeout, List<Integer> failoverCodes) {
    logger.info("Sending ping to DOWN element: {}", element);
    Integer key = element.hashCode();
    return Mono.defer(() -> Mono.fromFuture(createAndSendRequest(network, element)))
        .timeout(Duration.ofMillis(pingTimeout))
        .onErrorResume(
            throwable -> {
              logger.error(
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
                Event.emitSGElementUpEvent(
                    element.getIpAddress(), element.getPort(), element.getTransport(), network);
                elementStatus.put(key, true);
              }
            });
  }

  protected CompletableFuture<SIPResponse> createAndSendRequest(
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
