/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
package com.cisco.dsb.trunk.loadbalancer;

import com.cisco.dsb.common.messaging.models.AbstractSipRequest;

/**
 * This class defines all the methods that must be implemented by a load balancer That are exposed
 * to the LBFactory.
 *
 * @see LBFactory
 */
public interface RepositoryReceiverInterface extends LBInterface {

  void setServerInfo(
      String serverGroupName, ServerGroupInterface serverGroup, AbstractSipRequest request);
}
