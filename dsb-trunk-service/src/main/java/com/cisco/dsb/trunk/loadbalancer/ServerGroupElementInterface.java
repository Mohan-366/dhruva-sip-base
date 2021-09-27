package com.cisco.dsb.trunk.loadbalancer;

/** This interface defines all the methods that a server group element needs to implement. */
public interface ServerGroupElementInterface extends Comparable {

  /**
   * Sets the q-value.
   *
   * @param qValue the q-value
   */
  public void setQValue(float qValue);

  /**
   * Gets the q-value of this object.
   *
   * @return the q-value of this object.
   */
  public float getQValue();

  /**
   * sets the weight of this element
   *
   * @param weight weight in integer
   */
  public void setWeight(int weight);

  /**
   * gets the weight of this element
   *
   * @return weight
   */
  public int getWeight();

  /**
   * Determines if this object is an instance of a <code>NextHop</code> or a <code>
   * ServerGroupPlaceholder</code>. Used to cut down on the expensive <code>instanceof</code> calls.
   *
   * @return <code>true</code> if the instance of this object is a <code>NextHop</code>.
   */
  public boolean isNextHop();

  /**
   * Determines if this object is the same logical (not physical) reference to the passed object.
   *
   * @return <code>true</code> if the given object is the same logical reference to this object.
   */
  public boolean isSameReferenceTo(ServerGroupElementInterface element);

  /**
   * Gets the <code>String</code> representation of this object.
   *
   * @return the <code>String</code> representation of this object.
   */
  public String toString();

  /**
   * Compares this object to the object passed as an argument.
   *
   * @return
   *     <p>A negative integer if this object should appear before the given object in a sorted
   *     list, a positive integer if this object should appear after the given object in a sorted
   *     list, or <code>0</code> if this object is equal to the given object
   * @throws ClassCastException
   */
  public int compareTo(Object obj) throws ClassCastException;
}
