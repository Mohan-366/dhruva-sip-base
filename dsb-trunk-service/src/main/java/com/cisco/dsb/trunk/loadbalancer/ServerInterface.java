/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
// FILENAME: $RCSfile: ServerInterface.java,v $
//
// MODULE:  lb
//
// COPYRIGHT:
// ============== copyright 2000 dynamicsoft Inc. =================
// ==================== all rights reserved =======================
//
// MODIFICATIONS:
//
//
//////////////////////////////////////////////////////////////////////////////
package com.cisco.dsb.trunk.loadbalancer;

import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.transport.Transport;

/**
 * This interface defines all the methods needed to get the actual next hop destination and update
 * the status of the attempt. This interface was created so that applications wouldn't have access
 * to the <code>ServerGroupElement</code> methods that directly modify the status, statistics, and
 * information of that object.
 */
public interface ServerInterface {
  /**
   * Gets the network name
   *
   * @return the network name
   */
  public String getNetwork();

  /**
   * Gets the host name.
   *
   * @return the host name.
   */
  public String getDomainName();

  /**
   * Gets the port.
   *
   * @return the port.
   */
  public int getPort();

  /**
   * Gets the transport type.
   *
   * @return the transport protocol.
   */
  public Transport getProtocol();

  /**
   * Gets the end point associated with this server
   *
   * @return EndPoint end point
   */
  public EndPoint getEndPoint();

  public boolean isAvailable();
}
