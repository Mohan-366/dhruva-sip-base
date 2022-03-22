package com.cisco.dsb.trunk.trunks;

import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.loadbalancer.LBType;
import com.cisco.dsb.common.loadbalancer.LoadBalancable;
import com.cisco.dsb.common.loadbalancer.LoadBalancer;
import com.cisco.dsb.common.metric.SipMetricsContext;
import com.cisco.dsb.common.servergroup.DnsServerGroupUtil;
import com.cisco.dsb.common.servergroup.SGType;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.cisco.dsb.connectivity.monitor.service.OptionsPingController;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.util.RedirectionSet;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.header.ContactList;
import java.time.Duration;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import lombok.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/** Abstract class for all kinds of trunks */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@CustomLog
public abstract class AbstractTrunk implements LoadBalancable {
  protected String name;
  protected Ingress ingress;
  protected Egress egress;
  private DnsServerGroupUtil dnsServerGroupUtil;
  private OptionsPingController optionsPingController;
  private static MetricService metricService =
      SpringApplicationContext.getAppContext() == null
          ? null
          : SpringApplicationContext.getAppContext().getBean(MetricService.class);
  private static int MAX_ELEMENT_RETRIES = 100;

  public AbstractTrunk(AbstractTrunk abstractTrunk) {
    this.name = abstractTrunk.name;
    this.ingress = abstractTrunk.ingress;
    this.egress = abstractTrunk.egress;
    this.dnsServerGroupUtil = abstractTrunk.dnsServerGroupUtil;
    this.optionsPingController = abstractTrunk.optionsPingController;
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

  public abstract ProxySIPRequest processIngress(ProxySIPRequest proxySIPRequest);

  public abstract Mono<ProxySIPResponse> processEgress(ProxySIPRequest proxySIPRequest);

  protected abstract void doPostRouteNorm(TrunkCookie cookie);

  protected abstract boolean enableRedirection();

  protected Mono<ProxySIPResponse> sendToProxy(ProxySIPRequest proxySIPRequest) {
    TrunkCookie cookie = new TrunkCookie(this, proxySIPRequest);
    String userId = null;
    if (((SipUri) proxySIPRequest.getRequest().getRequestURI())
        .hasParameter(SipParamConstants.TEST_CALL)) userId = SipParamConstants.INJECTED_DNS_UUID;
    String finalUserId = userId;
    return Mono.defer(() -> getEndPoint(cookie, finalUserId))
        .doOnNext(endPoint -> doPostRouteNorm(cookie))
        .flatMap(
            endPoint ->
                Mono.defer(() -> Mono.fromFuture(cookie.getClonedRequest().proxy(endPoint))))
        .<ProxySIPResponse>handle(
            (proxySIPResponse, sink) -> {
              // process Response, based on that send error signal to induce retry
              // if best response received or no more elements to try, onNext(bestResponse)
              logger.debug("Received Response {}", proxySIPResponse.getStatusCode());

              if (shouldFailover(proxySIPResponse, cookie)) {
                logger.info(
                    "Received responseCode({}) is part of failOverCode, trying next element",
                    proxySIPResponse.getStatusCode());
                // Measure latency
                proxySIPRequest.handleProxyEvent(
                    metricService, SipMetricsContext.State.proxyNewRequestRetryNextElement);
                sink.error(
                    new DhruvaRuntimeException(ErrorCode.TRUNK_RETRY_NEXT, "Trying next element"));
              } else if (proxySIPResponse.getResponseClass() == 3 && enableRedirection()) {
                ContactList redirectionList = proxySIPResponse.getResponse().getContactHeaders();
                cookie.getRedirectionSet().add(redirectionList);
                logger.info("Following redirection");
                proxySIPRequest.handleProxyEvent(
                    metricService, SipMetricsContext.State.proxyNewRequestRetryNextElement);
                sink.error(
                    new DhruvaRuntimeException(
                        ErrorCode.TRUNK_RETRY_NEXT, "Following redirection"));
              } else {
                sink.next(cookie.getBestResponse());
              }
            })
        // retry only it matches TRUNK_RETRY ERROR CODE
        .retryWhen(this.retryTrunkOnException)
        .onErrorResume(
            err ->
                Mono.defer(
                    () -> {
                      ProxySIPResponse bestResponse = cookie.getBestResponse();
                      if (bestResponse == null) {
                        if (err instanceof DhruvaRuntimeException) {
                          return Mono.error(err);
                        } else {
                          return Mono.error(
                              new DhruvaRuntimeException(
                                  ErrorCode.APP_REQ_PROC, "no best response found", err));
                        }
                      }
                      return Mono.just(bestResponse);
                    }))
        .timeout(Duration.ofSeconds(egress.getOverallResponseTimeout()))
        .onErrorResume(
            TimeoutException.class,
            err -> {
              logger.error(
                  "Received overall timeout Exception, timeout:{}",
                  egress.getOverallResponseTimeout());
              ProxySIPResponse bestResponse = cookie.getBestResponse();
              if (bestResponse == null) {
                return Mono.empty();
              }
              return Mono.just(bestResponse);
            });
  }

  private boolean shouldFailover(ProxySIPResponse proxySIPResponse, TrunkCookie cookie) {
    // anything other than sgPolicy failoverCodes is considered as best response
    int currentRespCode = proxySIPResponse.getStatusCode();
    boolean failOver =
        ((ServerGroup) cookie.getSgLoadBalancer().getCurrentElement())
            .getSgPolicy().getFailoverResponseCodes().stream()
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

  private Mono<EndPoint> getEndPoint(TrunkCookie cookie, String userId) {
    LoadBalancer sgLB = cookie.getSgLoadBalancer();
    ServerGroup serverGroup = (ServerGroup) sgLB.getCurrentElement();
    ServerGroupElement redirectSGE = null;
    cookie.setClonedRequest(((ProxySIPRequest) cookie.originalRequest.clone()));
    // try all the contacts present in redirection set
    if (enableRedirection()) {
      if ((cookie.redirectionLB == null
              || (redirectSGE = (ServerGroupElement) cookie.redirectionLB.getNextElement()) == null)
          && cookie.redirectionSet.first() != null) {
        // create new SG from first contact header present in redirection set
        Contact contact = cookie.redirectionSet.pollFirst();
        return dnsServerGroupUtil
            .createDNSServerGroup(getSGFromContact(serverGroup, contact), userId)
            .map(
                rsg -> {
                  cookie.redirectionLB = LoadBalancer.of(rsg);
                  cookie.redirectionSG = rsg;
                  return getEndPointFromSge(
                      rsg, (ServerGroupElement) cookie.redirectionLB.getCurrentElement());
                });

      }
      // send the Endpoint from the present RSG, till all the elements are tried out
      else if (redirectSGE != null)
        return Mono.just(getEndPointFromSge(cookie.redirectionSG, redirectSGE));

      // clear redirectionSG and redirectionLB
      // NOTE: not removing redirectionSet as it's single copy per request
      else {
        cookie.setRedirectionLB(null);
        cookie.setRedirectionSG(null);
      }
    }

    LoadBalancer sgeLB = cookie.getSgeLoadBalancer();
    ServerGroupElement serverGroupElement = null;
    // dynamic sg from SRV/A_Record, create first time or when no SGE from current SG and there is
    // next SG
    if ((sgeLB) == null
        || ((serverGroupElement = ((ServerGroupElement) sgeLB.getNextElement())) == null
            && (serverGroup = (ServerGroup) sgLB.getNextElement()) != null)) {
      if (serverGroup.getSgType() == SGType.STATIC) {

        while (serverGroup != null
            && optionsPingController != null
            && !optionsPingController.getStatus(serverGroup)) {
          logger.error("serverGroup {} is DOWN, trying next serverGroup", serverGroup.getName());
          serverGroup = (ServerGroup) cookie.getSgLoadBalancer().getNextElement();
        }

        if (serverGroup == null)
          return Mono.error(
              new DhruvaRuntimeException(ErrorCode.FETCH_ENDPOINT_ERROR, "None of the SGs are up"));

        // TODO: modify logic when taking up DNS serverGroup optionsPing
        if (serverGroup.getSgType() == SGType.STATIC) {

          sgeLB = LoadBalancer.of(serverGroup);
          serverGroupElement = (ServerGroupElement) sgeLB.getCurrentElement();

          while (serverGroupElement != null
              && optionsPingController != null
              && !optionsPingController.getStatus(serverGroupElement)) {
            logger.error(
                "serverGroupElement {} is DOWN, trying next serverGroupelement",
                serverGroupElement);

            serverGroupElement = (ServerGroupElement) sgeLB.getNextElement();
          }
          if (serverGroupElement == null)
            return Mono.error(
                new DhruvaRuntimeException(
                    ErrorCode.FETCH_ENDPOINT_ERROR, "None of the SGEs are up "));

          cookie.setSgeLoadBalancer(sgeLB);
          return Mono.just(
              getEndPointFromSge(serverGroup, (ServerGroupElement) sgeLB.getCurrentElement()));
        }
      }
      logger.debug("Querying DNS for SG:{}", serverGroup);
      return dnsServerGroupUtil
          .createDNSServerGroup(serverGroup, userId) // this can throw Mono.error(DNS)
          .onErrorMap(
              err -> {
                // retry only when next SG is there
                logger.warn(
                    "Exception while querying DNS for SG {}, trying next SG",
                    sgLB.getCurrentElement());
                if (sgLB.getNextElement() != null) {
                  return new DhruvaRuntimeException(
                      ErrorCode.TRUNK_RETRY_NEXT, "DNS Exception", err);
                }
                logger.warn("No more SG left!");
                return new DhruvaRuntimeException(
                    ErrorCode.TRUNK_NO_RETRY, " DNS Exception, no more SG left", err);
              })
          .map(
              dsg -> {
                LoadBalancer sgeLBTemp = LoadBalancer.of(dsg);
                cookie.setSgeLoadBalancer(sgeLBTemp);
                return getEndPointFromSge(dsg, (ServerGroupElement) sgeLBTemp.getCurrentElement());
              });
    }
    // sge already exists, use it to return next element

    if (serverGroupElement != null) {
      while (serverGroupElement != null
          && optionsPingController != null
          && !optionsPingController.getStatus(serverGroupElement)) {
        serverGroupElement = (ServerGroupElement) sgeLB.getNextElement();
      }
      return Mono.just(getEndPointFromSge(serverGroup, serverGroupElement));
    }

    // no more sg
    logger.warn("No more endpoints to try!!!");
    return Mono.error(
        new DhruvaRuntimeException(ErrorCode.TRUNK_NO_RETRY, "No more endpoints to try"));
  }

  private EndPoint getEndPointFromSge(
      ServerGroup serverGroup, ServerGroupElement serverGroupElement) {
    EndPoint ep = new EndPoint(serverGroup, serverGroupElement);
    logger.info("Trying EndPoint {}", ep);
    return ep;
  }

  private ServerGroup getSGFromContact(ServerGroup serverGroup, Contact contact) {
    int port = ((AddressImpl) contact.getAddress()).getPort();
    port = (port == -1) ? 0 : port;
    Transport transport;
    if (contact.getParameter("transport") != null) {
      try {
        transport = Transport.valueOf(contact.getParameter("transport").toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        logger.debug("Invalid transport type in contact header", ex);
        transport = serverGroup.getTransport();
      }

    } else transport = serverGroup.getTransport();
    return serverGroup
        .toBuilder()
        .setHostName(((AddressImpl) contact.getAddress()).getHost())
        .setPort(port)
        .setTransport(transport)
        .build();
  }

  /*
   We want to trigger retry error only on certain error code
  */
  private final Retry retryTrunkOnException =
      Retry.max(MAX_ELEMENT_RETRIES)
          .doBeforeRetry((s) -> logger.debug("Retrying after retryTrunkOnException", s.failure()))
          .filter(
              (err) ->
                  err instanceof DhruvaRuntimeException
                      && ((DhruvaRuntimeException) err)
                          .getErrCode()
                          .equals(ErrorCode.TRUNK_RETRY_NEXT));

  @Override
  public String toString() {
    return String.format("(name=%s; ingress=%s; egress=%s)", name, ingress, egress);
  }

  @Getter
  @Setter
  protected class TrunkCookie {
    ProxySIPRequest clonedRequest;
    ProxySIPResponse bestResponse;
    LoadBalancer sgeLoadBalancer;
    LoadBalancer sgLoadBalancer;
    LoadBalancer redirectionLB;
    ServerGroup redirectionSG;
    final ProxySIPRequest originalRequest;
    final AbstractTrunk abstractTrunk;
    final RedirectionSet redirectionSet;

    private TrunkCookie(AbstractTrunk abstractTrunk, ProxySIPRequest originalRequest) {
      this.originalRequest = originalRequest;
      this.abstractTrunk = abstractTrunk;
      this.redirectionSet = new RedirectionSet();
      init();
    }

    private void init() {
      this.sgLoadBalancer = LoadBalancer.of(abstractTrunk);
    }
  }
}