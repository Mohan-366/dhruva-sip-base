package com.cisco.dsb.trunk.util;

import static org.testng.Assert.*;

import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.trunk.TrunkTestUtil;
import com.google.common.collect.Comparators;
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.header.ContactList;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.sip.InvalidArgumentException;
import org.testng.Assert;
import org.testng.annotations.Test;

public class RedirectionSetTest {

  @Test(description = "Adding contact headers with repetition")
  public void testAdd() throws InvalidArgumentException, ParseException {
    TrunkTestUtil trunkTestUtil = new TrunkTestUtil(null);
    ContactList contactList1 = trunkTestUtil.getContactList(10, "A", null);
    ContactList contactList2 = trunkTestUtil.getContactList(10, "SRV", null);
    ContactList contactList3 = trunkTestUtil.getContactList(10, "static", null);

    RedirectionSet redirectionSet = new RedirectionSet();
    redirectionSet.add(contactList1);
    redirectionSet.add(contactList2);
    redirectionSet.add(contactList3);
    redirectionSet.add(contactList3);

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

  @Test(description = "all the cases while adding new contact to treeSet using comparator")
  public void testComparator() throws ParseException {
    Contact contact1 =
        (Contact) JainSipHelper.createContactHeader(null, "akg", "akg.test.com", 5060);
    Contact contact2 =
        (Contact) JainSipHelper.createContactHeader(null, "akg", "akg.test.com", 5061);
    Contact contact3 =
        (Contact) JainSipHelper.createContactHeader(null, "akg", "akg.test.com", 5060);
    contact3.setParameter("transport", "TCP");
    // this contact won't be added as it's same as contact1
    Contact contact4 =
        (Contact) JainSipHelper.createContactHeader(null, "akg", "akg.test.com", 5060);
    contact4.setParameter("transport", "invalid");

    Contact contact5 =
        (Contact) JainSipHelper.createContactHeader(null, "akg", "akg.test.com", 5070);
    contact5.setParameter("transport", "invalid1");
    Contact contact6 =
        (Contact) JainSipHelper.createContactHeader(null, "akg", "akg.test.com", 5070);
    contact6.setParameter("transport", "invalid2");

    RedirectionSet redirectionSet = new RedirectionSet();
    redirectionSet.add(contact1);
    redirectionSet.add(contact2);
    redirectionSet.add(contact3);
    redirectionSet.add(contact4);
    redirectionSet.add(contact5);
    redirectionSet.add(contact6);

    Assert.assertEquals(contact1, redirectionSet.pollFirst());
    assertEquals(contact3, redirectionSet.pollFirst());
    assertEquals(contact2, redirectionSet.pollFirst());
    assertEquals(contact5, redirectionSet.pollFirst());
    assertNull(redirectionSet.pollFirst());
  }

  private Comparator<Contact> qComparator() {
    return (_this, _that) -> Float.compare(_that.getQValue(), _this.getQValue());
  }
}
