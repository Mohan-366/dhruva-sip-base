package com.cisco.dsb.trunk.loadbalancer;

// import com.cisco.dhruva.sip.libs.String;

/**
 * This interface defines all the methods that are needed to perform load balancing on a sub server
 * group.
 */
public interface SubServerGroupInterface {

  /**
   * Gets the name of the sub server group.
   *
   * @return the name of the sub server group.
   */
  public String getServerGroupName();
}
