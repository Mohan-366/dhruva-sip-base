package com.cisco.dhruva.application.calltype;

import com.cisco.dhruva.application.filters.FilterId;
import com.google.common.collect.ImmutableList;

public enum CallTypeEnum {
  DIAL_IN_PSTN,
  DIAL_IN_B2B,
  DIAL_OUT_WXC,
  DIAL_OUT_B2B,
  TEST_1,
  TEST_2;

  private ImmutableList<FilterId> filterIds;

  CallTypeEnum() {
    switch (this.ordinal()) {
      case 0:
        filterIds = ImmutableList.of(new FilterId(FilterId.Id.NETWORK_PSTN));
        break;
      case 1:
        filterIds =
            ImmutableList.of(
                new FilterId(FilterId.Id.NETWORK_B2B),
                new FilterId(FilterId.Id.CALLTYPE_DIAL_IN_OR_MID_DIALOG_DIAL_IN));
        break;
      case 2:
        filterIds = ImmutableList.of(new FilterId(FilterId.Id.NETWORK_WXC));
        break;
      case 3:
        filterIds =
            ImmutableList.of(
                new FilterId(FilterId.Id.NETWORK_B2B),
                new FilterId(FilterId.Id.CALLTYPE_DIAL_OUT_OR_MID_DIALOG_DIAL_OUT));
        break;
      case 4:
        filterIds = ImmutableList.of(new FilterId(FilterId.Id.NETWORK_B2B));
        break;
    }
  }

  public ImmutableList<FilterId> getFilters() {
    return filterIds;
  }
}
