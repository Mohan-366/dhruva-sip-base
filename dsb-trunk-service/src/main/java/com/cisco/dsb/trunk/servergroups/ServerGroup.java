package com.cisco.dsb.trunk.servergroups;

import java.util.TreeSet;
import lombok.ToString;

/**
 * This class implements a Server Group with the capability to notify listeners when the state of
 * the Server Group changes and a conversion into a CLI formatted command. It is assumed that
 * instances of this class are elements of ServerGroupRepository objects
 *
 * @see ServerGroup
 */
@ToString(callSuper = true, includeFieldNames = true)
public class ServerGroup extends DefaultServerGroup {

  public ServerGroup(String name, String network, TreeSet elements, int lbType, boolean pingOn) {
    super(name, network, elements, lbType, pingOn);
    this.toString();
  }

  /**
   * Overrides Object
   *
   * @return the ServerGroup in CLI command format
   */
  public String toString() {

    return String.format(
        "{ ServerGroup:  hostname=\"%s\" network=\"%s\" elements=%s lbType=%s }",
        name, network, elements.toString(), lbType);
  }
}
