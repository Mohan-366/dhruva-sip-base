package com.cisco.dsb.sip.stack.dto;

import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import java.io.Serializable;
import java.net.InetAddress;

/**
 * This class is a container for holding onto port, address and protocol information. When data
 * arrives at the transport layer, this class is used to capture the port and address on which the
 * data arrived. The transaction manager, then, passes the information forward to the message. When
 * a new passive connection is established, binding info is used as a key to the table which stores
 * connection data for reuse. When an active connection is sought, this class is used to address a
 * message.
 */
public class BindingInfo implements Cloneable, Serializable {
  /** Indicates an unspecified local port. */
  // changed its value to 0 as it is used by JDK Socket classes.
  public static final int LOCAL_PORT_UNSPECIFIED = 0;
  /** Indicates an unspecified remote port. */
  public static final int REMOTE_PORT_UNSPECIFIED = 0;
  /** Indicates an unspecified transport. */
  public static final Transport BINDING_TRANSPORT_UNSPECIFIED = Transport.NONE;

  /** Indicates that there is no local address or port. */
  public static final byte NO_LOCAL_ADDR_PORT = 0;
  /** Indicates that there is a port address only. */
  public static final byte LOCAL_ADDR_ONLY = 1;
  /** Indicates that there is a local address only. */
  public static final byte LOCAL_PORT_ONLY = 2;
  /** Indicates that there is both a local address and port. */
  public static final byte LOCAL_ADDR_PORT = 3;

  /** Constant representing an empty binding info object. */
  public static final BindingInfo EMPTY_INFO = new BindingInfo();

  /////////////   instance data

  private byte m_localBinding = NO_LOCAL_ADDR_PORT;
  private boolean isBindingInfoValid;

  private boolean m_IsTrying;
  private String m_RemoteAddressStr;
  private InetAddress m_RemoteAddress;
  private int m_RemotePort;
  private InetAddress m_LocalAddress;
  private int m_LocalPort = LOCAL_PORT_UNSPECIFIED;
  private int m_LocalEphemeralPort = m_LocalPort;
  private Transport m_Transport = Transport.NONE;
  private boolean m_PendingClosure;
  private boolean m_Compress;

  private byte m_Network;
  private String m_strConnectionId;
  private Logger logger = DhruvaLoggerFactory.getLogger(BindingInfo.class);

  /**
   * Constructs the binding info with the specified remote address, port and transport.
   *
   * @param remote_addr remote InetAddress
   * @param remote_port remote port
   * @param transport transport
   * @param pending_closure if set to <code>true</code> the connection is closing
   */
  public BindingInfo(
      InetAddress remote_addr, int remote_port, Transport transport, boolean pending_closure) {
    m_IsTrying = false;
    m_LocalAddress = null;
    m_LocalPort = LOCAL_PORT_UNSPECIFIED;
    m_LocalEphemeralPort = m_LocalPort;
    m_PendingClosure = pending_closure;
    m_Compress = false;
    m_RemoteAddress = remote_addr;
    m_RemoteAddressStr = null;
    m_RemotePort = remote_port;
    m_Transport = transport;
    m_Network = DhruvaNetwork.NONE;
  }

  /**
   * Constructs the binding info and it needs the binding info to be set by using the relevant
   * setter methods.
   */
  public BindingInfo() {
    m_IsTrying = false;
    m_LocalAddress = null;
    m_LocalPort = LOCAL_PORT_UNSPECIFIED;
    m_LocalEphemeralPort = m_LocalPort;
    m_PendingClosure = false;
    m_Compress = false;
    m_RemoteAddress = null;
    m_RemotePort = REMOTE_PORT_UNSPECIFIED;

    // m_Transport      = DsSipTransportType.UDP;
    m_Transport = Transport.NONE;
    m_Network = DhruvaNetwork.NONE;
  }

  /**
   * Constructs the binding info with the specified local and remote address information.
   *
   * @param local_addr the local address
   * @param local_port the local port number
   * @param remote_addr the remote address
   * @param remote_port the remote port number
   * @param transport the transport protocol
   */
  public BindingInfo(
      InetAddress local_addr,
      int local_port,
      InetAddress remote_addr,
      int remote_port,
      Transport transport) {
    this(local_addr, local_port, remote_addr, remote_port, transport, false, false);
  }

  /**
   * Constructs the binding info with the specified local and remote address information.
   *
   * @param remote_addr remote InetAddress
   * @param remote_port remote port
   * @param local_addr local InetAddress
   * @param local_port local port
   * @param transport transport
   * @param pending_closure if set to <code>true</code> the connection is closing
   */
  public BindingInfo(
      InetAddress local_addr,
      int local_port,
      InetAddress remote_addr,
      int remote_port,
      Transport transport,
      boolean pending_closure) {
    m_IsTrying = false;
    m_LocalAddress = local_addr;
    m_LocalPort = local_port;
    m_LocalEphemeralPort = m_LocalPort;
    m_PendingClosure = pending_closure;
    m_RemoteAddress = remote_addr;
    m_RemotePort = remote_port;
    m_Transport = transport;
    m_Compress = false;
    m_Network = DhruvaNetwork.NONE;
  }

  /**
   * Constructs the binding info with the specified remote address information.
   *
   * @param remote_addr remote InetAddress
   * @param remote_port remote port
   * @param transport the transport protocol
   */
  public BindingInfo(InetAddress remote_addr, int remote_port, Transport transport) {
    this(remote_addr, remote_port, transport, false, false);
  }

  /**
   * Constructs the binding info with the specified address information.
   *
   * @param addr the remote address
   * @param port the remote port number
   * @param transport the transport protocol
   */
  public BindingInfo(String addr, int port, Transport transport) {
    m_IsTrying = false;
    m_LocalAddress = null;
    m_LocalPort = LOCAL_PORT_UNSPECIFIED;
    m_LocalEphemeralPort = m_LocalPort;
    m_PendingClosure = false;
    m_Compress = false;
    m_RemoteAddressStr = addr;
    m_RemoteAddress = null;
    m_RemotePort = port;
    m_Transport = transport;
    m_Network = DhruvaNetwork.NONE;
  }

  /**
   * Constructs the binding info with the specified address information.
   *
   * @param lAddr the local address
   * @param lPort the local port number
   * @param addr the remote address
   * @param port the remote port number
   * @param transport the transport protocol
   */
  public BindingInfo(InetAddress lAddr, int lPort, String addr, int port, Transport transport) {
    m_IsTrying = false;
    m_LocalAddress = lAddr;
    m_LocalPort = lPort;
    m_LocalEphemeralPort = m_LocalPort;
    m_PendingClosure = false;
    m_Compress = false;
    m_RemoteAddressStr = addr;
    m_RemoteAddress = null;
    m_RemotePort = port;
    m_Transport = transport;
    m_Network = DhruvaNetwork.NONE;
  }

  /**
   * Constructs the binding info with the specified remote address, port and transport.
   *
   * @param remote_addr remote InetAddress
   * @param remote_port remote port
   * @param transport transport
   * @param pending_closure if set to <code>true</code> the connection is closing
   * @param compress if <code>true</code>, and the transport layer is capable, the message will be
   *     compressed on transmission.
   */
  public BindingInfo(
      InetAddress remote_addr,
      int remote_port,
      Transport transport,
      boolean pending_closure,
      boolean compress) {
    m_IsTrying = false;
    m_LocalAddress = null;
    m_LocalPort = LOCAL_PORT_UNSPECIFIED;
    m_LocalEphemeralPort = m_LocalPort;
    m_PendingClosure = pending_closure;
    m_Compress = compress;
    m_RemoteAddress = remote_addr;
    m_RemoteAddressStr = null;
    m_RemotePort = remote_port;
    m_Transport = transport;
    m_Network = DhruvaNetwork.NONE;
  }

  /**
   * Constructs the binding info with the specified local and remote address information.
   *
   * @param remote_addr remote InetAddress
   * @param remote_port remote port
   * @param local_addr local InetAddress
   * @param local_port local port
   * @param transport transport
   * @param pending_closure if set to <code>true</code> the connection is closing
   * @param compress if <code>true</code>, and the transport layer is capable, the message will be
   *     compressed on transmission.
   */
  public BindingInfo(
      InetAddress local_addr,
      int local_port,
      InetAddress remote_addr,
      int remote_port,
      Transport transport,
      boolean pending_closure,
      boolean compress) {
    m_IsTrying = false;
    m_LocalAddress = local_addr;
    m_LocalPort = local_port;
    m_LocalEphemeralPort = m_LocalPort;
    m_PendingClosure = pending_closure;
    m_Compress = compress;
    m_RemoteAddress = remote_addr;
    m_RemotePort = remote_port;
    m_Transport = transport;
    m_Network = DhruvaNetwork.NONE;
  }

  /**
   * TODO:DHRUVA , commenting for now have to take care if needed.
   *
   * <p>/** Returns the string describing the information contained in this binding info.
   *
   * @return the string representation of this class.
   *     <p>public String toString() { String local_info = ""; String remote_info = ""; String
   *     transport; String connId = ", Connection ID = ";
   *     <p>switch (m_Transport) {
   *     <p>case DsSipTransportType.UDP: transport = "UDP";
   *     <p>break;
   *     <p>case DsSipTransportType.TCP: transport = "TCP";
   *     <p>break;
   *     <p>case DsSipTransportType.MULTICAST: transport = "MULTICAST";
   *     <p>break;
   *     <p>case DsSipTransportType.TLS: transport = "TLS";
   *     <p>break; default: transport = "UNKNOWN PROTOCOL"; }
   *     <p>remote_info = " remote[[ port = " + m_RemotePort + " (" + ( getRemoteAddressStr() !=
   *     null ? m_RemoteAddressStr : "?") + ") ]]";
   *     <p>if (m_LocalAddress != null) { local_info = " local[[ port = " + m_LocalPort + " (" +
   *     ((DsString.getHostAddress(m_LocalAddress) != null) ?
   *     DsString.getHostAddress(m_LocalAddress) : "?") + ")]]"; } else { local_info = " local[[
   *     port = " + m_LocalPort + "(?)]]"; }
   *     <p>String nw = getNetwork() == null ? "null" : ("" + getNetwork().isTSIPEnabled()); return
   *     transport + local_info + remote_info + connId + m_strConnectionId + ", network = " +
   *     DsNetwork.getNetwork(m_Network) + ", TSIP = " + nw ; }
   */

  /**
   * Returns true if Pending Closure, false otherwise.
   *
   * @return true if Pending Closure, false otherwise
   */
  public final boolean isPendingClosure() {
    return m_PendingClosure;
  }

  /**
   * Returns true if this message should be compressed.
   *
   * @return true if this message should be compressed
   */
  public final boolean compress() {
    return m_Compress;
  }

  /**
   * Tell the transport layer that message should be compressed.
   *
   * @param compress if <code>true</code>, and the transport layer is capable, the message will be
   *     compressed on transmission.
   */
  public final void compress(boolean compress) {
    m_Compress = compress;
  }

  /**
   * Returns the remote port in the binding info.
   *
   * @return the remote port.
   */
  public final int getRemotePort() {
    return m_RemotePort;
  }

  /**
   * Method used to retrieve the transport type.
   *
   * @return the type of protocol used
   */
  public final Transport getTransport() {
    return m_Transport;
  }

  /**
   * Sets the transport type.
   *
   * @param transport the new transport type.
   */
  public final void setTransport(Transport transport) {
    m_Transport = transport;
  }

  /**
   * Method used to retrieve the InetAddress from where the message was received.
   *
   * @return the remote address.
   */
  public final InetAddress getRemoteAddress() {
    // don't want to set the address to the local address as a side effect
    // of getting it!
    if ((m_RemoteAddress == null) && (m_RemoteAddressStr != null)) {
      try {
        m_RemoteAddress = InetAddress.getByName(m_RemoteAddressStr);
      } catch (Throwable t) {
        logger.warn("Exception while resolving the remote hostname in the Binding Info", t);
      }
    }
    return m_RemoteAddress;
  }

  /**
   * Returns the string representation of the remote address.
   *
   * @return the remote address
   */
  public final String getRemoteAddressStr() {
    if ((m_RemoteAddressStr == null) && (m_RemoteAddress != null)) {
      m_RemoteAddressStr = m_RemoteAddress.getHostAddress();
    }

    return m_RemoteAddressStr;
  }

  /**
   * Returns an instance of InetAddress which contains the local address.
   *
   * @return local address
   */
  public final InetAddress getLocalAddress() {
    return m_LocalAddress;
  }

  /**
   * Returns the local port number.
   *
   * @return the local port.
   */
  public final int getLocalPort() {
    return m_LocalPort;
  }

  public final int getLocalEphemeralPort() {
    return m_LocalEphemeralPort;
  }

  /**
   * Set the local address.
   *
   * @param addr the new local address
   */
  public final void setLocalAddress(InetAddress addr) {
    m_LocalAddress = addr;
  }

  /**
   * Sets the local port number.
   *
   * @param port the new port number
   */
  public final void setLocalPort(int port) {
    m_LocalPort = port;
  }

  public final void setLocalEphemeralPort(int port) {
    m_LocalEphemeralPort = port;
  }

  /**
   * Sets the remote address to the new address specified in the <code>addr</code>.
   *
   * @param addr the new remote address
   */
  public final void setRemoteAddress(InetAddress addr) {
    m_RemoteAddress = addr;
    m_RemoteAddressStr = null;
  }

  /**
   * Sets the remote address to an address specified by the string value <code>addr</code>. It first
   * tries look for the host address specified in the string. If no such host found then throws the
   * UnknownHostException.
   *
   * @param addr the new remote address
   */
  public final void setRemoteAddress(String addr) {
    m_RemoteAddressStr = addr;
    m_RemoteAddress = null;
  }

  /**
   * Sets the remote port number.
   *
   * @param port the new port.
   */
  public final void setRemotePort(int port) {
    m_RemotePort = port;
  }

  /**
   * Checks if the remote address is already set in the binding info.
   *
   * @return true if set, false otherwise
   */
  public boolean isRemoteAddressSet() {
    return !((m_RemoteAddress == null) && (m_RemoteAddressStr == null));
  }

  /**
   * Checks if the local address is already set in the binding info.
   *
   * @return true if set, false otherwise
   */
  public boolean isLocalAddressSet() {
    return m_LocalAddress != null;
  }

  /**
   * Checks if the local port is already set in the binding info.
   *
   * @return true if set, false otherwise
   */
  public boolean isLocalPortSet() {
    return m_LocalPort != LOCAL_PORT_UNSPECIFIED;
  }

  /**
   * Checks if the remote port is already set in the binding info.
   *
   * @return true if set, false otherwise
   */
  public boolean isRemotePortSet() {
    return m_RemotePort != REMOTE_PORT_UNSPECIFIED;
  }

  /**
   * Checks if the transport type is already set in the binding info.
   *
   * @return true if set, false otherwise
   */
  public boolean isTransportSet() {
    return m_Transport != Transport.NONE;
  }

  /**
   * Sets local binding flag which is used to create proper connection.
   *
   * @deprecated it is not intended to be used by user code and might be removed.
   */
  public void determineLocalBindingFlag() {
    if (m_LocalAddress == null) {
      if (m_LocalPort == LOCAL_PORT_UNSPECIFIED) {
        m_localBinding = NO_LOCAL_ADDR_PORT;
      } else {
        m_localBinding = LOCAL_PORT_ONLY;
      }
    } else // if m_LocalAddress != null
    {
      if (m_LocalPort == LOCAL_PORT_UNSPECIFIED) {
        m_localBinding = LOCAL_ADDR_ONLY;
      } else {
        m_localBinding = LOCAL_ADDR_PORT;
      }
    }
  }

  /**
   * Gets the local binding flag.
   *
   * @return the local binding flag
   */
  public int getLocalBindingFlag() {
    return m_localBinding;
  }

  /**
   * Calculates and return the hash code for this class.
   *
   * @return the hash code.
   */
  public int hashCode() {
    int local_code = (m_LocalAddress == null ? 0 : m_LocalAddress.hashCode()) + m_LocalPort;
    int remote_code =
        (m_RemoteAddress == null ? 0 : (m_RemoteAddress.hashCode() >> 2)) + m_RemotePort >> 2;

    return local_code + remote_code;
  }

  /**
   * Compares this object to the specified object <code>other_object</code>.
   *
   * @param other_object the object to compare with
   * @return true if the objects are the same; false otherwise
   */
  public boolean equals(Object other_object) {
    boolean ret_value = false;
    BindingInfo other = (BindingInfo) other_object;

    /*
     * !XOR means they are they are either both null or both !null
     */
    boolean remote_null_agree = !((m_RemoteAddress == null) ^ (other.m_RemoteAddress == null));
    boolean local_null_agree = !((m_LocalAddress == null) ^ (other.m_LocalAddress == null));

    if (!(remote_null_agree && local_null_agree)) {
      ret_value = false;
    } else {

      /* if one addr is not null at this point they are both not null */
      ret_value =
          ((m_Transport == other.m_Transport)
              && (m_LocalAddress != null ? m_LocalAddress.equals(other.m_LocalAddress) : true)
              && (m_RemoteAddress != null ? m_RemoteAddress.equals(other.m_RemoteAddress) : true)
              && (m_LocalPort == other.m_LocalPort)
              && (m_RemotePort == other.m_RemotePort));
    }

    return ret_value;
  }

  /**
   * Checks the status flag if it's trying to connect.
   *
   * @return true if trying to connect, false otherwise
   */
  public final boolean isTrying() {
    return m_IsTrying;
  }

  /**
   * Sets the "trying" status flag.
   *
   * @param trying the "trying" status flag.
   */
  public final void setTrying(boolean trying) {
    m_IsTrying = trying;
  }

  /**
   * Updates this binding info as per the specified binding info.
   *
   * @param new_info the source binding info
   */
  public void update(BindingInfo new_info) {
    m_IsTrying = new_info.m_IsTrying;
    m_RemoteAddressStr = new_info.m_RemoteAddressStr;
    m_RemoteAddress = new_info.m_RemoteAddress;
    m_RemotePort = new_info.m_RemotePort;
    m_LocalAddress = new_info.m_LocalAddress;
    m_LocalPort = new_info.m_LocalPort;
    m_LocalEphemeralPort = new_info.m_LocalEphemeralPort;
    m_Transport = new_info.m_Transport;
    m_PendingClosure = new_info.m_PendingClosure;
    m_Network = new_info.m_Network;

    //
    // don't copy the m_Compress flag since here we are copying the info
    // from the connection onto the message and the connection's
    // m_Compress flag has no meaning since compression decision is made
    // on message by message basis
  }

  /**
   * Make a shallow copy of new_info to this object.
   *
   * @param new_info the source object
   */
  protected void clone(BindingInfo new_info) {
    new_info.m_IsTrying = m_IsTrying;
    new_info.m_RemoteAddressStr = m_RemoteAddressStr;
    new_info.m_RemoteAddress = m_RemoteAddress;
    new_info.m_RemotePort = m_RemotePort;
    new_info.m_LocalAddress = m_LocalAddress;
    new_info.m_LocalPort = m_LocalPort;
    new_info.m_LocalEphemeralPort = m_LocalEphemeralPort;
    new_info.m_Transport = m_Transport;
    new_info.m_PendingClosure = m_PendingClosure;
    new_info.m_Compress = m_Compress;
    new_info.m_Network = m_Network;
    new_info.m_strConnectionId = m_strConnectionId;
  }

  /**
   * Returns a new copy of this object.
   *
   * @return a new copy of this object
   */
  public Object clone() {
    BindingInfo new_info = new BindingInfo();
    clone(new_info);
    return new_info;
  }

  /**
   * Sets the Connection ID parameter for this binding information.
   *
   * @param connectionId The Connection ID parameter that needs to set for this binding information.
   */
  public void setConnectionId(String connectionId) {
    m_strConnectionId = connectionId;
  }

  /**
   * Returns the Connection ID parameter for this binding information.
   *
   * @return the Connection ID parameter for this binding information.
   */
  public String getConnectionId() {
    return m_strConnectionId;
  }

  /**
   * Returns a string representation of the object. In general, the {@code toString} method returns
   * a string that "textually represents" this object. The result should be a concise but
   * informative representation that is easy for a person to read. It is recommended that all
   * subclasses override this method.
   *
   * <p>The {@code toString} method for class {@code Object} returns a string consisting of the name
   * of the class of which the object is an instance, the at-sign character `{@code @}', and the
   * unsigned hexadecimal representation of the hash code of the object. In other words, this method
   * returns a string equal to the value of:
   *
   * <blockquote>
   *
   * <pre>
   * getClass().getName() + '@' + Integer.toHexString(hashCode())
   * </pre>
   *
   * </blockquote>
   *
   * @return a string representation of the object.
   */
  @Override
  public String toString() {
    return new StringBuilder()
        .append(" LocalIP= ")
        .append(getLocalAddress())
        .append(" LocalPort= ")
        .append(getLocalPort())
        .append(" RemoteIPAddress= ")
        .append(getRemoteAddress())
        .append(" RemotePort= ")
        .append(getRemotePort())
        .append(" Transport= ")
        .append(getTransport().name())
        .append(" Network= ")
        .toString();
  }

  /**
   * TODO:DHRUVA , commenting for now have to take care if needed.
   *
   * <p>public void updateBindingInfo(DsSocket socketInfo) {
   *
   * <p>if (this.isBindingInfoValid || socketInfo == null ) { return; } if
   * (this.getLocalEphemeralPort() != socketInfo.getLocalPort()) {
   * this.setLocalEphemeralPort(socketInfo.getLocalPort()); } if (this.getRemotePort() !=
   * socketInfo.getRemotePort()) { this.setRemotePort(socketInfo.getRemotePort()); } if (
   * (socketInfo.getLocalAddress() != null ) && ( this.getLocalAddress() == null ||
   * !this.getLocalAddress().equals(socketInfo.getLocalAddress()))) {
   * this.setLocalAddress(socketInfo.getLocalAddress()); } if ((socketInfo.getRemoteInetAddress() !=
   * null) && ( this.getRemoteAddress() == null ||
   * !this.getRemoteAddress().equals(socketInfo.getRemoteInetAddress()))) {
   * this.setRemoteAddress(socketInfo.getRemoteInetAddress()); } this.isBindingInfoValid = true; }
   */
}
