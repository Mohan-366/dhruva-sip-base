/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
// FILENAME:	$RCSFile$
//
// MODULE:	servergroups
//
// $Id: ServerGlobalStateWrapper.java,v 1.14.2.1 2005/11/21 01:45:27 lthamman Exp $
//
// COPYRIGHT:
// ============== copyright 2000 dynamicsoft Inc. =================
// ==================== all rights reserved =======================
// /////////////////////////////////////////////////////////////////
package com.cisco.dsb.servergroups;

import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.Trace;
import java.util.LinkedList;
import java.util.List;

/**
 * This class is a wrapper around state information that needs to be common to all end points with
 * the same host, port, and transport-type tuple. The state information includes a retry after date
 * and the number of tries the end point referenced by this object has been unsucessfully used.
 */
public class ServerGlobalStateWrapper {
  private static final Trace Log = Trace.getTrace(ServerGlobalStateWrapper.class.getName());

  private List parentServerGroupList = new LinkedList();

  String network;
  String host;
  Transport protocol;
  int port;

  public String getHost() {
    return host;
  }

  public Transport getProtocol() {
    return protocol;
  }

  /**
   * Creates a new ServerGlobalStateWrapper object with the given <code>EndPoint</code> and number
   * of tries.
   *
   * @param host host address of the PingObject
   * @param port port number of the PingObject
   * @param protocol the transport type of the PingObject is considered to have failed.
   */
  public ServerGlobalStateWrapper(
      String network,
      String host,
      String parentServerGroup,
      int port,
      Transport protocol,
      Boolean dnsServerGroup) {

    this.network = network;
    this.host = host;
    this.protocol = protocol;
    this.port = port;

    parentServerGroupList.add(parentServerGroup);
    if (Log.on && Log.isInfoEnabled()) Log.info("ServerGlobalStateWrapper created for " + this);
  }

  public String getNetwork() {
    return network;
  }

  public int getPort() {
    return port;
  }
}
