package com.cisco.dsb.servergroups;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.*;

import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.sip.stack.dto.SGPolicy;
import com.cisco.dsb.sip.stack.dto.ServerGroupElement;
import com.cisco.dsb.sip.stack.dto.StaticServer;
import java.util.Arrays;
import java.util.List;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class StaticServerGroupUtilTest {

  AbstractServerGroupRepository abstractServerGroupRepository;
  List<StaticServer> staticServer;
  List<SGPolicy> sgPolicyList;
  DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

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

    SGPolicy sgPolicy1 =
        SGPolicy.builder()
            .name("policy1")
            .lbType("call-type")
            .failoverResponseCodes(Arrays.asList(501, 502, 503))
            .build();

    SGPolicy sgPolicy2 =
        SGPolicy.builder()
            .name("policy2")
            .lbType("call-type")
            .failoverResponseCodes(Arrays.asList(501, 502))
            .build();

    SGPolicy global =
        SGPolicy.builder()
            .name("global")
            .lbType("call-type")
            .failoverResponseCodes(Arrays.asList(499, 599))
            .build();

    List<ServerGroupElement> sgeList = Arrays.asList(sge1, sge2);
    List<ServerGroupElement> sgeList1 = Arrays.asList(sge3, sge4);

    StaticServer server1 =
        StaticServer.builder()
            .networkName("net1")
            .serverGroupName("SG1")
            .lbType("call-id")
            .elements(sgeList)
            .sgPolicy("policy1")
            .build();
    StaticServer server2 =
        StaticServer.builder()
            .networkName("net1")
            .serverGroupName("SG2")
            .lbType("call-id")
            .elements(sgeList1)
            .sgPolicy("dummy")
            .build();
    staticServer = Arrays.asList(server1, server2);
    sgPolicyList = Arrays.asList(sgPolicy1, sgPolicy2, global);

    dhruvaSIPConfigProperties = mock(DhruvaSIPConfigProperties.class);
    Mockito.when(dhruvaSIPConfigProperties.getServerGroups()).thenReturn(staticServer);
    Mockito.when(dhruvaSIPConfigProperties.getSGPolicies()).thenReturn(sgPolicyList);

    abstractServerGroupRepository = new AbstractServerGroupRepository();
  }

  @Test
  void getServerGroupTest() {
    dhruvaSIPConfigProperties = mock(DhruvaSIPConfigProperties.class);
    Mockito.when(dhruvaSIPConfigProperties.getServerGroups()).thenReturn(staticServer);
    Mockito.when(dhruvaSIPConfigProperties.getSGPolicies()).thenReturn(sgPolicyList);

    List<StaticServer> staticServers = staticServer;
    List<SGPolicy> sgPolicies = sgPolicyList;

    abstractServerGroupRepository = new AbstractServerGroupRepository();

    StaticServerGroupUtil ss =
        new StaticServerGroupUtil(abstractServerGroupRepository, staticServers, sgPolicies);
    ss.init();

    assertNotNull(ss.getServerGroup("SG1"));
    assertNull(ss.getServerGroup("dummy"));
  }

  @Test
  void addingSameGroupAgainTest() {
    dhruvaSIPConfigProperties = mock(DhruvaSIPConfigProperties.class);
    Mockito.when(dhruvaSIPConfigProperties.getServerGroups()).thenReturn(staticServer);
    Mockito.when(dhruvaSIPConfigProperties.getSGPolicies()).thenReturn(sgPolicyList);
    abstractServerGroupRepository = new AbstractServerGroupRepository();

    StaticServerGroupUtil ss =
        new StaticServerGroupUtil(abstractServerGroupRepository, staticServer, sgPolicyList);
    ss.init();

    assertThrows(
        DuplicateServerGroupException.class,
        () -> ss.addServerGroup(dhruvaSIPConfigProperties.getServerGroups().get(0)));
    Assert.assertFalse(ss.addServerGroups(dhruvaSIPConfigProperties.getServerGroups()));
  }

  @Test
  void errorCodeTest() {
    dhruvaSIPConfigProperties = mock(DhruvaSIPConfigProperties.class);

    Mockito.when(dhruvaSIPConfigProperties.getServerGroups()).thenReturn(staticServer);
    Mockito.when(dhruvaSIPConfigProperties.getSGPolicies()).thenReturn(sgPolicyList);

    abstractServerGroupRepository = new AbstractServerGroupRepository();
    StaticServerGroupUtil ss =
        new StaticServerGroupUtil(abstractServerGroupRepository, staticServer, sgPolicyList);
    ss.init();

    // SG name present
    Assert.assertTrue(ss.isCodeInFailoverCodeSet("SG1", 501));

    // SG name present, code not in SGPolicy

    Assert.assertFalse(ss.isCodeInFailoverCodeSet("SG1", 509));

    // SG name present, SGPolicy is Invalid/null [gets from global]

    Assert.assertFalse(ss.isCodeInFailoverCodeSet("SG2", 509));
    Assert.assertTrue(ss.isCodeInFailoverCodeSet("SG2", 599));

    // SG name not present, code in global

    Assert.assertTrue(ss.isCodeInFailoverCodeSet("cisco.webex.com", 599));

    //  SG name not present, code not in global

    Assert.assertFalse(ss.isCodeInFailoverCodeSet("cisco.webex.com", 566));
  }

  @Test
  void errorCodeNoPolicy() {

    dhruvaSIPConfigProperties = mock(DhruvaSIPConfigProperties.class);

    Mockito.when(dhruvaSIPConfigProperties.getServerGroups()).thenReturn(null);
    Mockito.when(dhruvaSIPConfigProperties.getSGPolicies()).thenReturn(null);
    abstractServerGroupRepository = new AbstractServerGroupRepository();
    //    staticServer = null;
    //    sgPolicyList = null;
    StaticServerGroupUtil ss = new StaticServerGroupUtil(abstractServerGroupRepository, null, null);
    ss.init();
    Assert.assertFalse(ss.isCodeInFailoverCodeSet("cisco.webex.com", 599));
  }
}
