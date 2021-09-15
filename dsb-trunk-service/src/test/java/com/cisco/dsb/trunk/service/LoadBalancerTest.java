package com.cisco.dsb.trunk.service;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;

import com.cisco.dsb.common.messaging.models.AbstractSipRequest;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.common.util.log.Logger;
import com.cisco.dsb.trunk.loadbalancer.LBCallID;
import com.cisco.dsb.trunk.loadbalancer.LBWeight;
import com.cisco.dsb.trunk.loadbalancer.ServerGroupElementInterface;
import com.cisco.dsb.trunk.loadbalancer.ServerGroupInterface;
import com.cisco.dsb.trunk.servergroups.AbstractNextHop;
import com.cisco.dsb.trunk.servergroups.DefaultNextHop;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.mockito.Mockito;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class LoadBalancerTest {
  protected static final Logger logger = DhruvaLoggerFactory.getLogger(LoadBalancerTest.class);

  @DataProvider(name = "dataProvider")
  public Object[][] dataProviders() {
    return new Object[][] {
      {0.5f, 0.8f, 1},
      {0.9f, 0.4f, 0}
    };
  }

  @Test(dataProvider = "dataProvider", description = "based on call-ID")
  public void selectElementCallBased(float qValue1, float qValue2, int result) {
    LBCallID callBased;
    callBased = new LBCallID();

    // create multiple Server Group Elements
    AbstractNextHop anh1 =
        new DefaultNextHop("testNw", "testHost", 0001, Transport.UDP, qValue1, "testSG1");
    AbstractNextHop anh2 =
        new DefaultNextHop("testNw", "testHost", 0002, Transport.UDP, qValue1, "testSG2");
    AbstractNextHop anh3 =
        new DefaultNextHop("testNw", "testHost", 0003, Transport.UDP, qValue2, "testSG3");
    AbstractNextHop anh4 =
        new DefaultNextHop("testNw", "testHost", 0004, Transport.UDP, qValue2, "testSG4");
    AbstractNextHop anh5 =
        new DefaultNextHop("testNw", "testHost", 0005, Transport.UDP, qValue2, "testSG5");

    TreeSet<ServerGroupElementInterface> set = new TreeSet<ServerGroupElementInterface>();
    set.add(anh1);
    set.add(anh2);
    set.add(anh3);
    set.add(anh4);
    set.add(anh5);

    List<ServerGroupElementInterface> list = new ArrayList<ServerGroupElementInterface>();
    list.addAll(set);

    ServerGroupInterface serverGroup = mock(ServerGroupInterface.class);
    Mockito.when(serverGroup.getElements()).thenReturn(set);

    AbstractSipRequest message = mock(AbstractSipRequest.class);
    Mockito.when(message.getCallId()).thenReturn("1-123456@127.0.0.1");

    callBased.setServerInfo("SG2", serverGroup, message);
    callBased.setDomainsToTry(set);

    assertEquals(callBased.getServer(), list.get(result));
  }

  @Test(
      description =
          "based on weight , lower q-values not selected , equal weights for all elements")
  public void selectElementWeighBased() {
    LBWeight callBased;
    callBased = new LBWeight();

    // create multiple Server Group Elements
    AbstractNextHop anh1 =
        new DefaultNextHop("testNw", "testHost", 0001, Transport.UDP, 0.9f, "testSG1");
    AbstractNextHop anh2 =
        new DefaultNextHop("testNw", "testHost", 0002, Transport.UDP, 0.9f, "testSG2");
    AbstractNextHop anh3 =
        new DefaultNextHop("testNw", "testHost", 0003, Transport.UDP, 0.8f, "testSG3");
    AbstractNextHop anh4 =
        new DefaultNextHop("testNw", "testHost", 0004, Transport.UDP, 0.6f, "testSG4");
    AbstractNextHop anh5 =
        new DefaultNextHop("testNw", "testHost", 0005, Transport.UDP, 0.5f, "testSG5");
    AbstractNextHop anh6 =
        new DefaultNextHop("testNw", "testHost", 0001, Transport.UDP, 0.9f, "testSG1");

    TreeSet<ServerGroupElementInterface> set = new TreeSet<ServerGroupElementInterface>();
    set.add(anh1);
    set.add(anh2);
    set.add(anh3);
    set.add(anh4);
    set.add(anh5);
    set.add(anh6);
    List<ServerGroupElementInterface> list = new ArrayList<ServerGroupElementInterface>();
    list.addAll(set);
    ServerGroupInterface serverGroup = mock(ServerGroupInterface.class);
    Mockito.when(serverGroup.getElements()).thenReturn(set);

    AbstractSipRequest message = mock(AbstractSipRequest.class);

    callBased.setServerInfo("SG2", serverGroup, message);
    callBased.setDomainsToTry(set);

    {
      logger.info(
          "  Weight of hightest q -values is < lower q - values, high q value has to be selected");
      anh1.setWeight(10);
      anh2.setWeight(10);
      anh3.setWeight(90);
      anh4.setWeight(90);
      anh5.setWeight(90);
      anh6.setWeight(90);

      DefaultNextHop dnh = (DefaultNextHop) callBased.getServer();
      assertEquals(dnh.getQValue(), 0.9f);
    }

    {
      logger.info(
          "  Weight of hightest q -values is = 0 pick next higher q- value elements whose weight is non zero ");
      anh1.setWeight(0);
      anh2.setWeight(0);
      anh3.setWeight(0);
      anh4.setWeight(90);
      anh5.setWeight(90);
      anh6.setWeight(0);

      DefaultNextHop dnh = (DefaultNextHop) callBased.getServer();
      assertEquals(dnh.getQValue(), 0.6f);
    }
    {
      logger.info(" Only one non zero element");
      anh1.setWeight(0);
      anh2.setWeight(0);
      anh3.setWeight(0);
      anh4.setWeight(0);
      anh5.setWeight(90);
      anh6.setWeight(0);

      DefaultNextHop dnh = (DefaultNextHop) callBased.getServer();

      assertEquals(dnh.getQValue(), anh5.getQValue());
    }

    {
      logger.info(
          "  Weight of hightest q -values is = 0 pick next higher q- value elements whose weight "
              + "is non zero ");
      anh1.setWeight(0);
      anh2.setWeight(0);
      anh3.setWeight(0);
      anh4.setWeight(0);
      anh5.setWeight(0);
      anh6.setWeight(0);

      assertEquals(callBased.getServer(), null);
    }
  }
}
