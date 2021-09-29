package com.cisco.dhruva.application.filters;

import com.cisco.dhruva.application.calltype.CallType;
import com.cisco.dhruva.application.exceptions.FilterTreeException;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import java.util.HashMap;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class RootNodeTest {
  @InjectMocks RootNode rootNode;

  @BeforeTest
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void cleanUp() {
    rootNode.clear();
  }

  @Test(
      description = "Adding calltype to non-leaf node",
      expectedExceptions = {FilterTreeException.class})
  public void testInsertCallType_1() throws FilterTreeException {
    rootNode.insertCallType(CallType.CallTypes.DIAL_IN_B2B);
    rootNode.insertCallType(CallType.CallTypes.TEST_1);
  }

  @Test(
      description = "Adding children to leaf node",
      expectedExceptions = {FilterTreeException.class})
  public void testInsertCallType_2() throws FilterTreeException {
    rootNode.insertCallType(CallType.CallTypes.TEST_1);
    rootNode.insertCallType(CallType.CallTypes.DIAL_IN_B2B);
  }

  @Test(description = "cache checking")
  public void testGetCallType() throws FilterTreeException {
    rootNode.insertCallType(CallType.CallTypes.DIAL_IN_B2B);
    ProxySIPRequest proxySIPRequest = Mockito.mock(ProxySIPRequest.class);
    HashMap<Object, Object> cache = new HashMap<>();
    Mockito.when(proxySIPRequest.getNetwork()).thenReturn("TestNet");
    Mockito.when(proxySIPRequest.getCache()).thenReturn(cache);
    rootNode.getCallType(proxySIPRequest);
    // second time should pick from cache
    rootNode.getCallType(proxySIPRequest);
    Mockito.verify(proxySIPRequest, Mockito.times(1)).getNetwork();
    Assert.assertEquals(cache.get(new FilterId(FilterId.Id.NETWORK_B2B)), Boolean.FALSE);
  }

  @Test(description = "Checking two FilterNodes are identified based on FilterId")
  public void testEquals() {
    FilterNode f1 = new RootNode();
    FilterNode f2 = new RootNode();
    FilterNode f3 = new NetworkB2B();
    Boolean f4 = Boolean.FALSE;
    Assert.assertEquals(f1, f2);
    Assert.assertNotEquals(f1, f3);
    Assert.assertFalse(f1.equals(f4));
  }
}
