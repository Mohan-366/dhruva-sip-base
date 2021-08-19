/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
// FILENAME: $RCSFile$
//
// MODULE: servergroups
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

/**
 * Thrown when a duplicate server group is detected when adding a server group to the repository.
 */
public class DuplicateServerGroupException extends Exception {

  /**
   * Creates a new DuplicateServerGroupException with the given error message.
   *
   * @param error the error message.
   */
  public DuplicateServerGroupException(String error) {
    super(error);
  }
}
