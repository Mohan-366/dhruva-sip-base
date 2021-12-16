package com.cisco.dsb.common.loadbalancer;

public interface LBElement extends Comparable {

  /**
   * Sets priority, the lowest value will have the highest preference
   *
   * @param priority
   */
  void setPriority(int priority);

  /**
   * Gets priority of this object
   *
   * @return the priority of this object.
   */
  int getPriority();

  /**
   * sets the weight of this element
   *
   * @param weight weight in integer
   */
  void setWeight(int weight);

  /**
   * gets the weight of this element
   *
   * @return weight
   */
  int getWeight();

  /**
   * Gets the <code>String</code> representation of this object.
   *
   * @return the <code>String</code> representation of this object.
   */
  String toString();

  /**
   * Compares this object to the object passed as an argument.
   *
   * @return
   *     <p>A negative integer if this object should appear before the given object in a sorted
   *     list, a positive integer if this object should appear after the given object in a sorted
   *     list, or <code>0</code> if this object is equal to the given object
   * @throws ClassCastException
   */
  int compareTo(Object obj) throws ClassCastException;
}
