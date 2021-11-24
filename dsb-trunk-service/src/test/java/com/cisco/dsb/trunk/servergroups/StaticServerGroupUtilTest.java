package com.cisco.dsb.trunk.servergroups;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.*;

import com.cisco.dsb.trunk.config.TrunkConfigProperties;
import com.cisco.dsb.trunk.dto.ServerGroupElement;
import com.cisco.dsb.trunk.dto.StaticServer;
import java.util.Arrays;
import java.util.List;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class StaticServerGroupUtilTest {

  AbstractServerGroupRepository abstractServerGroupRepository;
  List<StaticServer> staticServer;
  TrunkConfigProperties trunkConfigProperties;

  @BeforeClass
  void init() {
    ServerGroupElement sge1 =
        ServerGroupElement.builder()
            .ipAddress("10.78.98.95")
            .port(5060)
            .qValue(0.9f)
            .weight(-1)
            .build();
    ServerGroupElement sge2 =
        ServerGroupElement.builder()
            .ipAddress("10.78.98.95")
            .port(5061)
            .qValue(0.9f)
            .weight(-1)
            .build();

    ServerGroupElement sge3 =
        ServerGroupElement.builder()
            .ipAddress("10.78.98.96")
            .port(5061)
            .qValue(0.9f)
            .weight(-1)
            .build();
    ServerGroupElement sge4 =
        ServerGroupElement.builder()
            .ipAddress("10.78.98.96")
            .port(5061)
            .qValue(0.9f)
            .weight(-1)
            .build();

    List<ServerGroupElement> sgeList = Arrays.asList(sge1, sge2);
    List<ServerGroupElement> sgeList1 = Arrays.asList(sge3, sge4);

    StaticServer server1 =
        StaticServer.builder()
            .networkName("net1")
            .serverGroupName("SG1")
            .elements(sgeList)
            .sgPolicy("policy1")
            .build();
    StaticServer server2 =
        StaticServer.builder()
            .networkName("net1")
            .serverGroupName("SG2")
            .elements(sgeList1)
            .sgPolicy("dummy")
            .build();
    staticServer = Arrays.asList(server1, server2);

    trunkConfigProperties = mock(TrunkConfigProperties.class);
    Mockito.when(trunkConfigProperties.getServerGroups()).thenReturn(staticServer);

    abstractServerGroupRepository = new AbstractServerGroupRepository();
  }

  @Test
  void getServerGroupTest() {
    trunkConfigProperties = mock(TrunkConfigProperties.class);
    Mockito.when(trunkConfigProperties.getServerGroups()).thenReturn(staticServer);

    List<StaticServer> staticServers = staticServer;

    abstractServerGroupRepository = new AbstractServerGroupRepository();

    StaticServerGroupUtil ss =
        new StaticServerGroupUtil(abstractServerGroupRepository, staticServers);
    ss.init();

    assertNotNull(ss.getServerGroup("SG1"));
    assertNull(ss.getServerGroup("dummy"));
  }

  @Test
  void addingSameGroupAgainTest() {
    trunkConfigProperties = mock(TrunkConfigProperties.class);
    Mockito.when(trunkConfigProperties.getServerGroups()).thenReturn(staticServer);
    abstractServerGroupRepository = new AbstractServerGroupRepository();

    StaticServerGroupUtil ss =
        new StaticServerGroupUtil(abstractServerGroupRepository, staticServer);
    ss.init();

    assertThrows(
        DuplicateServerGroupException.class,
        () -> ss.addServerGroup(trunkConfigProperties.getServerGroups().get(0)));
    Assert.assertFalse(ss.addServerGroups(trunkConfigProperties.getServerGroups()));
  }
}
