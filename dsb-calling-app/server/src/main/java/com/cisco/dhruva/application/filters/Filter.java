package com.cisco.dhruva.application.filters;

import com.cisco.dhruva.application.calltype.CallType;
import com.cisco.dhruva.application.calltype.CallTypeFactory;
import com.cisco.dhruva.application.exceptions.FilterTreeException;
import com.cisco.dhruva.application.exceptions.InvalidCallTypeException;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import java.util.List;
import java.util.Optional;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@CustomLog
public class Filter {
  private CallTypeFactory callTypeFactory;
  private RootNode rootNode;

  @Autowired
  public Filter(CallTypeFactory callTypeFactory) {
    this.callTypeFactory = callTypeFactory;
    this.rootNode = (RootNode) FilterFactory.getFilterNode(FilterId.Id.ROOT);
  }

  public void register(List<CallType.CallTypes> callTypes) throws FilterTreeException {
    for (CallType.CallTypes calltype : callTypes) {
      rootNode.insertCallType(calltype);
    }
  }

  public Optional<CallType> filter(final ProxySIPRequest proxySIPRequest) {
    try {
      CallType.CallTypes callType = rootNode.getCallType(proxySIPRequest);
      logger.info("CallType: {}, CallId: {}", callType, proxySIPRequest.getCallId());
      if (callType == null) throw new InvalidCallTypeException();
      return Optional.of(callTypeFactory.getCallType(callType));
    } catch (InvalidCallTypeException invalidCallType) {
      logger.error("Unknown Calltype, rejecting with 404");
      proxySIPRequest.reject(404);
    }
    return Optional.empty();
  }
}
