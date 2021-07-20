package com.cisco.dhruva.sip.proxy;

import com.cisco.dhruva.sip.controller.ControllerConfig;
import com.cisco.dhruva.sip.proxy.errors.DestinationUnreachableException;
import com.cisco.dhruva.sip.proxy.errors.InvalidStateException;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.sip.util.ReConstants;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import com.google.common.collect.Iterators;
import gov.nist.javax.sip.header.RecordRouteList;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.header.ViaList;
import gov.nist.javax.sip.header.ims.PathHeader;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.util.ListIterator;
import java.util.StringTokenizer;
import javax.sip.InvalidArgumentException;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.address.SipURI;
import javax.sip.header.RecordRouteHeader;

/**
 * This class represents the ServerTransaction on the receiving side of ProxyTransaction. Note that
 * more than one such transaction may exist due to merged requests.
 */
public class ProxyServerTransaction {

  private ServerTransaction serverTransaction;
  private ProxyTransaction proxy;
  private ControllerConfig controllerConfig;

  /** request received */
  private SIPRequest request;
  /** last response sent */
  private SIPResponse response;

  private int rrIndexFromEnd;
  private int pathIndexFromEnd;

  private int numVias = 0;

  private boolean okResponseSent = false;

  // replaced by the full binding info object
  // protected DsNetwork network; // remember the network a request was received on
  // protected DsByteString connId;

  // protected DsBindingInfo bindingInfo;
  private static final int UNINITIALIZED = -1;

  protected static Logger Log = DhruvaLoggerFactory.getLogger(ProxyServerTransaction.class);

  protected ProxyServerTransaction(
      ProxyTransaction proxy, ServerTransaction trans, SIPRequest request) {
    serverTransaction = trans;
    this.proxy = proxy;
    this.controllerConfig = this.proxy.getController().getControllerConfig();

    ViaList viaHeaders = request.getViaHeaders();
    // DsSipHeaderList viaHeaders = request.getHeaders(DsSipConstants.VIA);
    if (viaHeaders != null) numVias = viaHeaders.size();

    Log.debug("Counted number of Vias: " + numVias);

    // replaced by the full binding info object
    // network = request.getNetwork();
    // connId = request.getBindingInfo().getConnectionId();
    if (controllerConfig.isStateful()) {
      int recordRouteHeaderCount = UNINITIALIZED;
      RecordRouteList recordRouteHeaders = request.getRecordRouteHeaders();
      // DsSipHeaderList recordRouteHeaders = request.getHeaders(DsSipRecordRouteHeader.sID);

      if (recordRouteHeaders == null) {
        rrIndexFromEnd = 0;
      } else {
        RecordRouteList recordRouteHeadersClone =
            (RecordRouteList) request.getRecordRouteHeaders().clone();
        recordRouteHeaderCount = recordRouteHeadersClone.size();
        //                DsSipHeaderList recordRouteHeadersClone = (DsSipHeaderList)
        // recordRouteHeaders.clone();
        //                try {
        //                    //recordRouteHeadersClone.validate();
        //
        //                } catch (DsSipParserException e) {
        //                    Log.warn("Exception in parsing Record-Route header ", e);
        //                } catch (DsSipParserListenerException e) {
        //                    Log.warn("Exception in parsers listener while parsing Record-Route
        // header ", e);
        //                }
        if (recordRouteHeaderCount != UNINITIALIZED) {
          rrIndexFromEnd = recordRouteHeaderCount;
        } else {
          rrIndexFromEnd = recordRouteHeaders.size(); // at least it will be in a
          // moment
        }
      }
      ListIterator<SIPHeader> pathHeaderListIterator = request.getHeaders(PathHeader.NAME);
      pathIndexFromEnd = Iterators.size(pathHeaderListIterator);
    } else {
      rrIndexFromEnd = -1; // not valid in stateless
      pathIndexFromEnd = -1;
    }

    // bindingInfo = (DsBindingInfo) request.getBindingInfo().clone();
  }

  protected ServerTransaction getTransaction() {
    return serverTransaction;
  }

  public void respond(SIPResponse response) throws DestinationUnreachableException {

    Log.debug("Entering respond()");

    // send the response
    try {
      if (response != null) {
        ViaList vias = response.getViaHeaders();
        // DsSipHeaderList vias = response.getHeaders(DsSipConstants.VIA);
        int numResponseVias = vias != null ? vias.size() : 0;

        Log.debug("numResponseVias=" + numResponseVias + ", numVias=" + numVias);

        for (int x = numResponseVias; x > numVias; x--) {
          assert vias != null;
          vias.removeFirst();
        }

        if (controllerConfig.doRecordRoute()) setRecordRouteInterface(response);
      }

      // TODO DSB
      serverTransaction.sendResponse(response);
      // ProxySendMessage.sendResponse(serverTransaction, response);

      this.response = response;

      if (response != null && ProxyUtils.getResponseClass(response) == 2 && !okResponseSent) {
        // remove Transaction after a while even
        // if the ACK to 200 has not been received
        //	serverTransaction.setTn(60000);
        okResponseSent = true;
        Log.debug("Tn timer set for ServerTransaction");
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

  public void setRecordRouteInterface(SIPMessage msg) throws DhruvaException, ParseException {
    Log.debug("Entering setRecordRouteInterface()");

    if (msg.getHeaders(RecordRouteHeader.NAME).hasNext()) {
      if (rrIndexFromEnd >= 0) {
        // stateful, just flip your own
        setRecordRouteInterfaceStateful(msg);
      } else {
        // stateless, must flip them all
        setRecordRouteInterfaceStateless(msg);
      }
    }
  }

  // REDDY_RR_CHANGE
  private void setRecordRouteInterfaceStateful(SIPMessage msg)
      throws DhruvaException, ParseException {
    // int interfacing = (m_RequestDirection == DsControllerConfig.INBOUND) ?
    // DsControllerConfig.OUTBOUND : DsControllerConfig.INBOUND;

    RecordRouteList rrList = msg.getRecordRouteHeaders();
    // DsSipHeaderList rrList = null;

    // rrList = msg.getHeadersValidate(DsSipConstants.RECORD_ROUTE);
    //
    //        boolean compress = msg.shouldCompress();
    //        DsTokenSipDictionary encode = msg.shouldEncode();

    if (rrList == null) {
      Log.info("route header list is null in incoming message, not processing record route");
      return;
    }
    int routeIndex = rrList.size() - rrIndexFromEnd - 1;

    if ((routeIndex >= 0) && (routeIndex < rrList.size())) {
      RecordRouteHeader rrHeader = (RecordRouteHeader) rrList.get(routeIndex);
      SipURI currentRRURL = (SipURI) rrHeader.getAddress().getURI();
      setRRHelper(msg, currentRRURL, false);
    }
  }

  private void setRRHelper(SIPMessage msg, SipURI currentRRURL, boolean compress)
      throws ParseException {

    String currentRRURLHost = null;

    if (currentRRURL != null) {

      // get the network corresponding to the host portion in RR. If host contains externalIP,
      // get the localIP to know the network accordingly
      currentRRURLHost =
          com.cisco.dsb.sip.hostPort.HostPortUtil.reverseHostInfoToLocalIp(
              controllerConfig, currentRRURL);

      String network = null;
      String name =
          controllerConfig.checkRecordRoutes(
              currentRRURL.getUser(),
              new String(currentRRURLHost),
              currentRRURL.getPort(),
              currentRRURL.getTransportParam());

      if (name != null) {
        // todo optimize when get a chance
        Log.debug("Record Route URL to be modified : " + currentRRURL);
        String u = currentRRURL.getUser();
        String user = null;
        if (u != null) {
          user = u;
        }
        if (user != null) {
          StringTokenizer st = new StringTokenizer(user);
          String t = st.nextToken(ReConstants.DELIMITER_STR);
          while (t != null) {
            if (t.startsWith(ReConstants.NETWORK_TOKEN)) {
              network = t.substring(ReConstants.NETWORK_TOKEN.length());
              user = user.replaceFirst(t, ReConstants.NETWORK_TOKEN + name);
              Log.debug("Replace Record-route host from {} to {}", t, name);
              break;
            }
            t = st.nextToken(ReConstants.DELIMITER_STR);
          }
          currentRRURL.setUser(user);

        } else {
          network = ((DhruvaNetwork) msg.getApplicationData()).getName();
        }

        Log.debug(
            "Outgoing network of the message for which record route has to be modified : "
                + network);
        RecordRouteHeader recordRouteInterfaceHeader =
            controllerConfig.getRecordRouteInterface(network, false);

        if (recordRouteInterfaceHeader == null) {
          Log.debug("Did not find the Record Routing Interface!");
          return;
        }

        SipURI RRUrl = (SipURI) recordRouteInterfaceHeader.getAddress();

        // replace local IP with External IP for public network when modifying user portion of RR
        currentRRURL.setHost(
            com.cisco.dsb.sip.hostPort.HostPortUtil.convertLocalIpToHostInfo(
                controllerConfig, RRUrl));

        if (RRUrl.getPort() >= 0) {
          currentRRURL.setPort(RRUrl.getPort());
        } else {
          currentRRURL.removePort();
        }

        if (RRUrl.getTransportParam() != null) {
          currentRRURL.setTransportParam(RRUrl.getTransportParam());
        } else {
          currentRRURL.removeParameter("transport");
        }
        Log.debug("Modified Record route URL to : " + currentRRURL);
      }
    }
  }

  private void setRecordRouteInterfaceStateless(SIPMessage msg)
      throws DhruvaException, ParseException {
    // DsSipHeaderList rrHeaders = msg.getHeadersValidate(DsSipConstants.RECORD_ROUTE);
    RecordRouteList rrHeaders = msg.getRecordRouteHeaders();
    if (rrHeaders != null && rrHeaders.size() > 0) {
      for (Object rrHeader : rrHeaders) {
        RecordRouteHeader recordRouteHeader = (RecordRouteHeader) rrHeader;
        setRRHelper(msg, (SipURI) recordRouteHeader.getAddress(), false);
      }
    }
  }
}
