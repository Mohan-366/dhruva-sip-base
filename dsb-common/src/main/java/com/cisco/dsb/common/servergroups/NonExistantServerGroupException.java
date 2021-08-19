/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
// FILENAME:	$RCSFile$
//
// MODULE:	servergroups
//
// $Id: NonExistantServerGroupException.java,v 1.1.1.1.16.1 2005/11/21 01:45:26 lthamman Exp $
//
// COPYRIGHT:
// ============== copyright 2000 dynamicsoft Inc. =================
// ==================== all rights reserved =======================
// /////////////////////////////////////////////////////////////////
package com.cisco.dsb.common.servergroups;

/** Thrown when attempting to modify a server group that does not exist. */
public class NonExistantServerGroupException extends Exception {

  /**
   * Creates a new NonExistantServerGroupException with the given error message.
   *
   * @param error the error message.
   */
  public NonExistantServerGroupException(String error) {
    super(error);
  }
}
