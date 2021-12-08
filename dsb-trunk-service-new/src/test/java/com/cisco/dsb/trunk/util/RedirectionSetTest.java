package com.cisco.dsb.trunk.util;

import static org.testng.Assert.*;

import com.cisco.dsb.trunk.TrunkTestUtil;
import com.google.common.collect.Comparators;
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.header.ContactList;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.sip.InvalidArgumentException;
import org.testng.annotations.Test;

public class RedirectionSetTest {

  @Test(description = "Adding contact headers with repetition")
  public void testAdd() throws InvalidArgumentException, ParseException {
    TrunkTestUtil trunkTestUtil = new TrunkTestUtil(null);
    ContactList contactList1 = trunkTestUtil.getContactList(10, "A", null);
    ContactList contactList2 = trunkTestUtil.getContactList(10, "SRV", null);
    ContactList contactList3 = trunkTestUtil.getContactList(10, "ip", null);
    ContactList duplicateList = contactList3;

    RedirectionSet redirectionSet = new RedirectionSet();
    redirectionSet.add(contactList1);
    redirectionSet.add(contactList2);
    redirectionSet.add(contactList3);
    redirectionSet.add(duplicateList);

    // contactList1,2,3 can contain duplicates as host ip rand is between 0-1000
    assertTrue(redirectionSet.size() <= 30);
  }

  @Test(description = "pollFirst test with order")
  public void testPollFirst() throws InvalidArgumentException, ParseException {
    TrunkTestUtil trunkTestUtil = new TrunkTestUtil(null);
    ContactList contactList1 = trunkTestUtil.getContactList(10, "A", null);
    // adding a duplicate
    contactList1.add(contactList1.get(0));
    RedirectionSet redirectionSet = new RedirectionSet();
    redirectionSet.add(contactList1);

    List<Contact> selectedContact = new ArrayList<>();
    while (redirectionSet.size() != 0) selectedContact.add(redirectionSet.pollFirst());
    assertTrue(Comparators.isInOrder(selectedContact, qComparator()));
  }

  private Comparator<Contact> qComparator() {
    return (_this, _that) -> Float.compare(_that.getQValue(), _this.getQValue());
  }
}
