package com.cisco.dhruva.application.filters;

import com.cisco.dhruva.application.calltype.CallTypeEnum;
import com.cisco.dhruva.application.exceptions.FilterTreeException;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import java.util.*;
import java.util.function.Predicate;
import lombok.CustomLog;
import lombok.Getter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@CustomLog
public abstract class FilterNode {
  @Getter private final FilterId filterId;
  private Comparator<? super FilterNode> depthComparator =
      (o1, o2) -> {
        // This case is to make sure we don't insert same filterNode in same level
        if (o1.filterId.equals(o2.filterId)) {
          logger.error("Node already exists, not adding");
          return 0;
        }
        if (o1.depth < o2.depth) {
          logger.debug(
              "Depth of {} is greater than {}, hence swapping the order",
              o2.filterId.id,
              o1.filterId.id);
          return 1;
        } else {
          logger.debug(
              "Depth of {} is less than or equal to  {}, hence NOT swapping the order",
              o2.filterId.id,
              o1.filterId.id);
          return -1;
        }
      };
  protected List<FilterNode> children = new ArrayList<>();
  private boolean isRoot;
  private CallTypeEnum callType = null;
  private int depth;

  protected FilterNode(FilterId filterId) {
    this(filterId, false);
  }

  protected FilterNode(FilterId filterId, boolean isRoot) {
    this.filterId = filterId;
    this.isRoot = isRoot;
  }

  public abstract Predicate<ProxySIPRequest> filter();

  public CallTypeEnum getCallType(ProxySIPRequest proxySIPRequest) {
    Boolean result;
    // calculate the result of current Node
    if (!isRoot) {
      HashMap cache = proxySIPRequest.getCache();
      result = (Boolean) cache.computeIfAbsent(filterId, o -> filter().test(proxySIPRequest));
      if (!result) return null;
    }
    // if result = true iterate children
    CallTypeEnum calltype;
    for (FilterNode child : children) {
      calltype = child.getCallType(proxySIPRequest);
      if (calltype != null) return calltype;
    }
    // no matching calltype in children, return calltype of current node
    return this.callType;
  }

  int insert(List<FilterId> filterIds, CallTypeEnum callType) throws FilterTreeException {
    if (filterIds.size() == 0) {
      if (children.size() != 0) {
        logger.error("can't add calltype to non-leaf node");
        throw new FilterTreeException("can't add calltype to non-leaf node");
      }
      this.callType = callType;
      return 0;
    }
    if (this.callType != null) {
      logger.error("Reached a leaf node, can't add children.");
      throw new FilterTreeException("Adding children to leaf node");
    }
    FilterId childId = filterIds.get(0);
    FilterNode childNode;
    // check if child already exists
    int indexOfChildNode = children.indexOf(childId);
    if (indexOfChildNode != -1) {
      logger.debug("Child {} already present in {}", childId.id, this.filterId.id);
      childNode = children.get(indexOfChildNode);
    } else {
      // add FilterNode from Filter
      childNode =
          SpringApplicationContext.getAppContext()
              .getBean(FilterFactory.class)
              .getFilterNode(childId.id);
      children.add(childNode);
    }
    filterIds.remove(0);
    int child_depth = childNode.insert(filterIds, callType);
    // sort the Children based on depth of children
    Collections.sort(children, depthComparator);
    // update the depth
    if (child_depth + 1 > depth) depth = child_depth + 1;
    return depth;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj instanceof FilterNode) {
      FilterNode that = (FilterNode) obj;
      return new EqualsBuilder().append(filterId, that.getFilterId()).isEquals();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(filterId).toHashCode();
  }
}
