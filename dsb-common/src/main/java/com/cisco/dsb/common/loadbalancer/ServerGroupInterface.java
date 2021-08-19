/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
package com.cisco.dsb.common.loadbalancer;

import java.util.TreeSet;

/**
 * This interface defines all the methods that the LBFactory needs to create a load balancer for the
 * server group, and that the load balancer needs to perform load balancing on the server group.
 *
 * @see LBFactory
 */
public interface ServerGroupInterface {

  /**
   * Gets all the <code>ServerGroupElement</code>s in this <code>ServerGroup</code>. This is the
   * orginal list: modification to the list will modify the original list itself.
   *
   * @return a TreeSet of <code>ServerGroupElement</code>.
   */
  public TreeSet getElements();

  /**
   * Gets the load balancing type of this server group.
   *
   * @return the load balancing type of this server group.
   */
  public int getLBType();

  /**
   * Gets the load balancing type of this server group.
   *
   * @return the load balancing type of this server group.
   */
  public String getStrLBType();
}
