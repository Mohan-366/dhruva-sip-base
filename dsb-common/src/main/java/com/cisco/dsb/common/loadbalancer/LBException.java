/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
// FILENAME: $RCSfile: LBException.java,v $
//
// MODULE:  lb
//
// COPYRIGHT:
// ============== copyright 2000 dynamicsoft Inc. =================
// ==================== all rights reserved =======================
//
// MODIFICATIONS:
//
//
//////////////////////////////////////////////////////////////////////////////
package com.cisco.dsb.common.loadbalancer;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * This exception is thrown when there is a problem creating a load balancer in the <code>LBFactory
 * </code>
 *
 * @see LBFactory
 */
public class LBException extends Exception {

  private Throwable exception = null;

  /**
   * Instantiates a new LBException object with the error message specified.
   *
   * @param error A string indicating the error which caused this exception.
   */
  public LBException(String error) {
    super(error);
  }

  /**
   * Instantiates a new LBException object with the error message specified.
   *
   * @param //error A string indicating the error which caused this exception.
   * @param e A <code>Throwable</code> object that has been caught and rethrown as an <code>
   *     LBException</code>.
   */
  public LBException(String message, Throwable e) {
    super(message);
    this.exception = e;
  }

  public void printStackTrace() {
    // if (exception != null) exception.printStackTrace();
    // else super.printStackTrace();
  }

  public void printStackTrace(PrintStream s) {
    //  if (exception != null) exception.printStackTrace(s);
    //  else super.printStackTrace(s);
  }

  public void printStackTrace(PrintWriter s) {
    // if (exception != null) exception.printStackTrace(s);
    // else super.printStackTrace(s);
  }
}
