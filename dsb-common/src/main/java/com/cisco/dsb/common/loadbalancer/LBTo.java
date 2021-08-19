/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
package com.cisco.dsb.common.loadbalancer;

/**
 * Created by IntelliJ IDEA. User: rrachumallu Date: Aug 20, 2003 Time: 1:31:31 PM To change this
 * template use Options | File Templates.
 */
public final class LBTo extends LBHashBased {

  public LBTo() {}

  protected void setKey() {
    try {
      key = request.getSIPMessage().getTo().getName();
    } catch (Exception e) {

    }
  }
}
