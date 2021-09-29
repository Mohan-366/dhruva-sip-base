package com.cisco.dhruva.application.filters;

public class FilterId {
  public enum Id {
    ROOT,
    NETWORK_PSTN,
    NETWORK_B2B,
    NETWORK_WXC,
    CALLTYPE_DIAL_IN,
    CALLTYPE_DIAL_OUT
  }

  public final Id id;

  public FilterId(Id id) {
    this.id = id;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof FilterNode) return this.equals(((FilterNode) obj).getFilterId());
    else if (obj instanceof FilterId) {
      return this.id == ((FilterId) obj).id;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
