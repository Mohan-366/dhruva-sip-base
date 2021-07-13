/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
// FILENAME: $RCSfile: LBBase.java,v $
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
package com.cisco.dsb.loadbalancer;

import com.cisco.dsb.common.messaging.models.AbstractSipRequest;
import com.cisco.dsb.servergroups.ServerGroupElement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;

/**
 * This class is the base class for <code>LBHashBased</code> and <code>LBHighestQ</code>.
 *
 * @see LBHashBased
 * @see LBHighestQ
 * @see RepositoryReceiverInterface
 */
public abstract class LBBase implements RepositoryReceiverInterface {

  protected ServerInterface lastTried;

  protected String key;
  protected ServerGroupInterface serverGroup;
  protected String serverGroupName;
  protected TreeSet domainsToTry;
  protected HashMap lb;
  protected AbstractSipRequest request;

  /**
   * This method performs the appropriate load balancing algorithm to determine the next hop.
   * Successive calls to this method during the same transaction should return another potential
   * next hop, but SHOULD NOT consider <code>ServerGroupElement</code>s which have already been
   * attempted as valid next hops.
   *
   * @return the <code>ServerInterface</code> that is the next best hop.
   */
  public ServerInterface getServer() {

    lastTried = pickServer(null);
    return lastTried;
  }

  /**
   * This method performs the appropriate load balancing algorithm to determine the next hop, using
   * the passed in key (varKey), to perform the hashing.
   */
  public ServerInterface getServer(String varKey) {

    lastTried = pickServer(varKey);
    return lastTried;
  }

  /**
   * Gets the last server that was tried.
   *
   * @return the last server tried.
   */
  public final ServerInterface getLastServerTried() {
    return lastTried;
  }

  /**
   * Gives the load balancer all the information necessary to pick the next hop.
   *
   * @param serverGroupName the name of the server group that this load balancer is created for.
   * @param serverGroup the entire server group repository.
   * @param request the request used for the hash algorithm.
   */
  public final void setServerInfo(
      String serverGroupName, ServerGroupInterface serverGroup, AbstractSipRequest request) {
    this.serverGroupName = serverGroupName;
    this.serverGroup = serverGroup;
    this.request = request;
    setKey();
  }

  protected abstract void setKey();

  public ServerInterface pickServer(String varKey) {

    lastTried = null;

    if (domainsToTry == null) initializeDomains();
    if (domainsToTry.size() == 0) {
      //   if (Log.on && Log.isEnabled(Level.WARN)) Log.warn("No more routes remain");
      return null;
    }

    ServerGroupElementInterface selectedElement = selectElement(varKey);
    boolean isMyNextHop = true;

    if (selectedElement == null) {
      domainsToTry.clear();
    } else if (isMyNextHop) {
      domainsToTry.remove(selectedElement);

      lastTried = (ServerInterface) selectedElement;
    }
    return lastTried;
  }

  private final void initializeDomains() {
    ServerGroupInterface serverGroup = this.serverGroup;
    domainsToTry = new TreeSet();
    if (serverGroup == null) {

      return;
    }
    for (Iterator i = serverGroup.getElements().iterator(); i.hasNext(); ) {
      ServerGroupElementInterface sge = (ServerGroupElementInterface) i.next();
      if (sge.isNextHop()) {
        ServerInterface nh = (ServerInterface) sge;
        if (nh.isAvailable()) domainsToTry.add(sge);
      } else {
        if (((ServerGroupElement) sge).isAvailable()) {
          domainsToTry.add(sge);
        }
      }
    }
  }

  /**
   * Gets the number of remaining elements that can be routed to.
   *
   * @return the number of remaining elements that can be routed to.
   */
  public final int getNumberOfUntriedElements() {
    if (domainsToTry == null) return 0;
    else return domainsToTry.size();
  }

  public TreeSet getDomainsToTry() {
    return domainsToTry;
  }

  public void setDomainsToTry(TreeSet domainsToTry) {
    this.domainsToTry = domainsToTry;
  }

  /**
   * Selects an element from the list of available servers. Should be overridded by subclasses.
   *
   * @return a server group element
   */
  protected abstract ServerGroupElementInterface selectElement();

  protected ServerGroupElementInterface selectElement(String varKey) {
    return selectElement();
  }
}
