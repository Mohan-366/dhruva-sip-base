package com.cisco.dhruva.application.filters;

import com.cisco.dhruva.application.calltype.CallType;
import com.cisco.dhruva.application.calltype.CallTypeEnum;
import com.cisco.dhruva.application.calltype.CallTypeFactory;
import com.cisco.dhruva.application.exceptions.FilterTreeException;
import com.cisco.dhruva.application.exceptions.InvalidCallTypeException;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import java.util.List;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@CustomLog
public class Filter {
  private CallTypeFactory callTypeFactory;
  private RootNode rootNode;
  private FilterFactory filterFactory;

  @Autowired
  public Filter(CallTypeFactory callTypeFactory, FilterFactory filterFactory) {
    this.callTypeFactory = callTypeFactory;
    this.filterFactory = filterFactory;
    this.rootNode = (RootNode) filterFactory.getFilterNode(FilterId.Id.ROOT);
  }

  public void register(List<CallTypeEnum> callTypes) throws FilterTreeException {
    for (CallTypeEnum calltype : callTypes) {
      rootNode.insertCallType(calltype);
    }
  }

  public CallType filter(final ProxySIPRequest proxySIPRequest) throws InvalidCallTypeException {
    CallTypeEnum callType = rootNode.getCallType(proxySIPRequest);
    logger.info("CallType: {}, CallId: {}", callType, proxySIPRequest.getCallId());
    if (callType == null) throw new InvalidCallTypeException();
    return callTypeFactory.getCallType(callType);
  }
}
