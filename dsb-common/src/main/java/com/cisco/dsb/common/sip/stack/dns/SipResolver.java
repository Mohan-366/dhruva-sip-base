package com.cisco.dsb.common.sip.stack.dns;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.sip.enums.LocateSIPServerTransportType;
import com.cisco.dsb.common.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.common.transport.Transport;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import javax.sip.address.SipURI;

/** A common interface used to search for a SIP endpoint. */
interface SipResolver {

  public LocateSIPServersResponse resolve(
      String name, LocateSIPServerTransportType transport, @Nullable Integer port)
      throws ExecutionException, InterruptedException;

  public LocateSIPServersResponse resolve(
      String name,
      LocateSIPServerTransportType transportLookupType,
      @Nullable Integer port,
      @Nullable String userIdInject)
      throws ExecutionException, InterruptedException;

  /**
   * Indicate to the resolver that the message is larger than the path MTU.
   *
   * @param sizeExceedsMTU set to <code>true</code> to indicate that the message size exceeds the
   *     path MTU, otherwise set to <code>false</code>.
   */
  void setSizeExceedsMTU(boolean sizeExceedsMTU);

  /**
   * Set the supported transports.
   *
   * @param supported_transports a bit mask of the transports supported by this instance of the
   *     stack.
   */
  void setSupportedTransports(byte supported_transports);

  boolean shouldSearch(SipURI sip_url) throws DhruvaException;

  boolean shouldSearch(String host_name, int port, Transport transport);

  /**
   * Return <code>true</code> if this resolver has been configured to support a particular transport
   * as defined in enum Transport.
   *
   * @return <code>true</code> if this resolver has been configured to support a particular
   *     transport as defined in enum Transport.
   */
  boolean isSupported(Transport transport);
}
