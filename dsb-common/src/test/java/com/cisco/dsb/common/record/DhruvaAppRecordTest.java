package com.cisco.dsb.common.record;

import com.cisco.wx2.util.Utilities;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test(description = "tests for app record class")
public class DhruvaAppRecordTest {

  public enum TestState implements DhruvaState {
    IN_SIP_EVENT1("proxy event 1"),
    IN_SIP_EVENT2("proxy event 2"),
    IN_SIP_EVENT3("proxy event 3");

    private String state;

    TestState(String state) {
      this.state = state;
    }

    @Override
    public String getState() {
      return state;
    }
  }

  @Test(description = "basic app initialization tests")
  public void test1() {
    DhruvaAppRecord dhruvaAppRecord = DhruvaAppRecord.create();
    Assert.assertNotNull(dhruvaAppRecord);

    ConcurrentLinkedQueue<Record> records = dhruvaAppRecord.getHistory();
    Assert.assertTrue(records.isEmpty());

    long t1 = System.currentTimeMillis();
    long t2 = dhruvaAppRecord.creationTimeSinceEpochMs();
    Assert.assertTrue(t1 >= t2);
    long t3 = dhruvaAppRecord.firstTime();
  }

  @Test(description = "add records")
  public void test2() {
    ArrayList<TestState> states = new ArrayList<>();
    states.add(TestState.IN_SIP_EVENT1);
    states.add(TestState.IN_SIP_EVENT2);

    DhruvaAppRecord dhruvaAppRecord = DhruvaAppRecord.create();
    Assert.assertNotNull(dhruvaAppRecord);
    Utilities.Checks checks = new Utilities.Checks();
    checks.add("test1 validations", "done processing");
    checks.add("test1 dns query", "success");
    dhruvaAppRecord.add(TestState.IN_SIP_EVENT1, checks);

    ConcurrentLinkedQueue<Record> records = dhruvaAppRecord.getHistory();
    Assert.assertFalse(records.isEmpty());

    Assert.assertEquals(records.size(), 1);

    checks.add("test2 validations", "done processing");
    checks.add("test2 dns query", "success");
    dhruvaAppRecord.add(TestState.IN_SIP_EVENT2, checks);

    Assert.assertEquals(records.size(), 2);
    Object[] a = records.toArray();
    for (int i = 0; i < a.length; i++) {
      Record r = (Record) a[i];
      Assert.assertSame(r.getState(), states.get(i));
    }

    dhruvaAppRecord.addIfNotAlready(TestState.IN_SIP_EVENT1, null);

    Assert.assertEquals(records.size(), 2);

    dhruvaAppRecord.addIfNotAlready(TestState.IN_SIP_EVENT3, null);
    Assert.assertEquals(records.size(), 3);

    StartAndEnd sae =
        dhruvaAppRecord.findFirstStartAndLastEndStates(
            TestState.IN_SIP_EVENT1, TestState.IN_SIP_EVENT3);
    long t1 = dhruvaAppRecord.calculateTimeBetween(sae);
    Assert.assertTrue(t1 > 0);

    Assert.assertNotNull(dhruvaAppRecord.toString());
  }
}
