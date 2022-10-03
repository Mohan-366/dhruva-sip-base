package com.cisco.dsb.connectivity.monitor.service;

import static com.cisco.dsb.connectivity.monitor.util.OptionsUtil.getNumRetry;
import static com.cisco.dsb.connectivity.monitor.util.OptionsUtil.getRequest;
import static com.cisco.dsb.connectivity.monitor.util.OptionsUtil.isSGMapUpdated;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.servergroup.*;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.util.log.event.Event;
import com.cisco.dsb.connectivity.monitor.dto.ResponseData;
import com.cisco.dsb.connectivity.monitor.dto.Status;
import com.cisco.dsb.connectivity.monitor.sip.OptionsPingTransaction;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import javax.annotation.PostConstruct;
import javax.sip.InvalidArgumentException;
import javax.sip.SipException;
import javax.sip.SipProvider;
import lombok.CustomLog;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@CustomLog
@Component
@DependsOn("dhruvaExecutorService")
public class OptionsPingMonitor implements ApplicationListener<EnvironmentChangeEvent> {

  @Autowired ProxyPacketProcessor proxyPacketProcessor;
  @Autowired OptionsPingTransaction optionsPingTransaction;
  @Autowired CommonConfigurationProperties commonConfigurationProperties;
  @Autowired DnsServerGroupUtil dnsServerGroupUtil;
  @Autowired public MetricService metricsService;
  protected List<Disposable> opFlux = new ArrayList<>();
  protected ConcurrentMap<String, Status> elementStatus = new ConcurrentHashMap<>();
  protected ConcurrentMap<String, Boolean> serverGroupStatus = new ConcurrentHashMap<>();
  private Map<String, ServerGroup> localSGMap;
  @Setter private int maxFetchTime = 1500;
  @Setter private int fetchTime = 500;
  @Setter private int fetchIncrementTime = 250;
  private final Scheduler upElementsScheduler =
      Schedulers.newBoundedElastic(100, 100, "BE-Up-Elements");
  private final Scheduler downElementsScheduler =
      Schedulers.newBoundedElastic(100, 100, "BE-Down-Elements");

  @PostConstruct
  public void initOptionsPing() {
    proxyPacketProcessor.registerOptionsListener(optionsPingTransaction);
    startMonitoring(commonConfigurationProperties.getServerGroups());
  }

  protected void startMonitoring(Map<String, ServerGroup> map) {
    logger.debug("Starting OPTIONS pings. Map {}", map);
    for (Entry<String, ServerGroup> entry : map.entrySet()) {
      ServerGroup serverGroup = entry.getValue();
      // Servergroup should have pingOn = true and elements to ping
      if (!isServerGroupPingable(serverGroup)) {
        continue;
      }
      logger.info("Starting OPTIONS pings for elements of ServerGroup: {}", serverGroup);
      serverGroupStatus.putIfAbsent(serverGroup.getHostName(), true);
      pingPipeLine(serverGroup);
    }
  }

  public void pingPipeLine(ServerGroup serverGroup) {
    opFlux.add(createUpElementsFlux(serverGroup).subscribe());
    opFlux.add(createDownElementsFlux(serverGroup).subscribe());
  }

  protected Flux<ResponseData> createUpElementsFlux(ServerGroup serverGroup) {
    if (!serverGroupStatus.containsKey(serverGroup.getHostName())) {
      logger.info("Marking ServerGroup {} as UP initially", serverGroup.getName());
      serverGroupStatus.put(serverGroup.getHostName(), true);
      Event.emitSGEvent(serverGroup.getName(), false);
      metricsService.sendSGMetric(serverGroup.getName(), true);
    }
    return getElements(serverGroup)
        .filter(
            upFilter.and(isExpiredStatus(serverGroup.getOptionsPingPolicy().getUpTimeInterval())))
        .flatMap(
            element ->
                sendPingRequestToUpElement(
                    serverGroup.getNetworkName(), element, serverGroup.getOptionsPingPolicy()))
        .transformDeferred(responseFlux -> handleStatusUpdate(responseFlux, serverGroup, true))
        .onErrorResume(
            (err) -> {
              logger.warn("Uncaught error while creating UP element flux {},restarting flux", err);
              return Mono.empty();
            })
        .repeatWhen(
            completed ->
                completed.delayElements(
                    Duration.ofMillis(serverGroup.getOptionsPingPolicy().getUpTimeInterval()),
                    upElementsScheduler));
  }

  protected Flux<ResponseData> createDownElementsFlux(ServerGroup serverGroup) {
    return getElements(serverGroup)
        .filter(
            upFilter
                .negate()
                .and(isExpiredStatus(serverGroup.getOptionsPingPolicy().getDownTimeInterval())))
        .flatMap(
            element ->
                sendPingRequestToDownElement(
                    serverGroup.getNetworkName(), element, serverGroup.getOptionsPingPolicy()))
        .transform(responseFlux -> handleStatusUpdate(responseFlux, serverGroup, false))
        .onErrorResume(
            (err) -> {
              logger.warn(
                  "Uncaught error while creating DOWN element flux {},restarting flux", err);
              return Mono.empty();
            })
        .repeatWhen(
            completed ->
                completed.delayElements(
                    Duration.ofMillis(serverGroup.getOptionsPingPolicy().getDownTimeInterval()),
                    downElementsScheduler));
  }

  protected Flux<ServerGroupElement> getElements(ServerGroup serverGroup) {
    Flux<ServerGroupElement> elements;
    if (serverGroup.getSgType() == SGType.STATIC)
      elements = Flux.fromIterable(serverGroup.getElements());
    else
      elements =
          Flux.defer(() -> dnsServerGroupUtil.createDNSServerGroup(serverGroup, null))
              .flatMap(sg -> Flux.fromIterable(sg.getElements()));
    return elements;
  }

  /**
   * Separate methods to ping up elements and retries will be performed only in case of up elements.
   *
   * @param network network to use while sending OPTIONS
   * @param element element to send OPTIONS to
   * @param optionsPingPolicy policy to use while retrying with various ping intervals
   * @return ResponseData with element and either of SIPResponse or Exception.
   */
  protected Mono<ResponseData> sendPingRequestToUpElement(
      String network, ServerGroupElement element, OptionsPingPolicy optionsPingPolicy) {

    int downInterval = optionsPingPolicy.getDownTimeInterval();
    int pingTimeout = optionsPingPolicy.getPingTimeOut();

    logger.debug("Sending ping to UP element: {}", element);
    return Mono.defer(
            () -> Mono.fromFuture(createAndSendRequest(network, element, optionsPingPolicy)))
        .timeout(Duration.ofMillis(pingTimeout), upElementsScheduler)
        .doOnError(
            throwable ->
                logger.error(
                    "Error occurred for UP element: {}. Element will be retried: {}",
                    element,
                    throwable.getMessage()))
        .retryWhen(
            Retry.fixedDelay(getNumRetry(element.getTransport()), Duration.ofMillis(downInterval)))
        .map(sipResponse -> new ResponseData(sipResponse, element))
        .onErrorResume(throwable -> Mono.just(new ResponseData(new Exception(throwable), element)));
  }

  /**
   * Separate methods to ping up elements and retries will not be performed in case of down
   * elements.
   *
   * @param network network to use while sending OPTIONS
   * @param element element to send OPTIONS to
   * @param optionsPingPolicy policy to use while retrying with various ping intervals
   * @return ResponseData with element and either of SIPResponse or Exception.
   */
  protected Mono<ResponseData> sendPingRequestToDownElement(
      String network, ServerGroupElement element, OptionsPingPolicy optionsPingPolicy) {
    int pingTimeout = optionsPingPolicy.getPingTimeOut();
    logger.debug("Sending ping to DOWN element: {}", element);
    return Mono.defer(
            () -> Mono.fromFuture(createAndSendRequest(network, element, optionsPingPolicy)))
        .timeout(Duration.ofMillis(pingTimeout), downElementsScheduler)
        .map(sipResponse -> new ResponseData(sipResponse, element))
        .onErrorResume(
            throwable -> {
              logger.debug(
                  "Error happened for element: {}. Error: {} Keeping status as DOWN.",
                  element,
                  throwable.getMessage());
              return Mono.just(new ResponseData(new Exception(throwable), element));
            });
  }

  private Flux<ResponseData> handleStatusUpdate(
      Flux<ResponseData> flux, ServerGroup serverGroup, boolean upFlux) {
    AtomicBoolean sgUp = new AtomicBoolean(false);
    AtomicBoolean triedAtleastOne = new AtomicBoolean(false);
    return flux.doOnNext(
            responseData -> {
              triedAtleastOne.set(true);
              handleSGEUpdate(responseData, serverGroup, sgUp);
            })
        .doOnComplete(
            () -> {
              if (triedAtleastOne.get()) handleSGUpdate(serverGroup, sgUp, upFlux);
            })
        .doOnSubscribe(
            subscription -> {
              triedAtleastOne.set(false);
              sgUp.set(false);
            });
  }

  protected CompletableFuture<SIPResponse> createAndSendRequest(
      String network, ServerGroupElement element, OptionsPingPolicy optionsPingPolicy)
      throws DhruvaRuntimeException {
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

      SIPRequest sipRequest = getRequest(element, dhruvaNetwork, sipProvider, optionsPingPolicy);
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

  private void handleSGEUpdate(
      ResponseData responseData, ServerGroup serverGroup, AtomicBoolean sgStatus) {
    Exception responseException = responseData.getException();
    ServerGroupElement element = responseData.getElement();
    String key = element.toUniqueElementString();
    Status prevStatus =
        elementStatus.getOrDefault(key, new Status(true, System.currentTimeMillis()));
    // RejectedExecutionexception can occur when scheduler is unavailable. This exception should not
    // mark the element as DOWN
    // since OPTIONS was not sent for the given element
    if (responseException != null
        && !(responseException.getCause() instanceof RejectedExecutionException)) {
      logger.error(
          "All Ping attempts failed for element: {}. Marking status as DOWN. Error: {} ",
          element,
          responseException.getMessage());
      if(prevStatus.isUp()) {
        Event.emitSGElementDownEvent(
                serverGroup.getName(),
                null,
                "Error response received for element",
                element,
                serverGroup.getNetworkName());
        metricsService.sendSGElementMetric(serverGroup.getName(), element.getUniqueString(), false);
        elementStatus.put(key, prevStatus.setUp(false));
      }
      return;
    }
    // check response and mark element as UP/DOWN based on policy
    int responseCode = responseData.getSipResponse().getStatusCode();
    boolean failed =
        serverGroup.getOptionsPingPolicy().getFailureResponseCodes().contains(responseCode);
    if (prevStatus.isUp() && failed) {
      // element transitioned from UP to DOWN
      elementStatus.put(key, prevStatus.setUp(false));
      logger.info("{} received for UP element: {}. Marking it as DOWN.", responseCode, element);
      Event.emitSGElementDownEvent(
          serverGroup.getName(),
          responseCode,
          "Error response received for element",
          element,
          serverGroup.getNetworkName());
      metricsService.sendSGElementMetric(serverGroup.getName(), element.getUniqueString(), false);
    } else if (!prevStatus.isUp() && !failed) {
      elementStatus.put(key, prevStatus.setUp(true));
      logger.info("{} received for DOWN element: {}. Marking it as UP.", responseCode, element);
      Event.emitSGElementUpEvent(serverGroup.getName(), element, serverGroup.getNetworkName());
      metricsService.sendSGElementMetric(serverGroup.getName(), element.getUniqueString(), true);
    }
    if (!failed) sgStatus.compareAndSet(false, true);
  }

  private void handleSGUpdate(ServerGroup serverGroup, AtomicBoolean sgUp, boolean upFlux) {
    boolean previousStatus = serverGroupStatus.getOrDefault(serverGroup.getHostName(), true);
    boolean currentStatus = sgUp.get();
    // mark sg up if currentStatus is up and previousStatus is down
    if (currentStatus && !previousStatus) {
      this.serverGroupStatus.put(serverGroup.getHostName(), true);
      logger.info("Marking Servergroup {} as UP from DOWN", serverGroup.getName());
      Event.emitSGEvent(serverGroup.getName(), false);
      metricsService.sendSGMetric(serverGroup.getName(), true);
    }
    // marking sg down if currentStatus is down and prev is up and if it's upFlux only
    else if (!currentStatus && previousStatus && upFlux) {
      this.serverGroupStatus.put(serverGroup.getHostName(), false);
      logger.info("Marking ServerGroup {} as DOWN from UP", serverGroup.getName());
      Event.emitSGEvent(serverGroup.getName(), true);
      metricsService.sendSGMetric(serverGroup.getName(), false);
    }
  }

  private boolean isServerGroupPingable(@NotNull ServerGroup serverGroup) {
    return (serverGroup.isPingOn()
        && (serverGroup.getSgType() != SGType.STATIC || serverGroup.getElements() != null));
  }

  @Override
  public void onApplicationEvent(@NotNull EnvironmentChangeEvent event) {

    if (event.getKeys().stream()
        .anyMatch(
            key -> {
              // Refresh OPTIONS pings only when serverGroup config has some changes.
              return (key.contains("serverGroups") || key.contains("optionsPingPolicy"));
            })) {
      logger.info("ServerGroups environment config changed with keys :{}", event.getKeys());
      RefreshHandle refreshHandle = new RefreshHandle();
      Thread postRefresh = new Thread(refreshHandle);
      postRefresh.start();
    }
  }

  protected void disposeExistingFlux() {
    logger.debug("Disposing existing fluxes");
    opFlux.forEach(Disposable::dispose);
  }

  /*
   * Checking for updated values after a certain time interval and retrying if it is not available
   * is a hack added to overcome a bug in the kubernetes client which takes some time (few milli seconds)
   * to update the Bean under refreshScope after doing get on that object when a config refresh happens .
   */
  protected void getUpdatedMaps() {
    int originalFetchTime = fetchTime;
    boolean isUpdated = false;
    logger.info("Change detected in ServerGroups Config. Fetching latest SG Map");
    while (fetchTime < maxFetchTime) {
      try {
        Thread.sleep(fetchTime);
      } catch (InterruptedException e) {
        logger.error("Exception happened for sleep.");
      }
      if (isSGMapUpdated(commonConfigurationProperties.getServerGroups(), localSGMap)) {
        logger.info("SG Map Updated. Working with new map!");
        localSGMap = commonConfigurationProperties.getServerGroups();
        isUpdated = true;
        break;
      }
      fetchTime += fetchIncrementTime;
    }
    fetchTime = originalFetchTime;
    if (!isUpdated) {
      logger.error("ServerGroup refresh failed.");
      throw new DhruvaRuntimeException("ServerGroup Refresh Failed.");
    }
  }

  protected class RefreshHandle implements Runnable {
    @Override
    public void run() {
      getUpdatedMaps();
      disposeExistingFlux();
      opFlux.clear();
      startMonitoring(commonConfigurationProperties.getServerGroups());
    }
  }

  private Predicate<ServerGroupElement> upFilter =
      sge -> {
        Status status =
            elementStatus.putIfAbsent(sge.toUniqueElementString(), new Status(true, 0L));
        if (status == null) logger.debug("Marking SGE {} as UP initially", sge);
        return status == null || status.isUp();
      };

  private Predicate<ServerGroupElement> isExpiredStatus(long validity) {
    return sge -> {
      Status status = elementStatus.get(sge.toUniqueElementString());
      if (status == null) return true;
      boolean expired = status.updateIfExpired(validity);
      logger.debug("Status expired(={}) for {}", expired, sge.toUniqueElementString());
      return expired;
    };
  }
}
