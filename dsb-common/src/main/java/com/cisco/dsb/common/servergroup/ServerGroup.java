package com.cisco.dsb.common.servergroup;

import com.cisco.dsb.common.loadbalancer.LBElement;
import com.cisco.dsb.common.loadbalancer.LBType;
import com.cisco.dsb.common.loadbalancer.LoadBalancable;
import com.cisco.dsb.common.transport.Transport;
import java.util.Arrays;
import java.util.List;
import lombok.*;

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
  @Builder.Default private LBType lbType = LBType.HIGHEST_Q;
  private boolean pingOn = false;
  private List<ServerGroupElement> elements;
  private SGPolicy sgPolicy;
  private String sgPolicyConfig;
  private OptionsPingPolicy optionsPingPolicy;
  private String optionsPingPolicyConfig;
  private int priority;
  private int weight;
  @Builder.Default private SGType sgType = SGType.STATIC;
  @Builder.Default private Transport transport = Transport.UDP;
  private int port;

  @Override
  public boolean equals(Object a) {
    if (a instanceof ServerGroup) {
      return ((ServerGroup) a).hostName.equals(hostName);
    }
    return false;
  }

  public void setSgPolicy(String sgPolicy) {
    this.sgPolicyConfig = sgPolicy;
  }

  public void setSgPolicyFromConfig(SGPolicy sgPolicy) {
    this.sgPolicy = sgPolicy;
  }

  public void setOptionsPingPolicy(String optionsPingPolicy) {
    this.optionsPingPolicyConfig = optionsPingPolicy;
  }

  public void setOptionsPingPolicyFromConfig(OptionsPingPolicy optionsPingPolicy) {
    this.optionsPingPolicy = optionsPingPolicy;
  }

  public OptionsPingPolicy getOptionsPingPolicy() {
    if (this.optionsPingPolicy == null) {
      this.optionsPingPolicy =
          OptionsPingPolicy.builder()
              .setName("defaultOPPolicy")
              .setPingTimeOut(500)
              .setDownTimeInterval(5000)
              .setUpTimeInterval(30000)
              .setFailoverResponseCodes(Arrays.asList(501, 502, 503))
              .build();
      logger.info(
          "OptionsPingPolicy was not configured for servergroup: {}. Using default policy: {}",
          this.toString(),
          optionsPingPolicy.toString());
    }
    return optionsPingPolicy;
  }

  public SGPolicy getSgPolicy() {
    if (this.sgPolicy == null) {
      this.sgPolicy =
          SGPolicy.builder()
              .setName("defaultSGPolicy")
              .setFailoverResponseCodes(Arrays.asList(501, 502, 503))
              .build();
      logger.info(
          "SGPolicy was not configured for servergroup: {}. Using default policy: {}",
          this.toString(),
          sgPolicy.toString());
    }
    return sgPolicy;
  }
  /**
   * Compares this object to the object passed as an argument. If this object has a higher q-value,
   * the operation returns a negative integer. If this object has a lower q-value, the operation
   * returns a positive integer. If this object has the same q-value, the operation returns 0.
   *
   * @return
   *     <p>A negative integer if this object has a higher q-value, a positive integer if this
   *     object has a lower q-value, or <code>0</code> if this object has the same q-value.
   * @throws ClassCastException
   */
  @Override
  public int compareTo(Object obj) throws ClassCastException {
    int compare;
    if ((compare = Float.compare(this.priority, ((ServerGroup) obj).priority)) != 0) return compare;
    if ((compare = Integer.compare(((ServerGroup) obj).weight, this.weight)) != 0) return compare;
    if ((compare = ((ServerGroup) obj).hostName.compareTo(this.hostName)) != 0) return compare;
    return 0;
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
    /*SpringApplicationContext.getAppContext().getBean(SipServerLocatorService.class)
    .locateDestinationAsync()*/
    return null;
  }
}
