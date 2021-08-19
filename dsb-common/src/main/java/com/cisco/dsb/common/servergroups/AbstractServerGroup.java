/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
// FILENAME: $RCSfile: AbstractServerGroup.java,v $
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
package com.cisco.dsb.common.servergroups;

import com.cisco.dsb.common.loadbalancer.LBFactory;
import com.cisco.dsb.common.loadbalancer.ServerGroupInterface;
import com.cisco.dsb.common.sip.util.EndPoint;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/**
 * This class servers as a superclass for all types of server groups. Because it is a
 * general-purpose class, it defines a number of methods for creating new instances, accessing data
 * members, adding and removing ServerGroupElements, and creating a copy of an instance while
 * preserving the internal reference to some data members.
 *
 * @see ServerGroupInterface
 */
public abstract class AbstractServerGroup implements ServerGroupInterface {

  protected String name = null;
  protected String network = null;
  protected TreeSet elements;
  protected int lbType;
  protected boolean pingOn = false;
  protected String lbStrType = null;

  /**
   * Gets the name of this <code>AbstractServerGroup</code>
   *
   * @return the name of this <code>AbstractServerGroup</code>
   */
  public final String getName() {
    return name;
  }

  /**
   * Gets the network of this <code>AbstractServerGroup</code>
   *
   * @return the network of this <code>AbstractServerGroup</code>
   */
  public String getNetwork() {
    return network;
  }

  public void setNetwork(String network) {
    this.network = network;
  }

  /**
   * Constructs a new <code>AbstractServerGroup</code> with the given name and the given <code>
   * TreeSet</code> full of <code>ServerGroupElement</code> objects.
   *
   * @param name the name of this <code>AbstractServerGroup</code>
   * @param elements a <code>ArrayList</code> of <code>ServerGroupElement</code>s.
   * @param lbType the type of load balancing for this server group.
   */
  protected AbstractServerGroup(
      String name, String network, TreeSet elements, int lbType, boolean pingOn) {
    this.name = name;
    this.network = network;
    this.elements = elements;
    this.lbType = lbType;
    this.pingOn = pingOn;
    this.lbStrType = null;
    if (elements == null) this.elements = new TreeSet();
  }

  /**
   * Constructs a new <code>AbstractServerGroup</code> with the given name and the given <code>
   * TreeSet</code> full of <code>ServerGroupElement</code> objects.
   *
   * @param name the name of this <code>AbstractServerGroup</code>
   * @param elements a <code>ArrayList</code> of <code>ServerGroupElement</code>s.
   * @param lbType fully qualified class name of the type of load balancing for this server group.
   */
  protected AbstractServerGroup(
      String name, String network, TreeSet elements, String lbType, boolean pingOn) {
    this(name, network, elements, LBFactory.CUSTOM, pingOn);
    this.lbStrType = lbType;
  }

  /**
   * Gets the next hop corresponding to the given <code>EndPoint</code>
   *
   * @param endPoint the host, port, and transport-type tuple
   * @return the <code>ServerGroupElement</code> (<code>NextHop</code>) corresponding to the given
   *     <code>EndPoint</code>
   */
  public final ServerGroupElement get(EndPoint endPoint) {
    for (Iterator i = elements.iterator(); i.hasNext(); ) {
      ServerGroupElement obj = (ServerGroupElement) i.next();
      if (obj.isNextHop()) {
        AbstractNextHop nh = (AbstractNextHop) obj;
        if (nh.getEndPoint().equals(endPoint)) return nh;
      } else continue;
    }
    return null;
  }

  /**
   * Gets the server group placeholder corresponding to the given server group name
   *
   * @param serverGroupName the sub-server group name
   * @return the <code>ServerGroupElement</code> (<code>ServerGroupPlaceholder</code>) corresponding
   *     to the given server group name
   */
  public final ServerGroupElement get(String serverGroupName) {
    for (Iterator i = elements.iterator(); i.hasNext(); ) {
      Object obj = i.next();
      try {
        AbstractServerGroupPlaceholder sgp = (AbstractServerGroupPlaceholder) obj;
        if (sgp.getServerGroupName().equals(serverGroupName)) return sgp;
      } catch (ClassCastException cce) {
        continue;
      }
    }
    return null;
  }

  /**
   * Determines whether or not this AbstractServerGroup contains the given element.
   *
   * @param element the server group element.
   * @return <code>true</code> if this server group contains the given element. <code>false</code>
   *     otherwise.
   */
  public final boolean contains(ServerGroupElement element) {
    for (Iterator i = elements.iterator(); i.hasNext(); ) {
      ServerGroupElement sge = (ServerGroupElement) i.next();
      if (element.isSameReferenceTo(sge)) return true;
    }
    return false;
  }

  /**
   * Determines whether or not this AbstractServerGroup (or one of its sub-groups) contains a
   * reference to the given server group name.
   *
   * @param serverGroupName the name of the server group to look for.
   * @param serverGroupRepository the list of all server groups.
   * @return <code>true</code> if this server group (or one of its sub-groups) contains a reference
   *     to the given server group name.
   */
  public final boolean containsReferenceTo(String serverGroupName, HashMap serverGroupRepository) {
    for (Iterator i = elements.iterator(); i.hasNext(); ) {
      ServerGroupElement sge = (ServerGroupElement) i.next();
      if (sge.isNextHop()) continue;
      AbstractServerGroupPlaceholder sgp = (AbstractServerGroupPlaceholder) sge;
      if (sgp.getServerGroupName().equals(serverGroupName)) return true;
      AbstractServerGroup sg =
          (AbstractServerGroup) serverGroupRepository.get(sgp.getServerGroupName());
      if (sg.containsReferenceTo(serverGroupName, serverGroupRepository)) return true;
    }
    return false;
  }

  /**
   * Removes the given element from the server group.
   *
   * @param element the server group element to remove.
   */
  protected boolean remove(ServerGroupElement element) {
    return elements.remove(element);
  }

  /**
   * Removes the given element from the server group
   *
   * @param element the server group element.
   * @return <code>true</code> if the operation was successful, <code>false</code> otherwise.
   */
  protected ServerGroupElement removeSameReferenceTo(ServerGroupElement element) {
    for (Iterator i = elements.iterator(); i.hasNext(); ) {
      ServerGroupElement sge = (ServerGroupElement) i.next();
      if (sge.isSameReferenceTo(element)) {
        remove(sge);
        return sge;
      }
    }
    return null;
  }

  /**
   * Adds the specified <code>ServerGroupElement</code> to this <code>AbstractServerGroup</code>.
   *
   * @param element the <code>ServerGroupElement</code> to add.
   */
  protected boolean addElement(ServerGroupElement element) {
    return elements.add(element);
  }

  protected Set removeAll() {
    Set s = null;
    if (!elements.isEmpty()) {
      s = elements;
      elements = new TreeSet();
    }
    return s;
  }

  /**
   * Sets the load balancing type of this server group.
   *
   * @param lbType the load balancing type.
   */
  public final void setLBType(int lbType) {
    this.lbType = lbType;
  }

  /**
   * Sets the load balancing type of this server group.
   *
   * @param lbType the load balancing type.
   */
  public final void setLBType(String lbType) {
    this.lbType = LBFactory.CUSTOM;
    this.lbStrType = lbType;
  }

  /**
   * Gets the number of elements in the server group.
   *
   * @return the number of elements in the server group.
   */
  public final int size() {
    return elements.size();
  }

  /**
   * Overrides Object
   *
   * @return A String representation of this object
   */
  public abstract String toString();

  /**
   * Gives the availability of this server group, in this case, whether or not the size of the
   * server group is greater than zero. Subclasses should override this method if they have some
   * other definition of whether or not the server group is available.
   *
   * @return <code>true</code> if the size of the server group is greater than zero, <code>false
   *     </code> otherwise.
   */
  public boolean isAvailable() {
    return (size() > 0);
  }

  /**
   * *************************************** ServerGroupInterface methods *
   * ***************************************
   */

  /**
   * Gets the load balancing type of this server group.
   *
   * @return the load balancing type of this server group.
   */
  public final int getLBType() {
    return lbType;
  }

  /**
   * Gets the load balancing type of this server group.
   *
   * @return the load balancing type of this server group.
   */
  public final String getStrLBType() {
    return lbStrType;
  }

  /**
   * Gets all the <code>ServerGroupElement</code>s in this <code>AbstractServerGroup</code>. This is
   * the orginal list: modification to the list will modify the original list itself.
   *
   * @return a TreeSet of <code>ServerGroupElement</code>.
   */
  public final TreeSet getElements() {
    return elements;
  }
}
