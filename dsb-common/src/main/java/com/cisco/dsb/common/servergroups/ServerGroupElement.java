/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
// FILENAME: $RCSfile: ServerGroupElement.java,v $
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
package com.cisco.dsb.common.servergroups;

import com.cisco.dsb.common.loadbalancer.ServerGroupElementInterface;

/**
 * This class is an abstract data object that is used with
 * the <code>ServerGroup</class>
 */
public abstract class ServerGroupElement implements ServerGroupElementInterface, Cloneable {

  public static final float DEFAULT_Q = 1.0f;

  private float qValue = DEFAULT_Q;

  private int weight = -1;

  protected boolean isNextHop = false;
  protected String parent = null;

  /**
   * Sets the qvalue of this element.
   *
   * @param qValue the q-value.
   */
  public final void setQValue(float qValue) {
    if ((qValue >= 0) && (qValue <= 1)) this.qValue = qValue;
  }

  /**
   * ********************************************** ServerGroupElementInterface methods *
   * **********************************************
   */

  /**
   * Gets the q-value of this object.
   *
   * @return the q-value of this object.
   */
  public final float getQValue() {
    return qValue;
  }

  /**
   * Sets the weight of this element.
   *
   * @param weight the q-value.
   */
  public final void setWeight(int weight) {
    if (weight >= 0) this.weight = weight;
  }

  /**
   * ********************************************** ServerGroupElementInterface methods *
   * **********************************************
   */
  /**
   * Gets the q-value of this object.
   *
   * @return the q-value of this object.
   */
  public final int getWeight() {
    return weight;
  }

  /** @return <code>true</code> if the instance of this object is a <code>NextHop</code>. */
  public final boolean isNextHop() {
    return isNextHop;
  }

  /** @return <code>true</code> if the given object is the same logical reference to this object. */
  public abstract boolean isSameReferenceTo(ServerGroupElementInterface sge);

  /**
   * Compares this object to the object passed as an argument. If this object has a higher q-value,
   * the operation returns a negative integer. If this object has a lower q-value, the operation
   * returns a positive integer. If this object has the same q-value, the operation returns 0.
   *
   * @return
   *     <p>A negative integer if this object has a higher q-value, a positive integer if this
   *     object has a lower q-value, or <code>0</code> if this object has the same q-value.
   * @throws ClassCastException
   */
  public int compareTo(Object obj) throws ClassCastException {
    ServerGroupElement sge = (ServerGroupElement) obj;
    if (getQValue() < sge.getQValue()) return 1;
    if (getQValue() > sge.getQValue()) return -1;
    else return 0;
  }

  public abstract boolean isAvailable();

  public abstract String toString();

  public final void setParent(String parent) {
    this.parent = parent;
  }

  public final String getParent() {
    return parent;
  }

  public abstract Object clone();
}
