package com.cisco.dhruva.application.filters;

import com.cisco.dhruva.application.calltype.CallType;
import com.cisco.dhruva.application.exceptions.FilterTreeException;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

@Component
public class RootNode extends FilterNode {

  RootNode() {
    super(new FilterId(FilterId.Id.ROOT), true);
  }

  @Override
  public Predicate<ProxySIPRequest> filter() {
    return null;
  }

  public void insertCallType(CallType.CallTypes callType) throws FilterTreeException {
    // clone the filters as we are going to modify this list
    List<FilterId> filterIds = new ArrayList<>(callType.getFilters());
    insert(filterIds, callType);
  }

  public void clear() {
    children.clear();
  }
}
