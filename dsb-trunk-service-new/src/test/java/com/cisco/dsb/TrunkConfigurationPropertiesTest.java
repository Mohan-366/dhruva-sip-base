package com.cisco.dsb;

import static org.testng.Assert.*;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.loadbalancer.LBType;
import com.cisco.dsb.common.servergroup.SGPolicy;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.trunks.Egress;
import com.cisco.dsb.trunks.Ingress;
import com.cisco.dsb.trunks.PSTNTrunk;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

@TestPropertySource(locations = "classpath:application-trunkconfig.yaml")
@ContextConfiguration(
    classes = {TrunkConfigurationProperties.class, CommonConfigurationProperties.class},
    initializers = ConfigFileApplicationContextInitializer.class)
@ActiveProfiles("trunkconfig")
public class TrunkConfigurationPropertiesTest extends AbstractTestNGSpringContextTests {
  @Autowired TrunkConfigurationProperties configurationProperties;

  @Test
  public void testUserDefinedValues() {
    Map<String, PSTNTrunk> trunkMap = configurationProperties.getPstnTrunks().getTrunkMap();
    assertEquals(trunkMap.size(), 2);
    assertTrue(trunkMap.containsKey("Provider1"));
    assertTrue(trunkMap.containsKey("Provider2"));
    Egress egress1 = new Egress();
    egress1.setLbType(LBType.ONCE);
    egress1.setServerGroups(Arrays.asList("SG1", "SG2"));
    Map<String, ServerGroup> serverGroupMap = egress1.getServerGroupMap();
    Map<String, SGPolicy> sgpolicy = new HashMap<>();
    sgpolicy.put(
        "policy1",
        SGPolicy.builder()
            .setName("policy1")
            .setFailoverResponseCodes(Arrays.asList(501, 502))
            .build());
    List<ServerGroupElement> sg1Elements =
        Arrays.asList(
            ServerGroupElement.builder()
                .setIpAddress("1.1.1.1")
                .setPort(5060)
                .setQValue(0.9f)
                .setWeight(10)
                .setTransport(Transport.UDP)
                .build(),
            ServerGroupElement.builder()
                .setIpAddress("2.2.2.2")
                .setPort(5070)
                .setQValue(0.9f)
                .setWeight(10)
                .setTransport(Transport.UDP)
                .build());
    ServerGroup sg1 =
        ServerGroup.builder()
            .setName("SG1")
            .setLbType(LBType.WEIGHT)
            .setNetworkName("testNetwork")
            .setElements(sg1Elements)
            .setSgPolicy(sgpolicy.get("policy1"))
            .setQValue(0.9f)
            .setWeight(100)
            .build();
    ServerGroup sg2 =
        ServerGroup.builder()
            .setName("SG2")
            .setLbType(LBType.WEIGHT)
            .setNetworkName("testNetwork")
            .setElements(sg1Elements)
            .setSgPolicy(sgpolicy.get("policy1"))
            .setQValue(0.9f)
            .setWeight(50)
            .build();
    serverGroupMap.put("SG1", sg1);
    serverGroupMap.put("SG2", sg2);
    PSTNTrunk provider1 =
        PSTNTrunk.builder()
            .setName("Provider1")
            .setIngress(new Ingress("ingress1"))
            .setEgress(egress1)
            .build();
    assertEquals(provider1, trunkMap.get("Provider1"));
  }
}
