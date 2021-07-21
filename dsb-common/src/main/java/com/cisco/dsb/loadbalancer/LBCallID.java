/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
package com.cisco.dsb.loadbalancer;

public final class LBCallID extends LBHashBased {

  public LBCallID() {}

  protected void setKey() {
    key = request.getCallId();
  }
}
