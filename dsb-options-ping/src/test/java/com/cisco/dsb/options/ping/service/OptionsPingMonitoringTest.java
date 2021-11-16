package com.cisco.dsb.options.ping.service;

import com.cisco.dsb.common.sip.stack.dto.ServerGroupElement;
import com.cisco.dsb.options.ping.sip.OptionsPingTransaction;
import com.cisco.dsb.proxy.sip.ProxyPacketProcessor;
import com.cisco.dsb.trunk.dto.StaticServer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

@Ignore
public class OptionsPingMonitoringTest {

  @Spy ProxyPacketProcessor proxyPacketProcessor = new ProxyPacketProcessor();
  @Spy OptionsPingTransaction optionsPingTransaction = new OptionsPingTransaction();
  @InjectMocks OptionsPingMonitor optionsPingMonitor = new OptionsPingMonitor();

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testOptionsPing() throws InterruptedException {
    ServerGroupElement sge1 =
        com.cisco.dsb.common.sip.stack.dto.ServerGroupElement.builder()
            .ipAddress("10.78.98.54")
            .port(5060)
            .qValue(0.9f)
            .weight(-1)
            .build();
    ServerGroupElement sge2 =
        com.cisco.dsb.common.sip.stack.dto.ServerGroupElement.builder()
            .ipAddress("10.78.98.54")
            .port(5061)
            .qValue(0.9f)
            .weight(-1)
            .build();
    /*
        com.cisco.dsb.common.sip.stack.dto.ServerGroupElement sge3 =
            com.cisco.dsb.common.sip.stack.dto.ServerGroupElement.builder()
                .ipAddress("3.3.3.3")
                .port(5061)
                .qValue(0.9f)
                .weight(-1)
                .build();
        com.cisco.dsb.common.sip.stack.dto.ServerGroupElement sge4 =
            ServerGroupElement.builder()
                .ipAddress("4.4.4.4")
                .port(5061)
                .qValue(0.9f)
                .weight(-1)
                .build();
    */
    List<ServerGroupElement> sgeList = Arrays.asList(sge1, sge2);
    //    List<ServerGroupElement> sgeList1 = Arrays.asList(sge3, sge4);

    StaticServer server1 =
        StaticServer.builder()
            .networkName("net1")
            .serverGroupName("SG1")
            .elements(sgeList)
            .sgPolicy("global")
            .build();
    //    StaticServer server2 =
    //        StaticServer.builder()
    //            .networkName("net1")
    //            .serverGroupName("SG2")
    //            .elements(sgeList1)
    //            .sgPolicy("global")
    //            .build();

    Map<String, StaticServer> map = new HashMap<>();
    map.put(server1.getServerGroupName(), server1);
    //    map.put(server2.getServerGroupName(), server2);

    //    optionsPingMonitor.init(map);
    //    Thread.sleep(10000);
  }
}
