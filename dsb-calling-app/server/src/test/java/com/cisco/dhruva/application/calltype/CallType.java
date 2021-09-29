package com.cisco.dhruva.application.calltype;

import com.cisco.dhruva.application.filters.FilterId;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import reactor.core.publisher.Mono;

public interface CallType {
  enum CallTypes {
    DIAL_IN_PSTN,
    DIAL_IN_B2B,
    DIAL_OUT_WXC,
    DIAL_OUT_B2B,
    TEST_1,
    TEST_2;

    private ImmutableList<FilterId> filterIds;

    CallTypes() {
      switch (this.ordinal()) {
        case 0:
          filterIds = ImmutableList.of(new FilterId(FilterId.Id.NETWORK_PSTN));
          break;
        case 1:
          filterIds =
              ImmutableList.of(
                  new FilterId(FilterId.Id.NETWORK_B2B),
                  new FilterId(FilterId.Id.CALLTYPE_DIAL_IN));
          break;
        case 2:
          filterIds = ImmutableList.of(new FilterId(FilterId.Id.NETWORK_WXC));
          break;
        case 3:
          filterIds =
              ImmutableList.of(
                  new FilterId(FilterId.Id.NETWORK_B2B),
                  new FilterId(FilterId.Id.CALLTYPE_DIAL_OUT));
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

  void setReqRespMapper(
      ConcurrentHashMap<String, com.cisco.dhruva.application.calltype.CallType> reqRespMapper);

  void setKey(String key);

  String getOutBoundNetwork();

  Consumer<Mono<ProxySIPRequest>> processRequest();

  Consumer<Mono<ProxySIPResponse>> processResponse();
}
