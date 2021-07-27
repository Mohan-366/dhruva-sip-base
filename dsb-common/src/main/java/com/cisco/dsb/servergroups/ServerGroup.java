package com.cisco.dsb.servergroups;

/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
// FILENAME: $RCSfile: ServerGroup.java,v $
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

// import com.cisco.dhruva.config.sip.RE;

import com.cisco.dsb.util.log.Trace;
import java.util.HashMap;
import java.util.TreeSet;

/**
 * This class implements a Server Group with the capability to notify listeners when the state of
 * the Server Group changes and a conversion into a CLI formatted command. It is assumed that
 * instances of this class are elements of ServerGroupRepository objects
 *
 * @see ServerGroup
 */
public class ServerGroup extends DefaultServerGroup {

  private static final Trace Log = Trace.getTrace(ServerGroup.class.getName());
  // some private strings
  private static final String colon = ":";

  //  public ServerGroup(String name, String network, TreeSet elements, int lbType, boolean pingOn)
  // {
  //    super(name, network, elements, lbType, pingOn);
  //    this.wasAvailable = true;
  //    this.toString();
  //  }
  public ServerGroup(String name, String network, TreeSet elements, int lbType, boolean pingOn) {
    super(name, network, elements, lbType, pingOn);
    this.wasAvailable = true;
    this.toString();
  }
  /**
   * Overrides Object
   *
   * @return the ServerGroup in CLI command format
   */
  public String toString() {
    String value;

    HashMap elementMap = new HashMap();

    // MIGRATION
    value = elementMap.toString();

    return String.format(
        "{ ServerGroup:  hostname=\"%s\" network=\"%s\" elements=%s lbType=%s }",
        name, network, elements.toString(), lbType);
  }
}
