package com.cisco.dsb.proxy.messaging;

import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.messaging.models.AbstractSipRequest;
import com.cisco.dsb.common.metric.SipMetricsContext;
import com.cisco.dsb.common.record.DhruvaAppRecord;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.sip.util.SipUtils;
import com.cisco.dsb.proxy.sip.*;
import gov.nist.javax.sip.message.SIPMessage;
import java.text.ParseException;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.ReasonHeader;
import javax.sip.header.RouteHeader;
import javax.sip.message.Request;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;

@CustomLog
public class ProxySIPRequest extends AbstractSipRequest implements Cloneable {
  @Getter @Setter private ProxyStatelessTransaction proxyStatelessTransaction;
  @Getter @Setter private ProxyInterface proxyInterface;
  @Getter @Setter private String network;
  @Getter @Setter private String outgoingNetwork = null;
  @Getter private final ProxyCookie cookie;
  @Getter @Setter private ProxyParamsInterface params;
  @Getter @Setter private boolean statefulClientTransaction;
  @Getter @Setter private ProxyClientTransaction proxyClientTransaction;
  @Getter @Setter private URI lrFixUri = null;
  @Getter @Setter private URI m_routeTo = null;
  @Getter @Setter private boolean m_escaped = false;
  @Getter @Setter private EndPoint downstreamElement;
  @Getter HashMap<Object, Object> cache = new HashMap<>();
  @Getter @Setter private DhruvaAppRecord appRecord;

  public ProxySIPRequest(
      ExecutionContext executionContext,
      SipProvider provider,
      Request request,
      ServerTransaction transaction) {
    super(executionContext, provider, transaction, request);
    this.cookie = new ProxyCookieImpl();
  }

  public ProxySIPRequest(ProxySIPRequest proxySIPRequest) {
    super(proxySIPRequest);
    this.proxyStatelessTransaction = proxySIPRequest.proxyStatelessTransaction;
    this.proxyInterface = proxySIPRequest.proxyInterface;
    this.network = proxySIPRequest.network;
    this.outgoingNetwork = proxySIPRequest.outgoingNetwork;
    this.cookie = proxySIPRequest.cookie.clone();
    this.params = proxySIPRequest.params;
    this.statefulClientTransaction = proxySIPRequest.statefulClientTransaction;
    this.proxyClientTransaction = proxySIPRequest.proxyClientTransaction;
    this.lrFixUri = proxySIPRequest.lrFixUri;
    this.m_routeTo =
        proxySIPRequest.m_routeTo == null ? null : (URI) proxySIPRequest.m_routeTo.clone();
    this.m_escaped = proxySIPRequest.m_escaped;
    this.cache = proxySIPRequest.cache;
    // We are not cloning appRecord , so it will point to same memory having initial states added.
    // Generally while sending out the message is cloned.
    // TBD
    this.appRecord = proxySIPRequest.appRecord;
  }

  public CompletableFuture<ProxySIPResponse> proxy(EndPoint endPoint) {
    return this.proxyInterface.proxyRequest(this, endPoint);
  }

  public void reject(int responseCode, String additionalDetails) {
    if (this.proxyInterface == null) {
      throw new RuntimeException("proxy interface not set, unable to forward the request");
    }
    if (!this.getRequest().getMethod().equals(Request.ACK)) {
      logger.error(
          "reject with response code {} ,dhruva message record {}",
          responseCode,
          this.getAppRecord() == null ? "None" : this.getAppRecord().toString());
      this.proxyInterface.respond(responseCode, additionalDetails, this);
    }
  }

  @Override
  public void sendSuccessResponse() {}

  @Override
  public void sendFailureResponse() {}

  @Override
  public void sendRateLimitedFailureResponse() {}

  @Override
  public String toTraceString() {
    return null;
  }

  @Override
  public boolean validate() {
    return false;
  }

  @Override
  public boolean applyRateLimitFilter() {
    return false;
  }

  @Override
  public String eventType() {
    return null;
  }

  @Override
  public ReasonHeader getReason() {
    return null;
  }

  @Override
  public Integer getReasonCause() {
    return null;
  }

  @Override
  public boolean isSipConference() {
    return false;
  }

  @Override
  public SIPMessage getSIPMessage() {
    return this.getRequest();
  }

  /**
   * Apply the loose routing protect operation if necessary:
   *
   * <p>If the next hop is a strict router (top Route header's URI does not contain the 'lr'
   * parameter), move the request URI to the bottom Route header and the top Route header to the the
   * request URI.
   *
   * @return the request URI if no Route header exists or the top (pre-protection) Route header's
   *     URI
   * @throws ParseException if the parser encounters an error.
   */
  public URI lrEscape() throws ParseException {
    // do the escape checking ONE time
    if (m_routeTo != null) return m_routeTo;

    m_routeTo = req.getRequestURI();

    RouteHeader topRoute = (RouteHeader) req.getHeader(RouteHeader.NAME);
    if (topRoute != null) {
      m_routeTo = topRoute.getAddress().getURI();
      if (!((SipURI) m_routeTo).hasLrParam()) {
        SipURI reqURI = (SipURI) req.getRequestURI();
        RouteHeader routeHeader =
            JainSipHelper.createRouteHeader(
                reqURI.getUser(), reqURI.getHost(), reqURI.getPort(), reqURI.getTransportParam());
        // Add header to the bottom
        req.addHeader(routeHeader);
        req.setRequestURI(m_routeTo);
        req.removeFirst(RouteHeader.NAME);
        m_escaped = true;
      }
    }
    return m_routeTo;
  }

  /**
   * Returns new proxySIPRequest whose meta data are same as original proxySipRequest but Request is
   * clone,i.e new copy
   *
   * @return
   */
  public Object clone() {
    return new ProxySIPRequest(this);
  }

  public void handleProxyEvent(MetricService metricService, SipMetricsContext.State state) {
    this.manageLatencyMetrics.accept(metricService, state);
  }

  public BiConsumer<MetricService, SipMetricsContext.State> manageLatencyMetrics =
      (metricService, state) -> {
        // Emit latency metric for non mid-dialog requests
        if (metricService != null && !SipUtils.isMidDialogRequest(this.getRequest()))
          new SipMetricsContext(
              metricService, state, this.getCallId(), this.getCallTypeName(), true);
      };

  public ProxySIPResponse createResponse(int respCode, String details)
      throws DhruvaException, ParseException {
    return proxyInterface.createResponse(respCode, this, details);
  }
}
