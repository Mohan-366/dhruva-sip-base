package com.cisco.dsb.common.servergroup;

import com.cisco.dsb.common.loadbalancer.LBElement;
import com.cisco.dsb.common.transport.Transport;
import java.util.StringTokenizer;
import javax.validation.constraints.NotBlank;
import lombok.*;
import org.apache.commons.lang3.builder.EqualsBuilder;

@Getter
@Setter
@ToString
@Builder(setterPrefix = "set")
@NoArgsConstructor
@AllArgsConstructor
public class ServerGroupElement implements LBElement {

  @NotBlank private String ipAddress;

  @NotBlank private int port;

  @NotBlank private Transport transport;

  @NotBlank private float qValue;

  @NotBlank private int weight;

  @Override
  public boolean equals(Object a) {
    if (a instanceof ServerGroupElement) {
      ServerGroupElement b = ((ServerGroupElement) a);
      return new EqualsBuilder()
          .append(ipAddress, b.ipAddress)
          .append(port, b.port)
          .append(transport, b.transport)
          .append(qValue, b.qValue)
          .append(weight, b.weight)
          .isEquals();
    }
    return false;
  }

  /**
   * Compares this object to the object passed as an argument. If this object has a higher q-value,
   * the operation returns a negative integer. If this object has a lower q-value, the operation
   * returns a positive integer. If this object has the same q-value, the operation returns 0.
   *
   * @return
   *     <p>A negative integer if this object has a higher q-value, a positive integer if this
   *     object has a lower q-value, or <code>0</code> if this object has the same q-value. NOTE: 0
   *     means it's a duplicate
   * @throws ClassCastException
   */
  @Override
  public int compareTo(Object obj) throws ClassCastException {
    int compare;
    ServerGroupElement b = ((ServerGroupElement) obj);
    if ((compare = Float.compare(b.qValue, this.qValue)) != 0) {
      return compare;
    }
    if ((compare = Integer.compare(b.weight, this.weight)) != 0) {
      return compare;
    }
    if ((compare = compareDomainNames(b.ipAddress, this.ipAddress)) != 0) {
      return compare;
    }
    if ((compare = Integer.compare(b.port, this.port)) != 0) return compare;
    if ((compare = Integer.compare(b.transport.getValue(), this.transport.getValue())) != 0)
      return compare;
    return 0;
  }

  @Override
  public boolean isActive() {
    return true;
  }

  /**
   * Compares two hosts, whether hostname, IP address, or mixed.
   *
   * @param domain1 this objects domain.
   * @param domain2 another object's domain.
   * @return a positive integer if this endpoint should come after the given object the supplied
   *     object in a sorted list, a negative integer if this endpoint should come before the given
   *     object in a sorted list, or zero if the two objects are equivalent.
   */
  private int compareDomainNames(String domain1, String domain2) {
    int compare = 0;
    if (!domain1.equals(domain2)) {

      StringTokenizer st1 = new StringTokenizer(domain1.toString(), ".");
      StringTokenizer st2 = new StringTokenizer(domain2.toString(), ".");

      String[] list1 = new String[st1.countTokens()];
      String[] list2 = new String[st2.countTokens()];
      int i = 0;
      while (st1.hasMoreTokens()) {
        list1[i] = st1.nextToken();
        i++;
      }
      i = 0;
      while (st2.hasMoreTokens()) {
        list2[i] = st2.nextToken();
        i++;
      }
      if (list1.length == list2.length) {
        try {
          for (i = 0; i < list1.length; i++) {
            int a = Integer.parseInt(list1[i]);
            int b = Integer.parseInt(list2[i]);
            if (a > b) {
              compare = 1;
              break;
            } else if (b > a) {
              compare = -1;
              break;
            }
          }
        } catch (NumberFormatException nfe) {
          compare = doStringDomainCompare(list1, list2);
        }
      } else {
        compare = doStringDomainCompare(list1, list2);
      }
    }
    return compare;
  }

  private int doStringDomainCompare(String[] list1, String[] list2) {

    int compare = 0;
    int i = Math.min(list1.length, list2.length) - 1;
    for (; i >= 0; i--) {
      compare = list1[i].compareTo(list2[i]);
      if (compare != 0) return compare;
    }
    if (list1.length < list2.length) return -1;
    return 1;
  }
}
