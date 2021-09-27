/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
// FILENAME: $RCSfile: LBHashBased.java,v $
//
// MODULE:  loadbalancer
//
// COPYRIGHT:
// ============== copyright 2000 dynamicsoft Inc. =================
// ==================== all rights reserved =======================
//
// MODIFICATIONS:
//
//
//////////////////////////////////////////////////////////////////////////////
package com.cisco.dsb.trunk.loadbalancer;

import java.util.ArrayList;
import lombok.CustomLog;

/**
 * <p>This class implements the Hash-Based load balancer.<br>
 * The element with the highest q-value is selected as the next hop.
 * If multiple elements have the same highest q-value, a hash algorithm
 * using the request uri as the key is performed to ramdomly but deterministically
 * chose one of the <em>n</em> elements that have the same q-value.
 * If the chosen element is a <code>ServerGroupPlaceholder</code>, a new
 * <code>LBInterface is internally created for that sub server group and the process
 * is recursively repeated until a <code>NextHop</code> is chosen.
 */
@CustomLog
public class LBHashBased extends LBBase {

  protected final ServerGroupElementInterface selectElement(String varKey) {
    ServerGroupElementInterface selectedElement = null;
    ArrayList list = new ArrayList();
    float highestQ = -1;
    for (Object value : domainsToTry) {
      ServerGroupElementInterface sge = (ServerGroupElementInterface) value;
      if (Float.compare(highestQ, -1) == 0) {
        highestQ = sge.getQValue();
        list.add(sge);
      } else if (Float.compare(sge.getQValue(), highestQ) == 0) {
        list.add(sge);
      } else break;
    }
    StringBuffer output = new StringBuffer();
    for (Object o : list) {
      output.append(o.toString());
      output.append(", ");
    }

    logger.info("list of elements in order on which load balancing is done : " + output);

    if (list.size() == 1) selectedElement = (ServerGroupElementInterface) list.get(0);
    else {
      String hashKey = (varKey != null) ? varKey : key;
      logger.info("Hashing on " + hashKey);
      int index = HashAlgorithm.selectIndex(hashKey, list.size());
      if (index != -1) {
        logger.info("Index selected " + index);
        selectedElement = (ServerGroupElementInterface) list.get(index);
      }
    }
    return selectedElement;
  }

  protected final ServerGroupElementInterface selectElement() {
    return selectElement(null);
  }

  protected void setKey() {
    key = request.getReqURI();
  }
}
