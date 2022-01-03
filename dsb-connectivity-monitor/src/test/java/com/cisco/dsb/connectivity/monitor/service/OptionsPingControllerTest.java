package com.cisco.dsb.connectivity.monitor.service;

import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.servergroup.Pingable;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.transport.Transport;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class OptionsPingControllerTest {

  @InjectMocks OptionsPingController optionsPingController = new OptionsPingControllerImpl();
  @Spy OptionsPingMonitor optionsPingMonitor = new OptionsPingMonitor();
  @Mock CommonConfigurationProperties commonConfigurationProperties;

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
            .setElements(sgeList)
            .setSgPolicyConfig("global")
            .setPingOn(true)
            .build();

    map = new HashMap<>();
    map.put(server1.getHostName(), server1);
  }

  @Test(description = "test status of SGE")
  void testSGE() {

    optionsPingMonitor.elementStatus.put(sge1.hashCode(), true);
    optionsPingMonitor.elementStatus.put(sge2.hashCode(), false);
    optionsPingMonitor.elementStatus.put(sge3.hashCode(), true);
    //  optionsPingMonitor.elementStatus.put(sge3.hashCode(), false);

    assertTrue(optionsPingController.getStatus(sge1));
    assertFalse(optionsPingController.getStatus(sge2));

    assertTrue(optionsPingController.getStatus(sge3));

    // if no such element is present in statusMap , return true
    assertTrue(optionsPingController.getStatus(sge4));
  }

  @Test(description = "test status of ServerGroup")
  void testSG() {
    optionsPingMonitor.serverGroupStatus.put(server1.getHostName(), false);
    // turning on one element , SG should be UP
    Assert.assertFalse(optionsPingController.getStatus(server1));
    optionsPingMonitor.elementStatus.put(sge4.hashCode(), true);
    optionsPingMonitor.serverGroupStatus.put(server1.getHostName(), true);

    //
    assertTrue(optionsPingController.getStatus(server1));

    // if no such element is present in statusMap , return true

  }

  @Test(description = "Negative case when a non SG or SGE element status is requested")
  public void testWhenNotSGOrSGEGetStatus() {

    assertFalse(optionsPingController.getStatus(new NewPingable()));
  }

  class NewPingable implements Pingable {
    public NewPingable() {}
  }
}
