/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
// FILENAME: $RCSfile: LBHighestQ.java,v $
//
// MODULE:  loadbalancer
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

/**
 * <p>This class implements the Hash-Based load balancer.<br>
 * The element with the highest q-value is selected as the next hop.
 * If multiple elements have the same highest q-value, the first element in
 * the list is always chosen.
 * If the chosen element is a <code>ServerGroupPlaceholder</code>, a new
 * <code>LBInterface is internally created for that sub server group and the process
 * is recursively repeated until a <code>NextHop</code> is chosen.
 */
public final class LBHighestQ extends LBBase {

  public LBHighestQ() {}

  protected ServerGroupElementInterface selectElement() {
    return (ServerGroupElementInterface) domainsToTry.first();
  }

  protected void setKey() {
    key = request.getReqURI();
  }
}
