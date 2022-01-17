package com.cisco.dsb.connectivity.monitor.service;

import com.cisco.dsb.common.config.ConfigUpdateListener;
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
import java.util.List;
import java.util.Map;
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
import org.springframework.beans.factory.annotation.Autowired;
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
public class OptionsPingMonitor implements ConfigUpdateListener {

  @Autowired ProxyPacketProcessor proxyPacketProcessor;
  @Autowired OptionsPingTransaction optionsPingTransaction;
  @Autowired CommonConfigurationProperties commonConfigurationProperties;

  private Disposable opFlux;
  protected ConcurrentMap<Integer, Boolean> elementStatus = new ConcurrentHashMap<>();
  protected ConcurrentMap<String, Boolean> serverGroupStatus = new ConcurrentHashMap<>();
  protected ConcurrentHashMap<String, Set<Integer>> downServerGroupElementsCounter =
      new ConcurrentHashMap<>();

  @PostConstruct
  public void initOptionsPing() {
    logger.info("KALPA: Registering {} as ConfigUpdateListener", this.getClass().getSimpleName());
    commonConfigurationProperties.registerConfigUpdateListener(this);
    init(commonConfigurationProperties.getServerGroups());
  }

  public void init(Map<String, ServerGroup> map) {
    proxyPacketProcessor.registerOptionsListener(optionsPingTransaction);
    startMonitoring(map);
  }

  private void startMonitoring(Map<String, ServerGroup> map) {
    logger.info(
        "Starting OPTIONS pings!! : {}", commonConfigurationProperties.getServerGroups().values());
    Flux<SIPResponse> upElementsResponse =
        Flux.defer(
                () -> Flux.fromIterable(commonConfigurationProperties.getServerGroups().values()))
            .filter(this::isServerGroupPingable)
            .flatMap(
                serverGroup -> {
                  OptionsPingPolicy optionsPingPolicy = serverGroup.getOptionsPingPolicy();
                  if (optionsPingPolicy == null) {
                    optionsPingPolicy = OptionsUtil.getDefaultOptionsPingPolicy();
                  }
                  OptionsPingPolicy finalOptionsPingPolicy = optionsPingPolicy;
                  return upElementsFlux(
                          serverGroup.getElements(), optionsPingPolicy.getUpTimeInterval())
                      .flatMap(
                          element -> {
                            serverGroupStatus.putIfAbsent(serverGroup.getHostName(), true);
                            return sendPingRequestToUpElement(
                                serverGroup.getNetworkName(),
                                element,
                                finalOptionsPingPolicy.getDownTimeInterval(),
                                finalOptionsPingPolicy.getPingTimeOut(),
                                finalOptionsPingPolicy.getFailoverResponseCodes(),
                                serverGroup.getHostName(),
                                serverGroup.getElements().size());
                          })
                      .repeatWhen(
                          completed ->
                              completed.delayElements(
                                  Duration.ofMillis(
                                      serverGroup.getOptionsPingPolicy().getUpTimeInterval()),
                                  Schedulers.newBoundedElastic(40, 100, "BE-Up-Elements")));
                });

    Flux<SIPResponse> downElementsResponse =
        Flux.defer(
                () -> Flux.fromIterable(commonConfigurationProperties.getServerGroups().values()))
            .filter(this::isServerGroupPingable)
            .flatMap(
                serverGroup -> {
                  OptionsPingPolicy optionsPingPolicy = serverGroup.getOptionsPingPolicy();
                  return downElementsFlux(
                          serverGroup.getElements(), optionsPingPolicy.getDownTimeInterval())
                      .flatMap(
                          element ->
                              sendPingRequestToDownElement(
                                  serverGroup.getNetworkName(),
                                  element,
                                  optionsPingPolicy.getPingTimeOut(),
                                  optionsPingPolicy.getFailoverResponseCodes(),
                                  serverGroup.getHostName()))
                      .repeatWhen(
                          completed ->
                              completed.delayElements(
                                  Duration.ofMillis(
                                      serverGroup.getOptionsPingPolicy().getDownTimeInterval()),
                                  Schedulers.newBoundedElastic(40, 100, "BE-Up-Elements")));
                });
    opFlux = Flux.merge(upElementsResponse, downElementsResponse).subscribe();
  }

  protected Flux<ServerGroupElement> upElementsFlux(
      List<ServerGroupElement> list, int upTimeInterval) {
    return Flux.defer(() -> Flux.fromIterable(list))
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
                    Duration.ofMillis(upTimeInterval),
                    Schedulers.newBoundedElastic(5, 50, "BE-Up-Elements")));
  }

  protected Flux<ServerGroupElement> downElementsFlux(
      List<ServerGroupElement> list, int downIntervalTime) {
    return Flux.defer(() -> Flux.fromIterable(list))
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
                    Duration.ofMillis(downIntervalTime),
                    Schedulers.newBoundedElastic(5, 50, "BE-Down-Elements")));
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
    Integer key = element.hashCode();
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
              checkAndMakeServerGroupDown(serverGroupName, element.hashCode(), sgeSize);
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
                checkAndMakeServerGroupDown(serverGroupName, element.hashCode(), sgeSize);
              } else if (status == null) {
                logger.info("Adding status as UP for element: {}", element);
                elementStatus.put(key, true);
              }
            });
  }

  private void checkAndMakeServerGroupDown(
      String serverGroupName, Integer elementHashCode, int sgeSize) {
    Set<Integer> sgeHashSet;
    synchronized (this) {
      sgeHashSet =
          downServerGroupElementsCounter.getOrDefault(
              serverGroupName, ConcurrentHashMap.newKeySet());
      sgeHashSet.add(elementHashCode);
      downServerGroupElementsCounter.put(serverGroupName, sgeHashSet);
    }
    // this means all elements are down.
    if (sgeHashSet.size() == sgeSize) {
      serverGroupStatus.put(serverGroupName, false);
      Event.emitSGEvent(serverGroupName, true);
    }
    logger.info("KALPA: downServerGroupElementsCounter content: {}", sgeHashSet);
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
                Event.emitSGElementUpEvent(element, network);
                elementStatus.put(key, true);
                makeServerGroupUp(serverGroupName, element.hashCode());
              }
            });
  }

  private void makeServerGroupUp(String serverGroupName, Integer elementHashCode) {
    Boolean sgStatus = serverGroupStatus.get(serverGroupName);
    if (sgStatus != null && !sgStatus) {
      serverGroupStatus.put(serverGroupName, true);
      Event.emitSGEvent(serverGroupName, false);
    }
    Set<Integer> sgeHashSet = downServerGroupElementsCounter.get(serverGroupName);
    if (sgeHashSet != null) {
      sgeHashSet.remove(elementHashCode);
    }
    logger.info("KALPA: downServerGroupElementsCounter content: {}", sgeHashSet);
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

  private boolean isServerGroupPingable(ServerGroup serverGroup) {

    if (serverGroup.isPingOn() && serverGroup.getElements() != null) {
      logger.info("ServerGroup {} is pingeable!", serverGroup);
      return true;
    } else {
      logger.info("ServerGroup {} is not pingeable!", serverGroup);
      return false;
    }
  }

  public class MyThreadforOPRestart extends Thread {

    @Override
    public void run() {
      restartFlux();
    }
  }

  private void restartFlux() {
    opFlux.dispose();
    while (!opFlux.isDisposed()) {
      logger.error("KALPA: Not disposed yet");
    }
    logger.info("KALPA: flux is now disposed! restaring it!");
    startMonitoring(commonConfigurationProperties.getServerGroups());
  }

  public void configUpdated() {
    logger.info("KALPA: in optionsPingMonitor");
    MyThreadforOPRestart myThreadforOPRestart = new MyThreadforOPRestart();
    myThreadforOPRestart.start();
  }
}
