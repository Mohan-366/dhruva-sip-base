/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
package com.cisco.dsb.loadbalancer;

import com.cisco.dsb.servergroups.AbstractServerGroup;
import java.util.HashMap;

/** An interface that can be used by any class which wants to store a server group repository. */
@FunctionalInterface
public interface LBRepositoryHolder {

  /** Retrieve a hashmap containing the current server group repository. */
  HashMap<String, AbstractServerGroup> getServerGroups();
}
