package com.cisco.dsb.connectivity.monitor.service;

import static org.testng.Assert.*;

import com.cisco.dsb.common.servergroup.Pingable;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.connectivity.monitor.dto.Status;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mockito.*;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class OptionsPingControllerTest {

  @Spy @InjectMocks OptionsPingMonitor optionsPingMonitor;
  @Mock MetricService metricService;

  Map<String, ServerGroup> map;
  ServerGroupElement sge1;
  ServerGroupElement sge2;
  ServerGroupElement sge3;
  ServerGroupElement sge4;
  ServerGroup server1;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    sge1 =
        ServerGroupElement.builder()
            .setIpAddress("10.78.98.54")
            .setPort(5060)
            .setPriority(10)
            .setWeight(-1)
            .setTransport(Transport.TLS)
            .build();
    sge2 =
        ServerGroupElement.builder()
            .setIpAddress("10.78.98.54")
            .setPort(5061)
            .setPriority(10)
            .setWeight(-1)
            .setTransport(Transport.TLS)
            .build();

    sge3 =
        ServerGroupElement.builder()
            .setIpAddress("3.3.3.3")
            .setPort(5061)
            .setPriority(10)
            .setWeight(-1)
            .setTransport(Transport.TLS)
            .build();
    sge4 =
        ServerGroupElement.builder()
            .setIpAddress("4.4.4.4")
            .setPort(5061)
            .setPriority(10)
            .setWeight(-1)
            .setTransport(Transport.TLS)
            .build();

    List<ServerGroupElement> sgeList = Arrays.asList(sge1, sge2, sge3, sge4);

    server1 =
        ServerGroup.builder()
            .setHostName("net1")
            .setName("net1")
            .setElements(sgeList)
            .setRoutePolicyConfig("global")
            .setPingOn(true)
            .build();

    ServerGroup server2 =
        ServerGroup.builder()
            .setHostName("net2")
            .setName("net2")
            .setElements(sgeList)
            .setRoutePolicyConfig("global")
            .setPingOn(true)
            .build();

    map = new HashMap<>();
    map.put(server1.getName(), server1);
    map.put(server2.getName(), server2);
  }

  @BeforeMethod
  void cleanUp() {
    optionsPingMonitor.serverGroupStatus.clear();
    optionsPingMonitor.elementStatus.clear();
    Mockito.reset(optionsPingMonitor);
  }

  @Test(description = "test status of SGE")
  void testSGE() {
    OptionsPingControllerImpl optionsPingController = new OptionsPingControllerImpl();
    optionsPingController.setOptionsPingMonitor(optionsPingMonitor);
    optionsPingMonitor.elementStatus.put(sge1.toUniqueElementString(), new Status(true, 0));
    optionsPingMonitor.elementStatus.put(sge2.toUniqueElementString(), new Status(false, 0));
    optionsPingMonitor.elementStatus.put(sge3.toUniqueElementString(), new Status(true, 0));
    //  optionsPingMonitor.elementStatus.put(sge3.hashCode(), false);

    assertTrue(optionsPingController.getStatus(sge1));
    assertFalse(optionsPingController.getStatus(sge2));

    assertTrue(optionsPingController.getStatus(sge3));

    // if no such element is present in statusMap , return true
    assertTrue(optionsPingController.getStatus(sge4));
  }

  @Test(description = "test status of ServerGroup")
  void testSG() {
    OptionsPingControllerImpl optionsPingController = new OptionsPingControllerImpl();
    optionsPingController.setOptionsPingMonitor(optionsPingMonitor);
    optionsPingMonitor.serverGroupStatus.put(server1.getName(), false);
    // turning on one element , SG should be UP
    Assert.assertFalse(optionsPingController.getStatus(server1));
    optionsPingMonitor.elementStatus.put(sge4.toUniqueElementString(), new Status(true, 0));
    optionsPingMonitor.serverGroupStatus.put(server1.getName(), true);

    //
    assertTrue(optionsPingController.getStatus(server1));

    // if no such element is present in statusMap , return true

  }

  @Test(description = "Negative case when a non SG or SGE element status is requested")
  public void testWhenNotSGOrSGEGetStatus() {
    OptionsPingControllerImpl optionsPingController = new OptionsPingControllerImpl();
    optionsPingController.setOptionsPingMonitor(optionsPingMonitor);
    assertFalse(optionsPingController.getStatus(new NewPingable()));
  }

  class NewPingable implements Pingable {
    public NewPingable() {}
  }

  @Test(description = "API test to start OPTIONS towards Servergroup")
  public void testStartPing() {
    OptionsPingControllerImpl optionsPingController = new OptionsPingControllerImpl();
    optionsPingController.setOptionsPingMonitor(optionsPingMonitor);
    optionsPingController.startPing(map.get("net1"));
    Mockito.verify(optionsPingMonitor, Mockito.times(1)).pingPipeLine(map.get("net1"));
  }

  @Test(description = "Do not start new pipeline if one already exists")
  public void testStartPingDuplicate() {
    OptionsPingControllerImpl optionsPingController = new OptionsPingControllerImpl();
    optionsPingController.setOptionsPingMonitor(optionsPingMonitor);
    Mockito.doNothing().when(optionsPingMonitor).pingPipeLine(Mockito.any(ServerGroup.class));
    optionsPingController.startPing(map.get("net1"));
    optionsPingController.startPing(map.get("net1"));
    optionsPingController.startPing(map.get("net2"));
    Mockito.verify(optionsPingMonitor, Mockito.times(1)).pingPipeLine(map.get("net1"));
    Mockito.verify(optionsPingMonitor, Mockito.times(1)).pingPipeLine(map.get("net2"));
  }

  @Test(description = "Status test of duplicate SG")
  public void testDuplicateSgStatus() {
    OptionsPingControllerImpl optionsPingController = new OptionsPingControllerImpl();
    optionsPingController.setOptionsPingMonitor(optionsPingMonitor);

    optionsPingMonitor.serverGroupStatus.put("net1", false);
    Mockito.doNothing().when(optionsPingMonitor).pingPipeLine(Mockito.any(ServerGroup.class));

    ServerGroup sg1 = map.get("net1");
    ServerGroup duplicate = sg1.toBuilder().setName("duplicate").build();

    optionsPingController.startPing(sg1);
    optionsPingController.startPing(duplicate);

    assertFalse(optionsPingController.getStatus(sg1));
    assertFalse(optionsPingController.getStatus(duplicate));
  }
}
