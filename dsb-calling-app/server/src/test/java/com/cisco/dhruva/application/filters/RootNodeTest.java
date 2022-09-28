package com.cisco.dhruva.application.filters;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cisco.dhruva.application.CallingAppConfigurationProperty;
import com.cisco.dhruva.application.calltype.CallTypeEnum;
import com.cisco.dhruva.application.exceptions.FilterTreeException;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import java.util.HashMap;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class RootNodeTest {
  RootNode rootNode;
  @Mock CallingAppConfigurationProperty configurationProperty;

  @BeforeTest
  public void init() {
    rootNode = new RootNode();
    MockitoAnnotations.openMocks(this);
    when(configurationProperty.getNetworkPSTN()).thenReturn("net_sp");
    when(configurationProperty.getNetworkB2B()).thenReturn("net_b2b");
    when(configurationProperty.getNetworkCallingCore()).thenReturn("net_cc");

    NetworkPSTN networkPSTN = new NetworkPSTN();
    networkPSTN.setConfigurationProperty(configurationProperty);
    NetworkB2B networkB2B = new NetworkB2B();
    networkB2B.setConfigurationProperty(configurationProperty);
    NetworkWxC networkWxC = new NetworkWxC();
    networkWxC.setConfigurationProperty(configurationProperty);

    FilterFactory filterFactory = new FilterFactory();
    filterFactory.setNetworkPSTN(networkPSTN);
    filterFactory.setNetworkB2B(networkB2B);
    filterFactory.setNetworkWxC(networkWxC);

    SpringApplicationContext springApplicationContext = new SpringApplicationContext();
    ApplicationContext applicationContext = mock(ApplicationContext.class);
    springApplicationContext.setApplicationContext(applicationContext);
    when(applicationContext.getBean(FilterFactory.class)).thenReturn(filterFactory);
  }

  @AfterMethod
  public void cleanUp() {
    rootNode.clear();
  }

  @Test(
      description = "Adding calltype to non-leaf node",
      expectedExceptions = {FilterTreeException.class})
  public void testInsertCallType_1() throws FilterTreeException {
    rootNode.insertCallType(CallTypeEnum.DIAL_IN_B2B);
    rootNode.insertCallType(CallTypeEnum.TEST_1);
  }

  @Test(
      description = "Adding children to leaf node",
      expectedExceptions = {FilterTreeException.class})
  public void testInsertCallType_2() throws FilterTreeException {
    rootNode.insertCallType(CallTypeEnum.TEST_1);
    rootNode.insertCallType(CallTypeEnum.DIAL_IN_B2B);
  }

  @Test(description = "cache checking")
  public void testGetCallType() throws FilterTreeException {
    rootNode.insertCallType(CallTypeEnum.DIAL_IN_B2B);
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
    Assert.assertNotEquals(f4, f1);
  }

  @Test
  public void testEqualsOfFilterId() {
    EqualsVerifier.simple().forClass(FilterId.class).verify();
  }
}
