package com.cisco.dsb.common.sip.header;

import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.transport.Transport;
import gov.nist.javax.sip.address.SipUri;
import java.text.ParseException;
import lombok.CustomLog;
import lombok.Getter;

@CustomLog
public class ListenIfHeader {

  @Getter private final String name;
  @Getter private final int port;
  @Getter private final Transport protocol;
  // this could be either external FQDN or internal FQDN
  private final String hostName;
  @Getter private final String ipAddress;
  // External IP associated with the listenPoint Interface
  private final String externalIP;
  // sipUri is indexed based on HostnameType enum ordinal
  private final SipUri[] sipUris = new SipUri[3];

  @Getter private final HostnameType hostnameType;

  public enum HostnameType {
    LOCAL_IP,
    FQDN,
    EXTERNAL_IP
  }

  public ListenIfHeader(
      String ipAddress,
      Transport protocol,
      int port,
      String externalIP,
      String hostName,
      String networkName,
      HostnameType hostnameType) {
    this.ipAddress = ipAddress;
    this.protocol = protocol;
    this.port = port;
    this.externalIP = externalIP;
    this.hostName = hostName;
    this.name = networkName;
    this.hostnameType = hostnameType;
    init();
  }

  public void init() {
    try {
      sipUris[0] = (SipUri) JainSipHelper.getAddressFactory().createSipURI(null, this.ipAddress);
      setParams(sipUris[0]);
      if (this.hostName != null) {
        sipUris[1] = (SipUri) JainSipHelper.getAddressFactory().createSipURI(null, this.hostName);
        setParams(sipUris[1]);
      }
      if (this.externalIP != null) {
        sipUris[2] = (SipUri) JainSipHelper.getAddressFactory().createSipURI(null, this.externalIP);
        setParams(sipUris[2]);
      }
    } catch (ParseException exception) {
      logger.error("Unable to create SipURI for ListenPoint {}", exception.getMessage());
      throw new IllegalArgumentException(exception);
    }
  }

  /**
   * Returns a SipUri based on the hostnameType type. If the hostnameType type does not exist for
   * this interface then null SipUri is returned.
   */
  public SipUri getSipUri(HostnameType hostnameType) {
    SipUri uri = sipUris[hostnameType.ordinal()];
    if (uri != null) {
      return ((SipUri) uri.clone());
    }
    return null;
  }

  /** Returns default sipUri associated with ListenIf. */
  public SipUri getSipUri() {
    SipUri uri = sipUris[hostnameType.ordinal()];
    if (uri != null) {
      return ((SipUri) uri.clone());
    }
    return null;
  }

  public boolean recognize(SipUri uri) {
    for (SipUri mySipUri : this.sipUris) {
      if (mySipUri != null) {
        if (mySipUri.getHostPort().equals(uri.getHostPort())
            && mySipUri.getTransportParam().equals(uri.getTransportParam())) {
          logger.debug("SipURI {} matches listenIfHeader {}", uri, name);
          return true;
        }
      }
    }
    logger.debug("SipURI {} does not match listenIfHeader {}", uri.getUserAtHostPort(), name);
    return false;
  }

  private void setParams(SipUri sipUri) throws ParseException {
    sipUri.setTransportParam(this.protocol.toString());
    sipUri.setParameter("lr", null);
    sipUri.setPort(this.port);
  }
}
