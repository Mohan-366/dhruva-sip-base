/*
 * Copyright (c) 2001-2002, 2003-2005 by cisco Systems, Inc.
 * All rights reserved.
 */
// FILENAME: $RCSfile: LBFactory.java,v $
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
package com.cisco.dsb.loadbalancer;

import com.cisco.dsb.common.messaging.models.AbstractSipRequest;
import com.cisco.dsb.servergroups.SG;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import org.springframework.stereotype.Component;

/**
 * This factory creates a load balancer based on settings in the configuration or by loading a
 * specific named class.
 */
@Component
public class LBFactory {

  private static final Logger log = DhruvaLoggerFactory.getLogger(LBFactory.class);

  public static final int GLOBAL = SG.index_sgSgLbType_global;
  public static final int REQUEST_URI = SG.index_sgSgLbType_request_uri;
  public static final int HIGHEST_Q = SG.index_sgSgLbType_highest_q;
  public static final int CALLID = SG.index_sgSgLbType_call_id;
  public static final int TO = SG.index_sgSgLbType_to_uri;
  public static final int WEIGHT = SG.index_sgSgLbType_weight;
  public static final int MS_ID = SG.index_sgSgLbType_ms_id;
  public static final int VARKEY = 999; /* internal lb type */

  public static final int CUSTOM = -1;

  private static int DEFAULT_LB_TYPE =
      SG.getValidValueAsInt(SG.sgSgLbType, SG.dsSgGlobalSelectionTypeDefault);

  /**
   * <p>Creates a <code>LBInterface</code> based on settings in the
   * <code>LoadBalancerConfigInterface</code>. Applications should maintain a
   * reference to the <code>LBInterface</code> for the duration
   * of the transaction. If the application needs to choose another next hop
   * destination from the server group due to a timeout or failure, successive
   * calls to {@link LBInterface#getServer() should be made.
   * @param serverGroupName the server group to load balance over.
   * @param server groups the entire server group repository
   * @param key the key used for hashing
   * @return a load balancer.
   * @throws LBException
   * @throws NonExistantServerGroupException
   */
  public LBInterface createLoadBalancer(
      String serverGroupName, ServerGroupInterface serverGroup, AbstractSipRequest request)
      throws LBException {

    if (serverGroup == null)
      throw new LBException(
          "Cannot create load balancer.  Server group " + serverGroupName + " not found.");
    RepositoryReceiverInterface lb = null;
    int lbtype = serverGroup.getLBType();

    if (lbtype == GLOBAL) {
      lbtype = getDefaultLBType();
      log.info("Default lbtype is " + lbtype + "(" + getLBTypeAsString(lbtype) + ")");
    }
    switch (lbtype) {
      case REQUEST_URI:
        lb = new LBHashBased();
        break;
      case HIGHEST_Q:
        lb = new LBHighestQ();
        break;
      case CALLID:
        lb = new LBCallID();
        break;
      case TO:
        lb = new LBTo();
        break;
      case WEIGHT:
        lb = new LBWeight();
        break;
      case MS_ID:
        lb = new LBMsid();
        break;
      case VARKEY:
        lb = new LBHashBasedVariableKey();
        break;

      default:
        throw new LBException("Unknown lbtype: " + lbtype);
    }
    lb.setServerInfo(serverGroupName, serverGroup, request);
    return lb;
  }

  /**
   * Gets the global load balancing type.
   *
   * @return the global load balancing type.
   */
  public static synchronized int getDefaultLBType() {
    return DEFAULT_LB_TYPE;
  }

  public static String getLBTypeAsString(int index) {
    return SG.getValidValueAsString(SG.sgSgLbType, index);
  }
}
