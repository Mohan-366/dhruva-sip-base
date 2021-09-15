/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
/*
 * User: bjenkins
 * Date: Mar 25, 2002
 * Time: 1:40:37 PM
 */
package com.cisco.dsb.trunk.servergroups;

import com.cisco.dsb.common.transport.Transport;
import java.util.HashMap;

/**
 * This class implements a Next Hop with the capability to notify listeners when the state of the
 * Next Hop changes. It is assumed that instances of this class are elements of DefaultServerGroup
 * objects
 *
 * @see DefaultServerGroup
 */
public class DnsNextHop extends AbstractNextHop {

  public DnsNextHop(
      String network,
      String hostname,
      int port,
      Transport transport,
      float qValue,
      String serverGroup) {
    super(network, hostname, port, transport, qValue, serverGroup, true);
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  /**
   * Overrides Object
   *
   * @return the DefaultNextHop in CLI command format
   */
  public String toString() {
    String value = null;
    HashMap elementMap = new HashMap();
    elementMap.put(SG.sgSgElementSgName, parent);
    elementMap.put(SG.sgSgElementHost, getDomainName());
    elementMap.put(SG.sgSgElementPort, getPort());
    elementMap.put(SG.sgSgElementTransport, getProtocol());
    elementMap.put(SG.sgSgElementQValue, String.valueOf(getQValue()));
    elementMap.put(SG.sgSgElementWeight, String.valueOf(getWeight()));

    // MIGRATION
    value = elementMap.toString();

    return value;
  }

  public Object clone() {
    return new DnsNextHop(
        this.getGlobalWrapper().getNetwork(),
        this.getDomainName(),
        this.getPort(),
        this.getProtocol(),
        this.getQValue(),
        this.getParent());
  }
}
