/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
/*
 * Created by IntelliJ IDEA.
 * User: bjenkins
 * Date: Aug 28, 2002
 * Time: 9:42:41 AM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.cisco.dsb.servergroups;

import java.util.HashMap;

public class DefaultServerGroupPlaceholder extends AbstractServerGroupPlaceholder {

  private DefaultServerGroup myServerGroup = null;

  /**
   * Creates a new ServerGroupPlaceholder with the given name and q-value. This constructor must
   * always be used when adding a DefaultServerGroupPlaceholder to a DefaultServerGroupRepository
   *
   * @param myServerGroup the server group this placeholder is wrapping
   * @param qValue the q-value of this server group element.
   * @param parentGroupName the name of the server group this server group is being added to.
   */
  public DefaultServerGroupPlaceholder(
      DefaultServerGroup myServerGroup, float qValue, String parentGroupName) {
    super(myServerGroup.getName(), qValue, parentGroupName);
    this.myServerGroup = myServerGroup;
  }

  public boolean isAvailable() {
    if (myServerGroup != null) return myServerGroup.isAvailable();
    return false;
  }

  public String toString() {
    String value = null;
    HashMap elementMap = new HashMap();
    elementMap.put(SG.sgSgElementSgName, parent);
    elementMap.put(SG.sgSgElementSgReference, getServerGroupName());
    elementMap.put(SG.sgSgElementQValue, String.valueOf(getQValue()));
    elementMap.put(SG.sgSgElementWeight, String.valueOf(getWeight()));

    // MIGRATION
    value = elementMap.toString();

    return value;
  }

  public Object clone() {
    return new DefaultServerGroupPlaceholder(
        this.myServerGroup, this.getQValue(), this.getParent());
  }
}
