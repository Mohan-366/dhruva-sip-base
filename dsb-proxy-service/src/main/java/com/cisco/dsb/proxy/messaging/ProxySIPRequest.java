package com.cisco.dsb.proxy.messaging;

import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.messaging.models.AbstractSipRequest;
import com.cisco.dsb.proxy.sip.ProxyClientTransaction;
import com.cisco.dsb.proxy.sip.ProxyCookie;
import com.cisco.dsb.proxy.sip.ProxyInterface;
import com.cisco.dsb.proxy.sip.ProxyParamsInterface;
import com.cisco.dsb.proxy.sip.ProxyStatelessTransaction;
import com.cisco.dsb.sip.jain.JainSipHelper;
import com.cisco.dsb.sip.util.Location;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import java.io.IOException;
import java.text.ParseException;
import javax.servlet.ServletException;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.ReasonHeader;
import javax.sip.header.RouteHeader;
import javax.sip.message.Request;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class ProxySIPRequest extends AbstractSipRequest {
  @Getter @Setter private ProxyStatelessTransaction proxyStatelessTransaction;
  @Getter @Setter private ProxyInterface proxyInterface;
  @Getter @Setter private String network;
  @Getter @Setter private SIPRequest clonedRequest;
  @Getter @Setter private Location location;
  @Getter @Setter private String outgoingNetwork = null;
  @Getter @Setter private ProxyCookie cookie;
  @Getter @Setter private ProxyParamsInterface params;
  @Getter @Setter private boolean statefulClientTransaction;
  @Getter @Setter private ProxyClientTransaction proxyClientTransaction;
  @Getter @Setter private URI lrFixUri = null;
  @Getter @Setter private URI m_routeTo = null;
  @Getter @Setter private boolean m_escaped = false;

  Logger logger = DhruvaLoggerFactory.getLogger(ProxySIPRequest.class);

  public ProxySIPRequest(
      ExecutionContext executionContext,
      SipProvider provider,
      Request request,
      ServerTransaction transaction)
      throws IOException {
    super(executionContext, provider, transaction, request);
  }

  public void proxy(@NonNull ProxySIPRequest proxySIPRequest, @NonNull Location location) {
    if (this.proxyInterface == null) {
      throw new RuntimeException("proxy does not right interface set to forward the request");
    }
    this.proxyInterface.proxyRequest(proxySIPRequest, location);
  }

  @Override
  public void sendSuccessResponse() throws IOException, ServletException {}

  @Override
  public void sendFailureResponse() throws IOException, ServletException {}

  @Override
  public void sendRateLimitedFailureResponse() {}

  @Override
  public String toTraceString() {
    return null;
  }

  @Override
  public boolean validate() throws IOException, ServletException {
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
    SIPRequest request;

    if (this.clonedRequest != null) {
      request = this.getClonedRequest();
    } else {
      request = this.getRequest();
    }

    m_routeTo = request.getRequestURI();

    RouteHeader topRoute = (RouteHeader) request.getHeader(RouteHeader.NAME);
    if (topRoute != null) {
      m_routeTo = topRoute.getAddress().getURI();
      if (!((SipURI) m_routeTo).hasLrParam()) {
        SipURI reqURI = (SipURI) request.getRequestURI();
        RouteHeader routeHeader =
            JainSipHelper.createRouteHeader(
                reqURI.getUser(), reqURI.getHost(), reqURI.getPort(), reqURI.getTransportParam());
        // Add header to the bottom
        request.addHeader(routeHeader);
        request.setRequestURI(m_routeTo);
        request.removeFirst(RouteHeader.NAME);
        m_escaped = true;
      }
    }
    return m_routeTo;
  }
}
