package com.cisco.dsb.connectivity.monitor.service;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.servergroup.OptionsPingPolicy;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.PostConstruct;
import javax.sip.InvalidArgumentException;
import javax.sip.SipException;
import javax.sip.SipProvider;
import lombok.CustomLog;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@CustomLog
@Component
@DependsOn("dhruvaExecutorService")
public class OptionsPingMonitor implements ApplicationListener<EnvironmentChangeEvent> {

  @Autowired ProxyPacketProcessor proxyPacketProcessor;
  @Autowired OptionsPingTransaction optionsPingTransaction;
  @Autowired CommonConfigurationProperties commonConfigurationProperties;

  protected List<Disposable> opFlux = new ArrayList<>();
  protected ConcurrentMap<String, Boolean> elementStatus = new ConcurrentHashMap<>();
  protected ConcurrentMap<String, Boolean> serverGroupStatus = new ConcurrentHashMap<>();
  protected ConcurrentHashMap<String, Set<String>> downServerGroupElementsCounter =
      new ConcurrentHashMap<>();

  @PostConstruct
  public void initOptionsPing() {
    proxyPacketProcessor.registerOptionsListener(optionsPingTransaction);
    startMonitoring(commonConfigurationProperties.getServerGroups());
  }

  protected void startMonitoring(Map<String, ServerGroup> map) {
    logger.info("Starting OPTIONS pings");
    for (Entry<String, ServerGroup> entry : map.entrySet()) {
      ServerGroup serverGroup = entry.getValue();
      // Servergroup should have pingOn = true and elements to ping
      if (!isServerGroupPingable(serverGroup)) {
        continue;
      }
      logger.info("Starting OPTIONS pings for elements of ServerGroup: {}", serverGroup);
      serverGroupStatus.putIfAbsent(serverGroup.getName(), true);
      pingPipeLine(serverGroup);
    }
  }

  protected void pingPipeLine(ServerGroup serverGroup) {
    String serverGroupName = serverGroup.getName();
    String network = serverGroup.getNetworkName();
    List<ServerGroupElement> list = serverGroup.getElements();
    OptionsPingPolicy optionsPingPolicy = serverGroup.getOptionsPingPolicy();
    int upInterval = optionsPingPolicy.getUpTimeInterval();
    int downInterval = optionsPingPolicy.getDownTimeInterval();
    int pingTimeOut = optionsPingPolicy.getPingTimeOut();
    List<Integer> failoverCodes = optionsPingPolicy.getFailoverResponseCodes();

    Flux<SIPResponse> upElementsResponses =
        getUpElementsResponses(
            serverGroupName, network, list, upInterval, downInterval, pingTimeOut, failoverCodes);
    Flux<SIPResponse> downElementsResponses =
        getDownElementsResponses(
            serverGroupName, network, list, downInterval, pingTimeOut, failoverCodes);
    opFlux.add(Flux.merge(upElementsResponses, downElementsResponses).subscribe());
  }

  protected Flux<SIPResponse> getUpElementsResponses(
      String serverGroupName,
      String network,
      List<ServerGroupElement> list,
      int upInterval,
      int downInterval,
      int pingTimeOut,
      List<Integer> failoverCodes) {
    return getUpElements(list)
        .flatMap(
            element ->
                sendPingRequestToUpElement(
                    network,
                    element,
                    downInterval,
                    pingTimeOut,
                    failoverCodes,
                    serverGroupName,
                    list.size()))
        .repeatWhen(
            completed ->
                completed.delayElements(
                    Duration.ofMillis(upInterval),
                    Schedulers.newBoundedElastic(20, 50, "BE-Up-Elements")));
  }

  protected Flux<SIPResponse> getDownElementsResponses(
      String serverGroupName,
      String network,
      List<ServerGroupElement> list,
      int downInterval,
      int pingTimeOut,
      List<Integer> failoverCodes) {
    return getDownElements(list)
        .flatMap(
            element ->
                sendPingRequestToDownElement(
                    network, element, pingTimeOut, failoverCodes, serverGroupName))
        .repeatWhen(
            completed ->
                completed.delayElements(
                    Duration.ofMillis(downInterval),
                    Schedulers.newBoundedElastic(20, 50, "BE-Down-Elements")));
  }

  protected Flux<ServerGroupElement> getUpElements(List<ServerGroupElement> list) {
    return Flux.defer(() -> Flux.fromIterable(list))
        .filter(
            e -> {
              Boolean status = elementStatus.get(e.toUniqueElementString());
              return status == null || status;
            });
  }

  protected Flux<ServerGroupElement> getDownElements(List<ServerGroupElement> list) {
    return Flux.defer(() -> Flux.fromIterable(list))
        .filter(
            e -> {
              Boolean status = elementStatus.get(e.toUniqueElementString());
              return status != null && !status;
            });
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
      List<Integer> failoverCodes,
      String serverGroupName,
      int sgeSize) {

    logger.info("Sending ping to UP element: {}", element);
    String key = element.toUniqueElementString();
    Boolean status = elementStatus.get(key);
    return Mono.defer(() -> Mono.fromFuture(createAndSendRequest(network, element)))
        .timeout(Duration.ofMillis(pingTimeout))
        .doOnError(
            throwable ->
                logger.error(
                    "Error happened for UP element: {}. Element will be retried: {}",
                    element,
                    throwable.getMessage()))
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
                  null, "All Ping attempts failed for element", element, network);
              elementStatus.put(key, false);
              checkAndMakeServerGroupDown(
                  serverGroupName, element.toUniqueElementString(), sgeSize);
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
                    n.getStatusCode(), "Error response received for element", element, network);
                elementStatus.put(key, false);
                checkAndMakeServerGroupDown(
                    serverGroupName, element.toUniqueElementString(), sgeSize);
              } else if (status == null) {
                logger.info("Adding status as UP for element: {}", element);
                elementStatus.put(key, true);
              }
            });
  }

  private void checkAndMakeServerGroupDown(String serverGroupName, String elementKey, int sgeSize) {
    Set<String> sgeHashSet;
    synchronized (this) {
      sgeHashSet =
          downServerGroupElementsCounter.getOrDefault(
              serverGroupName, ConcurrentHashMap.newKeySet());
      sgeHashSet.add(elementKey);
      downServerGroupElementsCounter.put(serverGroupName, sgeHashSet);
    }
    // this means all elements are down.
    if (sgeHashSet.size() == sgeSize) {
      serverGroupStatus.put(serverGroupName, false);
      Event.emitSGEvent(serverGroupName, true);
    }
    logger.info("Total DOWN Elements for {}: {}", serverGroupName, sgeHashSet);
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
      String network,
      ServerGroupElement element,
      int pingTimeout,
      List<Integer> failoverCodes,
      String serverGroupName) {
    logger.info("Sending ping to DOWN element: {}", element);
    String key = element.toUniqueElementString();
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
                Event.emitSGElementUpEvent(element, network);
                elementStatus.put(key, true);
                makeServerGroupUp(serverGroupName, element.toUniqueElementString());
              }
            });
  }

  private void makeServerGroupUp(String serverGroupName, String elementKey) {
    Boolean sgStatus = serverGroupStatus.get(serverGroupName);
    if (sgStatus != null && !sgStatus) {
      serverGroupStatus.put(serverGroupName, true);
      Event.emitSGEvent(serverGroupName, false);
    }
    Set<String> sgeHashSet = downServerGroupElementsCounter.get(serverGroupName);
    if (sgeHashSet != null) {
      sgeHashSet.remove(elementKey);
    }
    logger.info("Total DOWN Elements for {}: {}", serverGroupName, sgeHashSet);
  }

  protected CompletableFuture<SIPResponse> createAndSendRequest(
      String network, ServerGroupElement element) throws DhruvaRuntimeException {
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
            "unable to find provider for outbound request with network:" + dhruvaNetwork.getName());
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

  private boolean isServerGroupPingable(@NotNull ServerGroup serverGroup) {
    return (serverGroup.isPingOn() && (serverGroup.getElements() != null));
  }

  @Override
  public void onApplicationEvent(@NotNull EnvironmentChangeEvent event) {
    logger.info("onApplicationEvent: {} invoked on OptionsPingMonitor for {}", event.getKeys());

    if (event.getKeys().stream()
        .anyMatch(
            key -> {
              // Refresh OPTIONS pings only when common config has some changes.
              return key.contains("common");
            })) {
      logger.info("Change detected in Common Config, Restarting OPTIONS pings.");
      RefreshHandle refreshHandle = new RefreshHandle();
      Thread postRefresh = new Thread(refreshHandle);
      postRefresh.start();
    }
  }

  protected void disposeExistingFlux() {
    logger.info("Disposing existing fluxes");
    opFlux.forEach(Disposable::dispose);
  }

  /**
   * This method will update the state of all the maps maintaining sg and elements status after
   * config refresh. So in case if elements were removed they will be removed from here too. i.e.
   * elementStatus, serverGroupStatus, downServerGroupElementsCounter
   */
  protected void cleanUpMaps() {
    Map<String, ServerGroup> sgMap = commonConfigurationProperties.getServerGroups();
    List<String> sgNameList = new ArrayList<>();
    List<String> sgeNameList = new ArrayList<>();

    for (Map.Entry<String, ServerGroup> entry : sgMap.entrySet()) {
      String sgName = entry.getValue().getName();
      ServerGroup sg = entry.getValue();
      sgNameList.add(sgName);
      sg.getElements().forEach(element -> sgeNameList.add(element.toUniqueElementString()));
    }

    elementStatus.entrySet().removeIf(elementEntry -> !sgeNameList.contains(elementEntry.getKey()));
    serverGroupStatus.entrySet().removeIf(sgEntry -> !sgNameList.contains(sgEntry.getKey()));
    downServerGroupElementsCounter
        .entrySet()
        .removeIf(sgEntry -> !sgNameList.contains(sgEntry.getKey()));

    for (Map.Entry<String, Set<String>> entry : downServerGroupElementsCounter.entrySet()) {
      Set<String> sgeNameSet = entry.getValue();
      sgeNameSet.removeIf(sgeName -> !sgeNameList.contains(sgeName));
    }
    logger.info("Updated serverGroupStatus: {}", serverGroupStatus);
    logger.info("Updated elementStatus: {}", elementStatus);
    logger.info("Updated downServerGroupElementsCounter: {}", downServerGroupElementsCounter);
  }

  protected class RefreshHandle implements Runnable {
    @Override
    public void run() {
      disposeExistingFlux();
      opFlux.clear();
      cleanUpMaps();
      startMonitoring(commonConfigurationProperties.getServerGroups());
    }
  }
}
