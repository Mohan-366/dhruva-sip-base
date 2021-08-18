/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
/*
 * User: bjenkins
 * Date: Mar 25, 2002
 * Time: 1:12:55 PM
 */
package com.cisco.dsb.servergroups;

import java.util.*;

/**
 * This class implements a Server Group with the capability to notify listeners when the state of
 * the Server Group changes. It is assumed that instances of this class are elements of
 * DefaultServerGroupRepository objects and contain DefaultNextHop objects.
 *
 * @see //DefaultServerGroupRepository
 */
public class DefaultServerGroup extends AbstractServerGroup {

  protected HashSet listeners = null;
  private int unreachable = 0;
  private int overloaded = 0;

  /**
   * Constructs a new <code>AbstractServerGroup</code> with the given name and the given <code>
   * TreeSet</code> full of <code>ServerGroupElement</code> objects.
   *
   * @param name the name of this <code>AbstractServerGroup</code>
   * @param elements a <code>ArrayList</code> of <code>ServerGroupElement</code>s.
   * @param lbType the type of load balancing for this server group.
   */
  protected DefaultServerGroup(
      String name, String network, TreeSet elements, int lbType, boolean pingOn) {

    super(name, network, elements, lbType, pingOn);
  }

  protected boolean addElement(ServerGroupElement element) {
    return super.addElement(element);
  }

  /**
   * Overrides Object
   *
   * @return A String representation of this object
   */
  public String toString() {
    String value = null;
    HashMap elementMap = new HashMap();
    elementMap.put(SG.sgSgName, name);
    // elementMap.put(SG.sgSgLbType, LBFactory.getLBTypeAsString(lbType));
    // MIGRATION
    value = elementMap.toString();

    return value;
  }

  /**
   * **************************************************** Methods overridding AbstractServerGroup
   * ****************************************************
   */

  /**
   * Removes the given element from the server group.
   *
   * @param element the server group element to remove.
   */
  protected boolean remove(ServerGroupElement element) {
    boolean b = super.remove(element);

    return b;
  }

  protected Set removeAll() {
    Set s = super.removeAll();

    return s;
  }

  /**
   * Gives the availability of this server group, in this case, whether or not the number of down
   * elements is less than the size of the server group. Subclasses should override this method if
   * they have some other definition of whether or not the server group is available.
   *
   * @return <code>true</code> if the size of the server group is greater than zero, <code>false
   *     </code> otherwise.
   */
  public boolean isAvailable() {
    boolean success = true;
    if (size() == 0) success = false;
    else {
      if ((unreachable + overloaded) >= size()) {
        for (Iterator i = elements.iterator(); i.hasNext(); ) {
          ServerGroupElement sge = (ServerGroupElement) i.next();
          success = sge.isAvailable();
          if (success) break;
        }
      }
    }
    return success;
  }
}
