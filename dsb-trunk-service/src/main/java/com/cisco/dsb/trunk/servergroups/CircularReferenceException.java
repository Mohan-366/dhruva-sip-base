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
package com.cisco.dsb.trunk.servergroups;

/**
 * Thrown when a circular reference is detected when adding a server group element to the
 * repository.
 */
public class CircularReferenceException extends Exception {

  /**
   * Creates a new CircularReferenceException with the given error message.
   *
   * @param error the error message.
   */
  public CircularReferenceException(String error) {
    super(error);
  }
}
