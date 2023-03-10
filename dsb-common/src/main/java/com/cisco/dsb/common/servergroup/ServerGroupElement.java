package com.cisco.dsb.common.servergroup;

import com.cisco.dsb.common.loadbalancer.LBElement;
import com.cisco.dsb.common.transport.Transport;
import java.util.StringTokenizer;
import lombok.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@Getter
@Setter
@ToString
@Builder(setterPrefix = "set")
@NoArgsConstructor
@AllArgsConstructor
public class ServerGroupElement implements LBElement, Pingable {

  private String ipAddress;

  private int port;

  private Transport transport;

  @Builder.Default private int priority = 10;

  @Builder.Default private int weight = 100;

  private String uniqueString;

  @Override
  public boolean equals(Object a) {
    if (this == a) return true;
    if (a instanceof ServerGroupElement) {
      ServerGroupElement b = ((ServerGroupElement) a);
      return new EqualsBuilder()
          .append(ipAddress, b.ipAddress)
          .append(port, b.port)
          .append(transport, b.transport)
          .isEquals();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(ipAddress).append(port).append(transport).toHashCode();
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
   */
  @Override
  public int compareTo(Object obj) throws ClassCastException {
    int compare;
    ServerGroupElement b = ((ServerGroupElement) obj);
    if ((compare = Float.compare(this.priority, b.priority)) != 0) {
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

  /**
   * Compares two hosts, whether hostname, IP address, or mixed.
   *
   * @param domain1 this objects domain.
   * @param domain2 another object's domain.
   * @return a positive integer if this endpoint should come after the given object the supplied
   *     object in a sorted list, a negative integer if this endpoint should come before the given
   *     object in a sorted list, or zero if the two objects are equivalent.
   */
  public int compareDomainNames(String domain1, String domain2) {
    int compare = 0;
    if (!domain1.equals(domain2)) {

      StringTokenizer st1 = new StringTokenizer(domain1, ".");
      StringTokenizer st2 = new StringTokenizer(domain2, ".");

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

    int compare;
    int i = Math.min(list1.length, list2.length) - 1;
    for (; i >= 0; i--) {
      compare = list1[i].compareTo(list2[i]);
      if (compare != 0) return compare;
    }
    if (list1.length < list2.length) return -1;
    return 1;
  }

  /**
   * This is used for options ping status maintenance. This will not change unless config refresh
   * happens in which case new object will get created.
   *
   * @return uniqueString -> ip:port:transport
   */
  public String toUniqueElementString() {
    if (uniqueString != null) {
      return uniqueString;
    }
    synchronized (this) {
      if (uniqueString == null) {
        uniqueString = ipAddress + ":" + port + ":" + transport;
      }
    }

    return uniqueString;
  }
}
