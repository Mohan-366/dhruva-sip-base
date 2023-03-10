package com.cisco.dsb.common.servergroup;

import com.cisco.dsb.common.config.RoutePolicy;
import com.cisco.dsb.common.loadbalancer.LBElement;
import com.cisco.dsb.common.loadbalancer.LBType;
import com.cisco.dsb.common.loadbalancer.LoadBalancable;
import com.cisco.dsb.common.sip.header.ListenIfHeader;
import com.cisco.dsb.common.transport.Transport;
import java.util.Arrays;
import java.util.List;
import lombok.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@CustomLog
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "set", toBuilder = true)
public class ServerGroup implements LBElement, LoadBalancable, Pingable {
  private String name;
  private String hostName;
  private String networkName;
  @Builder.Default private LBType lbType = LBType.WEIGHT;
  @Builder.Default private boolean pingOn = false;
  private List<ServerGroupElement> elements;
  private RoutePolicy routePolicy;
  private String routePolicyConfig;
  private OptionsPingPolicy optionsPingPolicy;
  private String optionsPingPolicyConfig;
  @Builder.Default private int priority = 10;
  @Builder.Default private int weight = 100;
  @Builder.Default private SGType sgType = SGType.STATIC;
  @Builder.Default private Transport transport = Transport.UDP;
  private int port;

  @Builder.Default
  private boolean enableRedirectionOptions = false; // TODO: default should be true or false?

  @Builder.Default
  private ListenIfHeader.HostnameType viaHostName = ListenIfHeader.HostnameType.LOCAL_IP;

  @Builder.Default
  private ListenIfHeader.HostnameType rrHostName = ListenIfHeader.HostnameType.LOCAL_IP;

  @Override
  public boolean equals(Object a) {
    if (this == a) return true;
    if (a instanceof ServerGroup) {
      ServerGroup sg = (ServerGroup) a;
      return new EqualsBuilder()
          .append(sg.name, this.name)
          .append(sg.hostName, this.hostName)
          .isEquals();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(name).append(hostName).toHashCode();
  }

  public void setRoutePolicy(String routePolicy) {
    this.routePolicyConfig = routePolicy;
  }

  public void setRoutePolicyFromConfig(RoutePolicy routePolicy) {
    this.routePolicy = routePolicy;
  }

  public void setOptionsPingPolicy(String optionsPingPolicy) {
    this.optionsPingPolicyConfig = optionsPingPolicy;
  }

  public void setOptionsPingPolicyFromConfig(OptionsPingPolicy optionsPingPolicy) {
    this.optionsPingPolicy = optionsPingPolicy;
  }

  public void setName(String name) {
    this.name = name;
    if (hostName == null) this.hostName = name;
  }

  public OptionsPingPolicy getOptionsPingPolicy() {
    if (this.optionsPingPolicy == null) {
      this.optionsPingPolicy =
          OptionsPingPolicy.builder()
              .setName("defaultOPPolicy")
              .setDownTimeInterval(5000)
              .setUpTimeInterval(30000)
              .setFailureResponseCodes(Arrays.asList(501, 502, 503))
              .setMaxForwards(70)
              .build();
      logger.info(
          "OptionsPingPolicy was not configured for servergroup: {}. Using default policy: {}",
          this.toString(),
          optionsPingPolicy.toString());
    }
    return optionsPingPolicy;
  }

  public RoutePolicy getRoutePolicy() {
    if (this.routePolicy == null) {
      this.routePolicy =
          RoutePolicy.builder()
              .setName("defaultSGRoutePolicy")
              .setFailoverResponseCodes(Arrays.asList(502, 503))
              .build();
      logger.info(
          "Route Policy was not configured for servergroup: {}. Using default policy: {}",
          this.toString(),
          routePolicy.toString());
    }
    return routePolicy;
  }
  /**
   * Compares this object to the object passed as an argument. If this object has a higher q-value,
   * the operation returns a negative integer. If this object has a lower q-value, the operation
   * returns a positive integer. If this object has the same q-value, the operation returns 0.
   *
   * @return
   *     <p>A negative integer if this object has a higher q-value, a positive integer if this
   *     object has a lower q-value, or <code>0</code> if this object has the same q-value.
   * @throws ClassCastException thrown if obj is not of type ServerGroup
   */
  @Override
  public int compareTo(Object obj) throws ClassCastException {
    int compare;
    if ((compare = Float.compare(this.priority, ((ServerGroup) obj).priority)) != 0) return compare;
    if ((compare = Integer.compare(((ServerGroup) obj).weight, this.weight)) != 0) return compare;
    if ((compare = ((ServerGroup) obj).hostName.compareTo(this.hostName)) != 0) return compare;
    return 0;
  }

  /*
  This method checks if a serverGroup is completely identical to a given serverGroup.
  This is needed in operations such as config refresh.
   */
  public boolean isCompleteObjectEqual(ServerGroup obj) {
    if (!this.equals(obj) || (this.compareTo(obj) != 0)) {
      return false;
    }
    return new EqualsBuilder()
        .append(this.name, obj.name)
        .append(this.networkName, obj.networkName)
        .append(this.pingOn, obj.pingOn)
        .append(this.transport, obj.transport)
        .append(this.lbType, obj.lbType)
        .append(this.sgType, obj.sgType)
        .append(this.routePolicy, obj.routePolicy)
        .append(this.optionsPingPolicy, obj.optionsPingPolicy)
        .isEquals();
  }

  @Override
  public String toString() {
    return String.format(
        "( Name=%s HostName=%s ;network=%s ;priority=%d ;weight=%d )",
        name, hostName, networkName, priority, weight);
  }

  @Override
  public List<ServerGroupElement> getElements() {
    if (elements != null) return elements;
    return null;
  }
}
