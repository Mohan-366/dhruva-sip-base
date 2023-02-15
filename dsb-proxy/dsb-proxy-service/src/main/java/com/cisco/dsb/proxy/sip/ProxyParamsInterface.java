package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.sip.header.ListenIfHeader;
import java.text.ParseException;
import javax.sip.InvalidArgumentException;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.ViaHeader;

/** Describes configuration settings of a ProxyTransaction */
public interface ProxyParamsInterface {

  /**
   * Specifies whether the proxy needs to insert itself into the Record-Route
   *
   * @return Record-Route setting
   */
  boolean doRecordRoute();

  /**
   * @return the timeout value in milliseconds for outgoing requests. -1 means default timeout This
   *     allows to set timeout values that are _lower_ than SIP defaults. Values higher than SIP
   *     deafults will have no effect.
   */
  long getRequestTimeout();

  RecordRouteHeader getRecordRoute(
      String user, String network, ListenIfHeader.HostnameType hostnameType);

  ViaHeader getViaHeader(String network, ListenIfHeader.HostnameType hostnameType, String branch)
      throws InvalidArgumentException, ParseException;
}
