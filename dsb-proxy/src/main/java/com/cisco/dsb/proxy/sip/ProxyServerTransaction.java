package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.proxy.controller.ControllerConfig;
import com.cisco.dsb.proxy.errors.DestinationUnreachableException;
import com.cisco.dsb.proxy.errors.InvalidStateException;
import gov.nist.javax.sip.header.RecordRouteList;
import gov.nist.javax.sip.header.ViaList;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import javax.sip.InvalidArgumentException;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import lombok.CustomLog;
import lombok.NonNull;

/**
 * This class represents the ServerTransaction on the receiving side of ProxyTransaction. Note that
 * more than one such transaction may exist due to merged requests.
 */
@CustomLog
public class ProxyServerTransaction {

  private ServerTransaction serverTransaction;
  private ProxyTransaction proxy;
  private ControllerConfig controllerConfig;

  /** request received */
  private SIPRequest request;
  /** last response sent */
  private SIPResponse response;

  private int rrIndexFromEnd;

  private int numVias = 0;

  private boolean okResponseSent = false;

  private static final int UNINITIALIZED = -1;

  // protected static Logger Log = DhruvaLoggerFactory.getLogger(ProxyServerTransaction.class);

  protected ProxyServerTransaction(
      ProxyTransaction proxy, ServerTransaction trans, SIPRequest request) {
    serverTransaction = trans;
    this.proxy = proxy;
    this.controllerConfig = this.proxy.getController().getControllerConfig();
    //    this.request = (SIPRequest) trans.getRequest();
    ViaList viaHeaders = request.getViaHeaders();
    if (viaHeaders != null) numVias = viaHeaders.size();

    logger.debug("Counted number of Vias: " + numVias);

    if (controllerConfig.isStateful()) {
      RecordRouteList recordRouteHeaders = request.getRecordRouteHeaders();
      rrIndexFromEnd = (recordRouteHeaders == null) ? 0 : recordRouteHeaders.size();
    } else {
      rrIndexFromEnd = -1; // not valid in stateless
    }

    // bindingInfo = (DsBindingInfo) request.getBindingInfo().clone();
  }

  public void respond(@NonNull SIPResponse response) throws DestinationUnreachableException {

    // logger.debug("Entering respond()");

    // send the response
    try {
      ViaList vias = response.getViaHeaders();
      // DsSipHeaderList vias = response.getHeaders(DsSipConstants.VIA);
      int numResponseVias = vias != null ? vias.size() : 0;

      logger.debug("numResponseVias=" + numResponseVias + ", numVias=" + numVias);
      for (int x = numResponseVias; x > numVias; x--) {
        vias.removeFirst();
      }

      if (controllerConfig.doRecordRoute()) {
        if (rrIndexFromEnd >= 0)
          controllerConfig.setRecordRouteInterface(response, false, rrIndexFromEnd);
        else controllerConfig.setRecordRouteInterface(response, true, rrIndexFromEnd);
      }

      ProxySendMessage.sendResponse(serverTransaction, response);
      // TODO there can be multiple responses associated with single serverTransaction, so this
      // should be a list?
      this.response = response;

      if (ProxyUtils.getResponseClass(response) == 2 && !okResponseSent) {
        okResponseSent = true;
      }

    } catch (Exception e) {
      throw new DestinationUnreachableException("Error sending a response" + e);
    }
  }

  protected SIPResponse getResponse() {
    return response;
  }

  /** This is used to handle the special case with INVITE 200OK retransmissions */
  protected void retransmit200() throws DhruvaException, SipException, InvalidArgumentException {

    if (response != null && ProxyUtils.getResponseClass(response) == 2) {
      // respond(response);
      serverTransaction.sendResponse(response);
    } else throw new InvalidStateException("Cannot retransmit in this state");
  }
}
