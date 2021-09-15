/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
/*
 * LBHashBasedVariableKey.java
 *
 * Created on June 30, 2005, 2:37 PM
 */

package com.cisco.dsb.trunk.loadbalancer;

/**
 * LoadBalancer that selects an available server based on key (string) passed.
 *
 * @author Selva Subramanian
 */
public class LBHashBasedVariableKey extends LBHashBased {

  /** Creates a new instance of LBHashBasedVariableKey */
  public LBHashBasedVariableKey() {}

  protected void setKey() {
    key = "void";
  }
}
