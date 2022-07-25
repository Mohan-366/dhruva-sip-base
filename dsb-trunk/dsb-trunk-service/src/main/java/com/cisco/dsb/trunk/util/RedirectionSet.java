package com.cisco.dsb.trunk.util;

import com.cisco.dsb.common.transport.Transport;
import gov.nist.core.HostPort;
import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.header.ContactList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.TreeSet;

public class RedirectionSet {
  private HashSet<String> hashSet = new HashSet<>();
  private TreeSet<Contact> treeSet = new TreeSet<>(contactComparator());

  /**
   * Adds Contact header to treeSet if the element was not added before. Even if the element gets
   * removed from treeset, adding the same element back will return false. If the element gets added
   * for the first time return true. while adding to hashSet, only host:port is considered
   *
   * @param contact
   * @return
   */
  public boolean add(Contact contact) {
    HostPort hostPort = ((AddressImpl) contact.getAddress()).getHostPort();
    String transport =
        contact.hasParameter("transport")
            ? contact.getParameter("transport")
            : String.valueOf(Transport.UDP);
    String key =
        hostPort.getHost().getHostname()
            + (hostPort.hasPort() ? hostPort.getPort() : 0)
            + transport;
    /*String key = ((AddressImpl) contact.getAddress()).getHost() +
    (((AddressImpl) contact.getAddress()).getPort())?sipUri.getPort():0);*/
    // check out double hashing??
    boolean success = hashSet.add(key);
    if (success) treeSet.add(contact);
    return success;
  }

  public void add(ContactList contactList) {
    contactList.forEach(this::add);
  }

  /**
   * returns the first element, without removing the element
   *
   * @return
   */
  public Contact first() {
    if (treeSet.isEmpty()) return null;
    return treeSet.first();
  }

  /**
   * returns the first element and removes the element
   *
   * @return
   */
  public Contact pollFirst() {
    if (treeSet.isEmpty()) return null;
    return treeSet.pollFirst();
  }

  public int size() {
    return treeSet.size();
  }

  /**
   * returns positive integer if this object has less qValue. In case qvalue is equal, host portion
   * is compared. if Host portion is equal then port is compared and then transport.
   *
   * @return
   */
  private Comparator<Contact> contactComparator() {
    return (_this, _that) -> {
      int compare;
      if (_this.equals(_that)) return 0;
      if ((compare = Float.compare(_that.getQValue(), _this.getQValue())) != 0) return compare;
      if ((compare =
              ((AddressImpl) _that.getAddress())
                  .getHost()
                  .compareTo(((AddressImpl) _this.getAddress()).getHost()))
          != 0) return compare;
      // least port is given highest preference, SRV > A record
      if ((compare =
              ((AddressImpl) _this.getAddress()).getPort()
                  - ((AddressImpl) _that.getAddress()).getPort())
          != 0) return compare;
      Transport transport_this, transport_that;
      String trans_this =
          _this.hasParameter("transport")
              ? _this.getParameter("transport")
              : String.valueOf(Transport.UDP);
      String trans_that =
          _that.hasParameter("transport")
              ? _that.getParameter("transport")
              : String.valueOf(Transport.UDP);
      try {
        transport_this = Transport.valueOf(trans_this);
      } catch (IllegalArgumentException ex) {
        transport_this = Transport.UDP;
      }
      try {
        transport_that = Transport.valueOf(trans_that);
      } catch (IllegalArgumentException ex) {
        transport_that = Transport.UDP;
      }
      return Integer.compare(transport_this.getValue(), transport_that.getValue());
    };
  }

  public boolean isEmpty() {
    return treeSet.isEmpty();
  }
}
