package com.cisco.dhruva.application.filters;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class FilterId {
  public enum Id {
    ROOT,
    NETWORK_PSTN,
    NETWORK_B2B,
    NETWORK_WXC,
    CALLTYPE_DIAL_IN_OR_MID_DIALOG_DIAL_IN,
    CALLTYPE_DIAL_OUT_OR_MID_DIALOG_DIAL_OUT
  }

  public final Id id;

  public FilterId(Id id) {
    this.id = id;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj instanceof FilterNode) {
      return this.equals(((FilterNode) obj).getFilterId());
    } else if (obj instanceof FilterId) {
      return new EqualsBuilder().append(id, ((FilterId) obj).id).isEquals();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(id).toHashCode();
  }
}
