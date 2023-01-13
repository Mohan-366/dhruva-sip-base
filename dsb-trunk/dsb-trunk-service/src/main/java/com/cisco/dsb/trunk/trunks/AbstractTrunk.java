package com.cisco.dsb.trunk.trunks;

import com.cisco.dsb.common.circuitbreaker.ConditionalTransformer;
import com.cisco.dsb.common.circuitbreaker.DsbCircuitBreaker;
import com.cisco.dsb.common.circuitbreaker.DsbCircuitBreakerUtil;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.loadbalancer.LBElement;
import com.cisco.dsb.common.loadbalancer.LBType;
import com.cisco.dsb.common.loadbalancer.LoadBalancable;
import com.cisco.dsb.common.loadbalancer.LoadBalancer;
import com.cisco.dsb.common.maintanence.Maintenance;
import com.cisco.dsb.common.metric.SipMetricsContext;
import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.common.servergroup.*;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.cisco.dsb.connectivity.monitor.service.OptionsPingController;
import com.cisco.dsb.proxy.ProxyState;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.MaintenanceMode;
import com.cisco.dsb.trunk.MaintenanceModeImpl;
import com.cisco.dsb.trunk.TrunkConfigurationProperties;
import com.cisco.dsb.trunk.util.RedirectionSet;
import com.cisco.dsb.trunk.util.SipParamConstants;
import com.cisco.wx2.util.Utilities;
import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.header.ContactList;
import java.text.ParseException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import reactor.util.retry.Retry;

/** Abstract class for all kinds of trunks */
@Getter
@Setter
@NoArgsConstructor
@CustomLog
public abstract class AbstractTrunk implements LoadBalancable {
  protected String name;
  protected Ingress ingress;
  protected Egress egress;
  private DnsServerGroupUtil dnsServerGroupUtil;
  private OptionsPingController optionsPingController;
  private ConcurrentHashMap<String, Long> loadBalancerMetric;
  private static int MAX_ELEMENT_RETRIES = 100;
  @Getter private boolean enableCircuitBreaker = false;
  @Setter private DsbCircuitBreaker dsbCircuitBreaker;

  public AbstractTrunk(AbstractTrunk abstractTrunk) {
    this.name = abstractTrunk.name;
    this.ingress = abstractTrunk.ingress;
    this.egress = abstractTrunk.egress;
    this.dnsServerGroupUtil = abstractTrunk.dnsServerGroupUtil;
    this.optionsPingController = abstractTrunk.optionsPingController;
    this.loadBalancerMetric = abstractTrunk.loadBalancerMetric;
    this.enableCircuitBreaker = abstractTrunk.enableCircuitBreaker;
    this.dsbCircuitBreaker = abstractTrunk.dsbCircuitBreaker;
  }

  public AbstractTrunk(String name, Ingress ingress, Egress egress, boolean enableCircuitBreaker) {
    this.name = name;
    this.ingress = ingress;
    this.egress = egress;
    this.enableCircuitBreaker = enableCircuitBreaker;
  }

  static MetricService getMetricService() {
    return SpringApplicationContext.getAppContext() == null
        ? null
        : SpringApplicationContext.getAppContext().getBean(MetricService.class);
  }

  @Override
  public boolean equals(Object a) {
    if (this == a) return true;
    if (a instanceof AbstractTrunk) {
      AbstractTrunk b = (AbstractTrunk) a;
      return new EqualsBuilder()
          .append(this.name, b.name)
          .append(this.ingress, b.ingress)
          .append(this.egress, b.egress)
          .isEquals();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(name).append(ingress).append(egress).toHashCode();
  }

  @Override
  public Collection<ServerGroup> getElements() {
    return egress.getServerGroupMap().values();
  }

  @Override
  public LBType getLbType() {
    return egress.getLbType();
  }

  public BiFunction<Maintenance, TrunkConfigurationProperties, MaintenanceMode>
      getMaintenanceMode() {
    return (maintenance, configurationProperties) ->
        MaintenanceModeImpl.getInstance(maintenance, configurationProperties, this);
  }

  public abstract ProxySIPRequest processIngress(
      ProxySIPRequest proxySIPRequest,
      Normalization normalization,
      Maintenance maintenance,
      TrunkConfigurationProperties configurationProperties);

  public abstract Mono<ProxySIPResponse> processEgress(
      ProxySIPRequest proxySIPRequest, Normalization normalization);

  protected abstract boolean enableRedirection();

  /**
   * Handles the proxySIPResponse received from the endpoint Based on the response code , checks if
   * it matches failOver code. If so , next element will be tried In case of 3xx, updates the cookie
   * for redirection handling Finally sends the best response stored in the cookie for this request
   *
   * @param cookie stateful info stored for a given sip request
   * @return BiConsumer function accepting ProxySIPResponse and reactor sink.
   */
  private BiConsumer<ProxySIPResponse, SynchronousSink<ProxySIPResponse>> handleProxyResponse(
      TrunkCookie cookie) {
    return (proxySIPResponse, sink) -> {
      // process Response, based on that send error signal to induce retry
      // if best response received or no more elements to try, onNext(bestResponse)
      logger.debug("Received Response {}", proxySIPResponse.getStatusCode());
      ProxySIPRequest proxySIPRequest = cookie.getOriginalRequest();

      if (shouldFailover(proxySIPResponse, cookie)) {
        logger.info(
            "Received responseCode({}) is part of failOverCode, trying next element",
            proxySIPResponse.getStatusCode());
        // Measure latency
        proxySIPRequest.handleProxyEvent(
            getMetricService(), SipMetricsContext.State.proxyNewRequestRetryNextElement);
        sink.error(new DhruvaRuntimeException(ErrorCode.TRUNK_RETRY_NEXT, "Trying next element"));

      } else if (proxySIPResponse.getResponseClass() == 3 && enableRedirection()) {
        ContactList redirectionList = proxySIPResponse.getResponse().getContactHeaders();
        cookie.getRedirectionSet().add(redirectionList);
        logger.info("Following redirection");
        proxySIPRequest.handleProxyEvent(
            getMetricService(), SipMetricsContext.State.proxyNewRequestRetryNextElement);
        sink.error(new DhruvaRuntimeException(ErrorCode.TRUNK_RETRY_NEXT, "Following redirection"));
      } else {
        sink.next(cookie.getBestResponse());
      }
    };
  }

  private Function<Throwable, Mono<ProxySIPResponse>> sendBestResponseOnException(
      TrunkCookie cookie) {
    return err ->
        Mono.defer(
            () -> {
              ProxySIPResponse bestResponse = cookie.getBestResponse();
              if (bestResponse == null) {
                try {
                  DhruvaRuntimeException bestException = cookie.getBestException();
                  return Mono.just(
                      cookie.originalRequest.createResponse(
                          bestException.getErrCode().getResponseCode(),
                          bestException.getMessage()));
                } catch (DhruvaException | ParseException e) {
                  logger.error("Unable to create Error Response", e);
                  return Mono.empty();
                }
              }
              return Mono.just(bestResponse);
            });
  }

  /**
   * Fetches the endpoint or downstream element for this sip request Handles mid-call requests
   * separately and is not part of pipeline Applies post normalization rules Finally sends the
   * request to the endpoint derived Handles the response received in aysnc fashion
   *
   * @param proxySIPRequest sip request that needs to be sent out
   * @param normalization normalization rules to applied for this trunk
   * @return ProxySipResponse or Mono.error
   */
  protected Mono<ProxySIPResponse> sendToProxy(
      ProxySIPRequest proxySIPRequest, Normalization normalization) {
    // mid call requests must be simply be sent to proxy by-passing LB logic
    if (proxySIPRequest.isMidCall()) {
      return sendMidCallRequestToProxy(proxySIPRequest, normalization);
    }

    String userId = null;
    if (((SipUri) proxySIPRequest.getRequest().getRequestURI())
        .hasParameter(SipParamConstants.TEST_CALL)) userId = SipParamConstants.INJECTED_DNS_UUID;

    TrunkCookie cookie = new TrunkCookie(this, proxySIPRequest, userId);

    return Mono.defer(() -> getEndPoint(cookie))
        .doOnNext(endPoint -> normalization.egressPostNormalize().accept(cookie, endPoint))
        .flatMap(endPoint -> sendToProxy(cookie, endPoint))
        .timeout(Duration.ofSeconds(egress.getOverallResponseTimeout()))
        .<ProxySIPResponse>handle(
            (proxySIPResponse, sink) -> handleProxyResponse(cookie).accept(proxySIPResponse, sink))
        .transform(proxySIPResponseMono -> handleProxyException(proxySIPResponseMono, cookie));
  }

  /**
   * Checks if error maps to circuit breaker exception. If so , will set error code to
   * TRUNK_RETRY_NEXT invokes retryWhen to again go over the pipeline.Checks if error code matches
   * TRUNK_RETRY_NEXT If there is a match, invokes the pipeline again, this time a new endpoint or
   * next endpoint will be tried
   *
   * @param proxySIPResponseMono sip response received for a given request
   * @param cookie stateful info stored for a given sip request
   * @return ProxySipResponse or Mono.error
   */
  private Mono<ProxySIPResponse> handleProxyException(
      Mono<ProxySIPResponse> proxySIPResponseMono, TrunkCookie cookie) {
    return proxySIPResponseMono
        .doOnError(this.recordException(cookie))
        .retryWhen(this.retryHandler())
        .onErrorResume(sendBestResponseOnException(cookie));
  }

  /**
   * Each Trunk can override this class to implement their own retry filter based on ErrorCodes.
   *
   * @return
   */
  private Retry retryHandler() {
    return Retry.max(MAX_ELEMENT_RETRIES)
        .doBeforeRetry((s) -> logger.debug("Retrying after retryTrunkOnException", s.failure()))
        .filter(
            err -> {
              if (err instanceof DhruvaRuntimeException
                  && ((DhruvaRuntimeException) err).getErrCode() == ErrorCode.TRUNK_RETRY_NEXT)
                return true;
              else if (DsbCircuitBreakerUtil.isCircuitBreakerException(err)) return true;
              return failOverException().test(err);
            });
  }

  protected Predicate<Throwable> failOverException() {
    return err -> {
      if (err instanceof DhruvaRuntimeException) {
        switch (((DhruvaRuntimeException) err).getErrCode()) {
          case REQUEST_TIME_OUT:
          case DESTINATION_UNREACHABLE:
            return true;
        }
      }
      return false;
    };
  }

  private Consumer<Throwable> recordException(TrunkCookie cookie) {
    return err -> {
      DhruvaRuntimeException dre;
      if (err instanceof DhruvaRuntimeException) {
        dre = ((DhruvaRuntimeException) err);
        // We should not record trunk_retry_next as it does not map to any response. To exit the
        // retry loop an exception
        // apart from trunk_retry_next will be thrown and we need to record only such exception to
        // generate the best response.
        if (dre.getErrCode() == ErrorCode.TRUNK_RETRY_NEXT) return;
      } else if (err instanceof TimeoutException)
        dre =
            new DhruvaRuntimeException(ErrorCode.REQUEST_TIME_OUT, "Overall pipeline timeout", err);
      else if (DsbCircuitBreakerUtil.isCircuitBreakerException(err))
        dre = new DhruvaRuntimeException(ErrorCode.CB_OPEN, err.getMessage(), err);
      else dre = new DhruvaRuntimeException(err);
      if (cookie.getBestException() == null) {
        cookie.setBestException(dre);
        return;
      }
      // record best exception based on errCode
      if (dre.getErrCode().getResponseCode()
          < cookie.getBestException().getErrCode().getResponseCode()) {
        cookie.setBestException(dre);
      }
    };
  }

  private boolean shouldFailover(ProxySIPResponse proxySIPResponse, TrunkCookie cookie) {
    // anything other than serverGroup RoutePolicy failoverCodes is considered as best response
    int currentRespCode = proxySIPResponse.getStatusCode();
    boolean failOver =
        ((ServerGroup) cookie.getSgLoadBalancer().getCurrentElement())
            .getRoutePolicy().getFailoverResponseCodes().stream()
                .anyMatch((failOverRespCode) -> failOverRespCode.equals(currentRespCode));

    // update the best response for non-3xx
    if (!(this.enableRedirection() && proxySIPResponse.getResponseClass() == 3)) {
      ProxySIPResponse bestResponse = cookie.getBestResponse();
      if (bestResponse == null || bestResponse.getStatusCode() > currentRespCode) {
        cookie.setBestResponse(proxySIPResponse);
      }
    }

    return failOver;
  }

  private boolean checkFailOverSG(TrunkCookie cookie) {
    boolean failOver;
    if (cookie.getBestResponse() == null) return true;

    failOver =
        this.getEgress()
            .getRoutePolicy()
            .getFailoverResponseCodes()
            .contains(cookie.getBestResponse().getStatusCode());
    if (failOver) {
      logger.info("Trunk Retry matches with best response, trying next SG");
      return true;
    }
    logger.info(
        "Trunk Retry does not match  best response {}, not trying any more serverGroups",
        cookie.getBestResponse());

    return false;
  }

  /**
   * clones the original request.The cloned request will be sent via network Handles static, dynamic
   * Server Group and redirection. Checks the options ping status for the remote element before it
   * decides to pick one. Operates on load balancer set in cookie which is stateful and is used for
   * all retries to subsequent elements Supports load balancing across elements with server group
   * and across server groups as well. Uses dnsServerGroupUtil to setup the pipeline for async DNS
   * resolutions
   *
   * @param cookie stateful info stored for a given sip request(lb, original request, redirection
   *     set)
   * @return Endpoint object having IP:PORT of remote element where request needs to be sent
   */
  private Mono<EndPoint> getEndPoint(TrunkCookie cookie) {
    // try all the contacts present in redirection set
    cookie.setClonedRequest(((ProxySIPRequest) cookie.getOriginalRequest().clone()));
    return getEndpointRedirectionSet(cookie)
        .switchIfEmpty(
            Mono.defer(
                () ->
                    getEndpointSG(
                        cookie))); // function should be executed if Redirection Set is empty.
  }

  private Mono<EndPoint> getEndpointRedirectionSet(TrunkCookie cookie) {
    // 1. Fetch resolved SG from cookie. If not present, create SG for the top Contact, resolve it
    // and store in cookie
    // 2. Start options for resolved SG
    // 3. Get Status for SG. Create LB for SG, store in Cookie. If up, iterate through SGE to get
    // active endpoint

    // We have to resolve when we run out of active resolved endpoint
    if (cookie.rsgLoadBalancer != null) {
      ServerGroupElement activeSGE =
          ((ServerGroupElement) getActiveLBElement(cookie.rsgLoadBalancer));
      if (activeSGE != null) return Mono.just(getEndPointFromSge(cookie.redirectionSG, activeSGE));
    }
    cookie.rsgLoadBalancer = null;
    cookie.redirectionSG = null;
    // resolve next SG if present in redirection set
    if (cookie.redirectionSet.isEmpty()) return Mono.empty();

    return getSGFromContact(cookie.redirectionSet.pollFirst(), cookie)
        .map(
            resolvedSG -> {
              cookie
                  .getClonedRequest()
                  .getAppRecord()
                  .add(ProxyState.OUT_PROXY_DNS_RESOLUTION, null);
              if (resolvedSG.isEnableRedirectionOptions()) {
                optionsPingController.startPing(resolvedSG);
              }
              if (!isActive(resolvedSG)) {
                throw new DhruvaRuntimeException(
                    ErrorCode.TRUNK_RETRY_NEXT, "Redirection SG is down");
              }
              cookie.rsgLoadBalancer = LoadBalancer.of(resolvedSG);
              cookie.redirectionSG = resolvedSG;
              ServerGroupElement activeSGE =
                  (ServerGroupElement) getActiveLBElement(cookie.rsgLoadBalancer);
              if (activeSGE == null)
                throw new DhruvaRuntimeException(
                    ErrorCode.TRUNK_RETRY_NEXT, "None of the SGEs are up ");
              return getEndPointFromSge(resolvedSG, activeSGE);
            });
  }

  private Mono<EndPoint> getEndpointSG(TrunkCookie cookie) {
    ServerGroup currentSG = ((ServerGroup) cookie.sgLoadBalancer.getCurrentElement());
    while (true) {
      ServerGroupElement activeSGE =
          (ServerGroupElement) getActiveLBElement(cookie.sgeLoadBalancer);
      if (activeSGE != null) {
        return Mono.just(getEndPointFromSge(currentSG, activeSGE));
      }
      currentSG =
          checkFailOverSG(cookie) ? (ServerGroup) getActiveLBElement(cookie.sgLoadBalancer) : null;
      if (currentSG == null) {
        logger.info("No More Active SG to try");
        return Mono.error(
            new DhruvaRuntimeException(ErrorCode.FETCH_ENDPOINT_ERROR, "No more SG left"));
      }
      if (currentSG.getSgType() != SGType.STATIC) {
        break;
      }
      cookie.sgeLoadBalancer = LoadBalancer.of(currentSG);
    }
    logger.debug("Querying DNS for SG:{}", currentSG);
    return dnsServerGroupUtil
        .createDNSServerGroup(currentSG, cookie.userId)
        // this can throw Mono.error(DNS)
        .onErrorMap(
            err -> {
              // retry only when next SG is there
              Utilities.Checks checks = new Utilities.Checks();
              checks.add("dns resolution", err.getMessage());
              cookie
                  .getClonedRequest()
                  .getAppRecord()
                  .add(ProxyState.OUT_PROXY_DNS_RESOLUTION, checks);
              logger.warn(
                  "Exception while querying DNS for SG {}, trying next SG",
                  cookie.sgLoadBalancer.getCurrentElement());
              if (cookie.sgLoadBalancer.isEmpty()) {
                logger.warn("No more SG left!");
                return new DhruvaRuntimeException(
                    ErrorCode.TRUNK_NO_RETRY, " DNS Exception, no more SG left", err);
              }
              return new DhruvaRuntimeException(ErrorCode.TRUNK_RETRY_NEXT, "DNS Exception", err);
            })
        .map(
            dsg -> {
              cookie
                  .getClonedRequest()
                  .getAppRecord()
                  .add(ProxyState.OUT_PROXY_DNS_RESOLUTION, null);
              LoadBalancer sgeLBTemp = LoadBalancer.of(dsg);
              ServerGroupElement activeSge = (ServerGroupElement) getActiveLBElement(sgeLBTemp);
              if (activeSge == null)
                throw new DhruvaRuntimeException(
                    ErrorCode.TRUNK_RETRY_NEXT, "None of the SGEs are up ");
              cookie.setSgeLoadBalancer(sgeLBTemp);
              return getEndPointFromSge(dsg, activeSge);
            });
  }

  private EndPoint getEndPointFromSge(
      ServerGroup serverGroup, ServerGroupElement serverGroupElement) {

    EndPoint ep = new EndPoint(serverGroup, serverGroupElement);

    // increment counter of SGE of this trunk
    this.addLBMetric(serverGroup);
    this.updateLBMetric(serverGroupElement, serverGroup.getLbType().name());
    logger.info("Trying EndPoint {}", ep);
    return ep;
  }

  private void addLBMetric(ServerGroup sg) {
    if (sg == null) return;
    if (this.getLoadBalancerMetric() == null) return;

    if (this.getLoadBalancerMetric().isEmpty()) {
      sg.getElements()
          .forEach(
              serverGroupElement -> {
                if (serverGroupElement.getIpAddress() == null
                    || serverGroupElement.getTransport() == null) return;
                String key =
                    serverGroupElement.getIpAddress()
                        + ":"
                        + serverGroupElement.getPort()
                        + ":"
                        + serverGroupElement.getTransport();
                this.getLoadBalancerMetric().put(key, 0L);
              });
    }
  }

  private void updateLBMetric(ServerGroupElement serverGroupElement, String lbType) {

    if (serverGroupElement == null) return;
    if (this.getLoadBalancerMetric() == null) return;
    if (getMetricService() == null) return;

    String elementKey;
    if (serverGroupElement.getUniqueString() == null) {
      elementKey =
          serverGroupElement.getIpAddress()
              + ":"
              + serverGroupElement.getPort()
              + ":"
              + serverGroupElement.getTransport();
    } else {
      elementKey = serverGroupElement.getUniqueString();
    }
    long count =
        this.getLoadBalancerMetric().containsKey(elementKey)
            ? this.getLoadBalancerMetric().get(elementKey)
            : 0;
    this.getLoadBalancerMetric().put(elementKey, count + 1);
    getMetricService().getTrunkLBMap().put(this.name, this.getLoadBalancerMetric());
    getMetricService().getTrunkLBAlgorithm().put(this.name, lbType);
  }

  @Override
  public String toString() {
    return String.format("(name=%s; ingress=%s; egress=%s)", name, ingress, egress);
  }

  @Getter
  @Setter
  public static class TrunkCookie {
    ProxySIPRequest clonedRequest;
    ProxySIPResponse bestResponse;
    DhruvaRuntimeException bestException;
    LoadBalancer sgeLoadBalancer;
    LoadBalancer sgLoadBalancer;
    LoadBalancer rsgLoadBalancer; // Resolved SG from 3xx
    ServerGroup redirectionSG;
    final ProxySIPRequest originalRequest;
    final RedirectionSet redirectionSet;
    final String userId;

    private TrunkCookie(
        AbstractTrunk abstractTrunk, ProxySIPRequest originalRequest, String userId) {
      this.originalRequest = originalRequest;
      this.redirectionSet = new RedirectionSet();
      this.userId = userId;
      init(abstractTrunk);
    }

    private void init(AbstractTrunk abstractTrunk) {
      this.sgLoadBalancer = LoadBalancer.of(abstractTrunk);
    }
  }

  /*
   * returns the predicate required in CircuitBreaker to distinguish
   * success and failure responses
   */
  private Predicate<Object> getCircuitBreakerRecordResult(TrunkCookie cookie) {
    return object ->
        ((ServerGroup) cookie.getSgLoadBalancer().getCurrentElement())
            .getRoutePolicy()
            .getFailoverResponseCodes()
            .contains(((ProxySIPResponse) object).getStatusCode());
  }

  private Mono<ProxySIPResponse> sendToProxy(TrunkCookie cookie, EndPoint endPoint) {
    Predicate<Object> cbRecordResult = getCircuitBreakerRecordResult(cookie);
    return Mono.defer(() -> Mono.fromFuture(cookie.getClonedRequest().proxy(endPoint)))
        .transformDeferred(
            ConditionalTransformer.of(
                dsbCircuitBreaker,
                endPoint,
                cbRecordResult,
                failOverException(),
                getEgress().getRoutePolicy().getCircuitBreakConfig()));
  }

  private Mono<ProxySIPResponse> sendMidCallRequestToProxy(
      ProxySIPRequest proxySIPRequest, Normalization normalization) {
    normalization.egressMidCallPostNormalize().accept(proxySIPRequest);
    logger.debug("Sending midDialog request to proxy");
    return Mono.fromFuture(proxySIPRequest.getProxyInterface().proxyRequest(proxySIPRequest));
  }

  private boolean isActive(Pingable object) {
    if (object == null) return false;
    return optionsPingController != null ? optionsPingController.getStatus(object) : true;
  }

  private Mono<ServerGroup> getSGFromContact(Contact contact, TrunkCookie cookie) {
    ServerGroup serverGroup = ((ServerGroup) cookie.getSgLoadBalancer().getCurrentElement());
    AddressImpl address = (AddressImpl) contact.getAddress();
    ServerGroup rsg =
        serverGroup
            .toBuilder()
            .setName(serverGroup.getName() + "_contact")
            .setHostName(address.getHost())
            .setPort(address.getPort())
            .build();
    logger.debug("Querying DNS for SG {}", rsg);
    return dnsServerGroupUtil
        .createDNSServerGroup(rsg, cookie.userId)
        .onErrorMap(
            err -> {
              Utilities.Checks checks = new Utilities.Checks();
              checks.add("dns resolution", err.getMessage());
              cookie
                  .getClonedRequest()
                  .getAppRecord()
                  .add(ProxyState.OUT_PROXY_DNS_RESOLUTION, checks);
              logger.warn(
                  "Exception while querying DNS for Contact {}",
                  ((AddressImpl) contact.getAddress()).getHost());
              return new DhruvaRuntimeException(ErrorCode.TRUNK_RETRY_NEXT, "DNS Exception", err);
            });
  }

  private LBElement getActiveLBElement(LoadBalancer loadBalancer) {
    if (loadBalancer == null) return null;

    while (loadBalancer.getNextElement() != null) {
      if (isActive((Pingable) loadBalancer.getCurrentElement()))
        return loadBalancer.getCurrentElement();
      logger.debug(
          "Skipping inActive Element as it's down {}", loadBalancer.getCurrentElement().toString());
    }
    return null;
  }
}
