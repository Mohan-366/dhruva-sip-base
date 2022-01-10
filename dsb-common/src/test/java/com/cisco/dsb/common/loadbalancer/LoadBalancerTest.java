package com.cisco.dsb.common.loadbalancer;

import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.transport.Transport;
import com.google.common.collect.Comparators;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class LoadBalancerTest {
  private SecureRandom random = new SecureRandom();

  @BeforeTest
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @DataProvider(name = "lbTypesSGE")
  private Object[][] getLbTypeSGE() {
    List<ServerGroupElement> serverGroupElements = getServerGroupElements(50, false);
    List<ServerGroupElement> serverGroupElements1 = getServerGroupElements(1, false);
    List<ServerGroupElement> serverGroupElements2 = getServerGroupElements(0, false);
    return new Object[][] {
      {LBType.HIGHEST_Q, serverGroupElements},
      {LBType.WEIGHT, serverGroupElements},
      {LBType.ONCE, serverGroupElements},
      {LBType.HIGHEST_Q, serverGroupElements2},
      {LBType.HIGHEST_Q, serverGroupElements1},
      {LBType.WEIGHT, serverGroupElements1},
      {LBType.ONCE, serverGroupElements1}
    };
  }

  @DataProvider(name = "lbTypesSG")
  private Object[][] getLbTypeSG() {
    List<ServerGroup> serverGroup = getServerGroups(50);
    List<ServerGroup> serverGroup1 = getServerGroups(1);
    List<ServerGroup> serverGroup2 = getServerGroups(0);
    return new Object[][] {
      {LBType.HIGHEST_Q, serverGroup},
      {LBType.WEIGHT, serverGroup},
      {LBType.ONCE, serverGroup},
      {LBType.HIGHEST_Q, serverGroup2},
      {LBType.HIGHEST_Q, serverGroup1},
      {LBType.WEIGHT, serverGroup1},
      {LBType.ONCE, serverGroup1}
    };
  }

  @Test(
      description = "Testing loadbalancing across ServerGroupsElements",
      dataProvider = "lbTypesSGE")
  public void SGELoadBalancing(LBType lbType, List<ServerGroupElement> sgelements) {
    ServerGroup serverGroup =
        ServerGroup.builder().setElements(sgelements).setLbType(lbType).build();
    LoadBalancer loadBalancer = LoadBalancer.of(serverGroup);
    ArrayList<ServerGroupElement> treeSetInit = new ArrayList(loadBalancer.getElementsToTry());
    List<ServerGroupElement> selectedElements = new ArrayList<>();
    ServerGroupElement selectedElement = (ServerGroupElement) loadBalancer.getCurrentElement();
    while (selectedElement != null) {
      selectedElements.add(selectedElement);
      selectedElement = (ServerGroupElement) loadBalancer.getNextElement();
    }
    switch (lbType) {
      case WEIGHT:
      case HIGHEST_Q:
        if (treeSetInit.size() > 1) {
          Assert.assertFalse(Comparators.isInOrder(selectedElements, weightComparator()));
          Assert.assertNotEquals(selectedElements, treeSetInit);
        }
        Assert.assertEquals(selectedElements.size(), sgelements.size());
        break;
      case ONCE:
        Assert.assertEquals(selectedElements.size(), 1);
        break;
    }
  }

  @Test(description = "Testing loadbalancing across ServerGroups", dataProvider = "lbTypesSG")
  public void SGLoadBalancerTest(LBType lbType, List<ServerGroup> serverGroups) {
    LoadBalancable loadBalancable =
        new LoadBalancable() {
          @Override
          public List<ServerGroup> getElements() {
            return serverGroups;
          }

          @Override
          public LBType getLbType() {
            return lbType;
          }
        };
    LoadBalancer loadBalancer = LoadBalancer.of(loadBalancable);
    ArrayList<ServerGroup> treeSetInit = new ArrayList(loadBalancer.getElementsToTry());
    List<ServerGroup> selectedElements = new ArrayList<>();
    ServerGroup selectedElement = (ServerGroup) loadBalancer.getCurrentElement();
    while (selectedElement != null) {
      selectedElements.add(selectedElement);
      selectedElement = (ServerGroup) loadBalancer.getNextElement();
    }
    switch (lbType) {
      case ONCE:
        Assert.assertEquals(selectedElements.size(), 1);
        break;
      case HIGHEST_Q:
      case WEIGHT:
        if (treeSetInit.size() > 1) {
          Assert.assertFalse(Comparators.isInOrder(selectedElements, weightComparator()));
          Assert.assertNotEquals(selectedElements, treeSetInit);
        }
        Assert.assertEquals(selectedElements.size(), serverGroups.size());
        break;
    }
  }

  @Test(retryAnalyzer = LbWeightRetryAnalyser.class)
  public void testLBWeight() {
    LBType lbType = LBType.WEIGHT;
    List<ServerGroupElement> sgelements = getServerGroupElements(1000, true);
    ServerGroup serverGroup =
        ServerGroup.builder().setElements(sgelements).setLbType(lbType).build();

    int[] count = {0, 0, 0}; // 50 is 0, 30 is 1, 30 is 2
    // distribution for 1000 calls is ~ { 625+-=~4%, 312+-~5% , 63+=~10% } (error allowance in %)
    int test = 0;
    while (test < 1000) {
      switch (LoadBalancer.of(serverGroup).getCurrentElement().getWeight()) {
        case 50:
          count[0]++;
          break;
        case 30:
          count[1]++;
          break;
        case 20:
          count[2]++;
          break;
      }
      test++;
    }
    System.out.println(Arrays.toString(count));
    Assert.assertTrue(450 <= count[0] && count[0] <= 550); // 5%
    Assert.assertTrue(270 <= count[1] && count[1] <= 330); // 10%
    Assert.assertTrue(150 <= count[2] && count[2] <= 250); // ~15%
  }

  private List<ServerGroupElement> getServerGroupElements(int count, boolean sameQ) {
    List<ServerGroupElement> sgeList = new ArrayList<>();
    List<Transport> transports = Arrays.asList(Transport.TCP, Transport.UDP, Transport.TLS);
    int[] qValues;
    if (sameQ) qValues = new int[] {10};
    else qValues = new int[] {10, 20, 30};

    int[] weights = {50, 30, 20};
    for (int i = 0; i < count; i++) {
      String ipAddress =
          random.nextInt(255)
              + "."
              + random.nextInt(255)
              + "."
              + random.nextInt(255)
              + "."
              + random.nextInt(255);
      int port = random.nextInt(1000);
      Transport transport = transports.get(random.nextInt(transports.size()));
      int qvalue = qValues[random.nextInt(qValues.length)];
      int weight = weights[i % weights.length];

      ServerGroupElement element =
          ServerGroupElement.builder()
              .setIpAddress(ipAddress)
              .setPort(port)
              .setTransport(transport)
              .setPriority(qvalue)
              .setWeight(weight)
              .build();
      sgeList.add(element);
    }
    return sgeList;
  }

  private List<ServerGroup> getServerGroups(int count) {
    List<ServerGroup> serverGroups = new ArrayList<>();
    int[] qValues = {10, 20, 30};
    int[] weights = {100, 50, 10};
    for (int i = 0; i < count; i++) {
      String name = "test_" + i;
      int qValue = qValues[random.nextInt(3)];
      int weight = weights[random.nextInt(3)];
      ServerGroup sg = new ServerGroup();
      sg.setHostName(name);
      sg.setPriority(qValue);
      sg.setWeight(weight);
      serverGroups.add(sg);
    }
    return serverGroups;
  }

  private Comparator<LBElement> weightComparator() {
    return (o1, o2) -> {
      int compare;
      if ((compare = Float.compare(o2.getPriority(), o1.getPriority())) != 0) {
        return compare;
      }
      return Integer.compare(o2.getWeight(), o1.getWeight());
    };
  }
}

class LbWeightRetryAnalyser implements IRetryAnalyzer {

  int counter = 0;
  int retryLimit = 5;

  @Override
  public boolean retry(ITestResult iTestResult) {
    if (counter < retryLimit) {
      counter++;
      return true;
    }
    return false;
  }
}
