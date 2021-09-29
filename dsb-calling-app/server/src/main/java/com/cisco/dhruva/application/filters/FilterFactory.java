package com.cisco.dhruva.application.filters;

public class FilterFactory {
  protected static FilterNode getFilterNode(FilterId.Id id) {
    switch (id) {
      case ROOT:
        return new RootNode();
      case NETWORK_B2B:
        return new NetworkB2B();
      case NETWORK_PSTN:
        return new NetworkPSTN();
      case NETWORK_WXC:
        return new NetworkWxC();
      case CALLTYPE_DIAL_IN:
        return new CallTypeDialInTag();
      case CALLTYPE_DIAL_OUT:
        return new CallTypeDialOutTag();
      default:
        return null;
    }
  }
}
