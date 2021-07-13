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
package com.cisco.dsb.servergroups;

/**
 * Thrown when a duplicate server group entry is detected when adding a server group element to the
 * repository.
 */
public class DuplicateServerGroupEntryException extends Exception {

  /**
   * Creates a new DuplicateServerGroupEntryException with the given error message.
   *
   * @param error the error message.
   */
  public DuplicateServerGroupEntryException(String error) {
    super(error);
  }
}
