/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
// FILENAME: $RCSfile: AbstractNextHop.java,v $
//
// MODULE:  servergroups
//
// COPYRIGHT:
// ============== copyright 2000 dynamicsoft Inc. =================
// ==================== all rights reserved =======================
//
// MODIFICATIONS:
//
//
//////////////////////////////////////////////////////////////////////////////
package com.cisco.dsb.common.servergroups;

import com.cisco.dsb.common.loadbalancer.ServerGroupElementInterface;
import com.cisco.dsb.common.loadbalancer.ServerInterface;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.transport.Transport;
import java.util.StringTokenizer;

/**
 * This class servers as a superclass for <code>Next Hop</code> server group elements. Because it is
 * a general-purpose class, it defines a number of methods for creating new instances, accessing
 * data members, comparing two instances, and creating a copy of an instance while preserving the
 * internal reference to some data members.
 *
 * @see ServerGroupElement
 * @see ServerInterface
 * @see// com.dynamicsoft.DsLibs.DsUtil.EndPoint
 * @see ServerGlobalStateWrapper
 */
public abstract class AbstractNextHop extends ServerGroupElement
    implements Comparable, ServerInterface {

  private ServerGlobalStateWrapper globalWrapper = null;
  private EndPoint endpoint;

  /**
   * Constructor
   *
   * @param host The Host name of this end point/address.
   * @param port The port number.
   * @param protocol The int representing the protocol.
   * @param qValue the priority of this endpoint
   */
  protected AbstractNextHop(
      String network,
      String host,
      int port,
      Transport protocol,
      float qValue,
      String serverGroup,
      Boolean dnsServerGroup) {
    this.isNextHop = true;
    endpoint = new EndPoint(network, host, port, protocol, serverGroup);

    this.setQValue(qValue);
    setParent(serverGroup);
  }

  /**
   * Gets the destination this <code>NextHop</code> is keeping track of.
   *
   * @return the <code>EndPoint</code>
   */
  public final EndPoint getEndPoint() {
    return this.endpoint;
  }

  /**
   * Gets the object containing the globally available state for this endpoint.
   *
   * @return the <code>GlobalStateWrapper</code> containing the globally available state for this
   *     endpoint.
   * @see ServerGlobalStateWrapper
   */
  public final ServerGlobalStateWrapper getGlobalWrapper() {
    return globalWrapper;
  }

  /**
   * Determines if the given object has the same values for <code>EndPoint</code> and <code>qValue
   * </code> as this object.
   *
   * @param obj the object to compare.
   * @return <code>true</code> if this object is equal to the given object.
   */
  public boolean equals(Object obj) {
    if (obj == null) return false;
    try {
      AbstractNextHop nh = (AbstractNextHop) obj;
      if (Float.compare(getQValue(), nh.getQValue()) == 0)
        if (getEndPoint().equals(nh.getEndPoint())) return true;
    } catch (ClassCastException cce) {
    }
    return false;
  }

  /**
   * Overrides Object
   *
   * @return A String representation of this object
   */
  public abstract String toString();

  /**
   * Compares two hosts, whether hostname, IP address, or mixed.
   *
   * @param domain1 this objects domain.
   * @param domain2 another object's domain.
   * @return a positive integer if this endpoint should come after the given object the supplied
   *     object in a sorted list, a negative integer if this endpoint should come before the given
   *     object in a sorted list, or zero if the two objects are equivalent.
   */
  private int compareDomainNames(String domain1, String domain2) {
    int compare = 0;
    if (!domain1.equals(domain2)) {

      StringTokenizer st1 = new StringTokenizer(domain1.toString(), ".");
      StringTokenizer st2 = new StringTokenizer(domain2.toString(), ".");

      String[] list1 = new String[st1.countTokens()];
      String[] list2 = new String[st2.countTokens()];
      int i = 0;
      while (st1.hasMoreTokens()) {
        list1[i] = st1.nextToken();
        i++;
      }
      i = 0;
      while (st2.hasMoreTokens()) {
        list2[i] = st2.nextToken();
        i++;
      }
      if (list1.length == list2.length) {
        try {
          for (i = 0; i < list1.length; i++) {
            int a = Integer.parseInt(list1[i]);
            int b = Integer.parseInt(list2[i]);
            if (a > b) {
              compare = 1;
              break;
            } else if (b > a) {
              compare = -1;
              break;
            }
          }
        } catch (NumberFormatException nfe) {
          compare = doStringDomainCompare(list1, list2);
        }
      } else {
        compare = doStringDomainCompare(list1, list2);
      }
    }
    return compare;
  }

  private int doStringDomainCompare(String[] list1, String[] list2) {

    int compare = 0;
    int i = Math.min(list1.length, list2.length) - 1;
    for (; i >= 0; i--) {
      compare = list1[i].compareTo(list2[i]);
      if (compare != 0) return compare;
    }
    if (list1.length < list2.length) return -1;
    return 1;
  }

  /**
   * ******************************************************** ServerGroupElement methods *
   * ********************************************************
   */

  /**
   * Compares this next hop to the given object.
   *
   * @return a positive integer if this endpoint should come after the given object the supplied
   *     object in a sorted list, a negative integer if this endpoint should come before the given
   *     object in a sorted list, or zero if the two objects are equivalent.
   * @param obj the object to compare.
   * @throws ClassCastException
   */
  public final int compareTo(Object obj) throws ClassCastException {
    int compare = super.compareTo(obj);
    if (compare != 0) return compare;
    ServerGroupElement e = (ServerGroupElement) obj;
    if (!e.isNextHop()) return 1;
    AbstractNextHop nh = (AbstractNextHop) e;
    compare = compareDomainNames(getDomainName(), nh.getDomainName());
    if (compare != 0) return compare;
    if (getPort() < nh.getPort()) return 1;
    else if (getPort() > nh.getPort()) return -1;
    else if (getProtocol().getValue() < nh.getProtocol().getValue()) return 1;
    else if (getProtocol().getValue() > nh.getProtocol().getValue()) return -1;
    return 0;
  }

  /**
   * Determines if the given <code>ServerGroupElementInterface</code> has the same host, port, and
   * transport type as this next hop.
   *
   * @param element the <code>ServerGroupElementInterface</code> to compare.
   * @return <code>true</code> if the given element has the same <code>EndPoint</code> values,
   *     <code>false</code> otherwise.
   */
  public final boolean isSameReferenceTo(ServerGroupElementInterface element) {
    if (element.isNextHop()) {
      AbstractNextHop nh = (AbstractNextHop) element;
      return nh.getEndPoint().equals(globalWrapper);
    } else return false;
  }

  /**
   * ******************************************************** ServerInterface methods *
   * ********************************************************
   */

  /**
   * Gets the network for this endpoint.
   *
   * @return the network for this endpoint.
   */
  public final String getNetwork() {
    return endpoint.getNetwork();
  }

  /**
   * Gets the host for this endpoint.
   *
   * @return the host for this endpoint.
   */
  public final String getDomainName() {
    return endpoint.getHost();
  }

  /**
   * Gets the port for this endpoint.
   *
   * @return the port for this endpoint.
   */
  public final int getPort() {

    return endpoint.getPort();
  }

  /**
   * Gets the transport type for this endpoint.
   *
   * @return the transport protocol for this endpoint.
   */
  public final Transport getProtocol() {
    return endpoint.getProtocol();
  }
}
