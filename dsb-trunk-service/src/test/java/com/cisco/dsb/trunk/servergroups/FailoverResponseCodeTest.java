package com.cisco.dsb.trunk.servergroups;

import static org.mockito.Mockito.mock;

import com.cisco.dsb.trunk.config.TrunkConfigProperties;
import com.cisco.dsb.trunk.dto.DynamicServer;
import com.cisco.dsb.trunk.dto.SGPolicy;
import com.cisco.dsb.trunk.dto.StaticServer;
import java.util.Arrays;
import java.util.List;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class FailoverResponseCodeTest {
  List<StaticServer> staticServer;
  List<DynamicServer> dynamicServers;

  List<SGPolicy> sgPolicyList;

  TrunkConfigProperties trunkConfigProperties;

  @BeforeClass
  void init() {

    StaticServer server1 =
        StaticServer.builder()
            .networkName("net1")
            .serverGroupName("SG1")
            .sgPolicy("policy1")
            .build();
    StaticServer server2 =
        StaticServer.builder().networkName("net1").serverGroupName("SG2").sgPolicy("dummy").build();

    DynamicServer dynamicServer1 =
        DynamicServer.builder().serverGroupName("cisco.webex.com").sgPolicy("policy1").build();

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

    staticServer = Arrays.asList(server1, server2);
    sgPolicyList = Arrays.asList(sgPolicy1, sgPolicy2, global);
    dynamicServers = Arrays.asList(dynamicServer1);
  }

  @Test(
      description =
          "errorCodes for both static and dynamic SGs, return true only if policy is associated "
              + "and contains errorCode")
  void errorCodeTest() {
    trunkConfigProperties = mock(TrunkConfigProperties.class);

    Mockito.when(trunkConfigProperties.getServerGroups()).thenReturn(staticServer);
    Mockito.when(trunkConfigProperties.getDynamicServerGroups()).thenReturn(dynamicServers);

    Mockito.when(trunkConfigProperties.getSGPolicies()).thenReturn(sgPolicyList);

    FailoverResponseCode failoverResponseCode =
        new FailoverResponseCode(staticServer, dynamicServers, sgPolicyList);

    // SG name present
    Assert.assertTrue(failoverResponseCode.isCodeInFailoverCodeSet("SG1", 501));

    // SG name present, code not in SGPolicy
    Assert.assertFalse(failoverResponseCode.isCodeInFailoverCodeSet("SG1", 509));

    // SG name from dynamic SG -> policy1 configured for dynamic SG
    Assert.assertTrue(failoverResponseCode.isCodeInFailoverCodeSet("cisco.webex.com", 501));
    Assert.assertFalse(failoverResponseCode.isCodeInFailoverCodeSet("cisco.webex.com", 599));

    // SG name not present, code in global
    Assert.assertTrue(failoverResponseCode.isCodeInFailoverCodeSet("go.webex.com", 599));

    //  SG name not present, code not in global
    Assert.assertFalse(failoverResponseCode.isCodeInFailoverCodeSet("go.webex.com", 566));
  }

  @Test(description = " when no policy is associated for SG, return false")
  void errorCodeNoPolicy() {

    FailoverResponseCode failoverResponseCode = new FailoverResponseCode(null, null, null);
    Assert.assertFalse(failoverResponseCode.isCodeInFailoverCodeSet("go.webex.com", 599));
  }
}
