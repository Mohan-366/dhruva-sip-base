package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.proxy.controller.ControllerConfig;
import com.cisco.dsb.proxy.errors.DestinationUnreachableException;
import gov.nist.javax.sip.header.RecordRouteList;
import gov.nist.javax.sip.header.ViaList;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import javax.sip.ServerTransaction;
import lombok.CustomLog;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * This class represents the ServerTransaction on the receiving side of ProxyTransaction. Note that
 * more than one such transaction may exist due to merged requests.
 */
@CustomLog
public class ProxyServerTransaction {

  private ServerTransaction serverTransaction;
  private ProxyTransaction proxy;
  private ControllerConfig controllerConfig;
  @Getter @Setter private boolean isInternallyGeneratedResponse = false;
  @Getter @Setter private String additionalDetails;

  /** last response sent */
  private SIPResponse response;

  private int rrIndexFromEnd;

  private int numVias = 0;

  private boolean okResponseSent = false;

  protected ProxyServerTransaction(
      ProxyTransaction proxy, ServerTransaction trans, SIPRequest request) {
    serverTransaction = trans;
    this.proxy = proxy;
    this.controllerConfig = this.proxy.getController().getControllerConfig();

    ViaList viaHeaders = request.getViaHeaders();
    if (viaHeaders != null) numVias = viaHeaders.size();

    logger.debug("Counted number of Vias: " + numVias);

    if (controllerConfig.isStateful()) {
      RecordRouteList recordRouteHeaders = request.getRecordRouteHeaders();
      rrIndexFromEnd = (recordRouteHeaders == null) ? 0 : recordRouteHeaders.size();
    } else {
      rrIndexFromEnd = -1; // not valid in stateless
    }
  }

  public void respond(@NonNull SIPResponse response) throws DestinationUnreachableException {

    // send the response
    try {
      ViaList vias = response.getViaHeaders();
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
      String callTypeName =
          this.proxy.getClientTransaction() != null
              ? this.proxy.getClientTransaction().getProxySIPRequest().getCallTypeName()
              : null;
      ProxySendMessage.sendResponse(
          serverTransaction,
          response,
          this.isInternallyGeneratedResponse(),
          callTypeName,
          this.getAdditionalDetails());
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
}
