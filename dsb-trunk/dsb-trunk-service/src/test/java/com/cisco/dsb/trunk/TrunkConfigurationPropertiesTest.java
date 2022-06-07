package com.cisco.dsb.trunk;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.loadbalancer.LBType;
import com.cisco.dsb.common.servergroup.DnsServerGroupUtil;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.connectivity.monitor.service.OptionsPingController;
import com.cisco.dsb.connectivity.monitor.service.OptionsPingControllerImpl;
import com.cisco.dsb.trunk.trunks.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TrunkConfigurationPropertiesTest {

  @Test(expectedExceptions = DhruvaRuntimeException.class)
  public void testSettersAndGetters() {
    TrunkConfigurationProperties configurationProperties = new TrunkConfigurationProperties();
    CommonConfigurationProperties commonConfigurationProperties =
        Mockito.mock(CommonConfigurationProperties.class);
    DnsServerGroupUtil dnsServerGroupUtil = Mockito.mock(DnsServerGroupUtil.class);
    OptionsPingController opController = new OptionsPingControllerImpl();

    configurationProperties.setCommonConfigurationProperties(commonConfigurationProperties);
    configurationProperties.setDnsServerGroupUtil(dnsServerGroupUtil);
    configurationProperties.setOptionsPingController(opController);

    ServerGroup sg1 = new ServerGroup();
    sg1.setName("SG1");
    ServerGroup sg2 = new ServerGroup();
    sg2.setName("SG2");

    HashMap<String, ServerGroup> serverGroups = new HashMap<>();
    serverGroups.put("SG1", sg1);
    serverGroups.put("SG2", sg2);
    Mockito.when(commonConfigurationProperties.getServerGroups()).thenReturn(serverGroups);

    Ingress ingress1 = new Ingress();
    ingress1.setName("ingress1");
    Egress egress1 = new Egress();
    egress1.setLbType(LBType.WEIGHT);
    ServerGroups sgs1 = ServerGroups.builder().setSg("SG1").setWeight(100).setPriority(5).build();
    ServerGroups sgs2 = ServerGroups.builder().setSg("SG2").setWeight(100).setPriority(5).build();

    egress1.setServerGroups(Arrays.asList(sgs1, sgs2));
    egress1.setOverallResponseTimeout(300);
    PSTNTrunk pstnTrunk1 =
        PSTNTrunk.builder().setName("UsPoolA").setIngress(ingress1).setEgress(egress1).build();
    Ingress ingress2 = new Ingress();
    ingress2.setName("ingress2");
    Egress egress2 = new Egress();
    Map<String, String> selector2 = new HashMap<>();

    egress2.setLbType(LBType.WEIGHT);
    selector2.put("dtg", "CCPFUSIONIN");
    egress2.setSelector(selector2);
    egress2.setServerGroups(Arrays.asList(sgs1, sgs2));
    egress2.setOverallResponseTimeout(300);
    PSTNTrunk pstnTrunk2 =
        PSTNTrunk.builder().setName("UsPoolB").setIngress(ingress2).setEgress(egress2).build();
    B2BTrunk b2BTrunk = new B2BTrunk();
    b2BTrunk.setName("Antares");
    b2BTrunk.setIngress(ingress1);
    b2BTrunk.setEgress(egress1);

    B2BTrunk b2BTrunk2 = new B2BTrunk();
    b2BTrunk.setName("beech");
    b2BTrunk.setIngress(ingress1);
    b2BTrunk.setEgress(egress1);

    CallingTrunk callingTrunk = new CallingTrunk();
    callingTrunk.setName("NS1");
    callingTrunk.setIngress(ingress1);
    callingTrunk.setEgress(egress1);

    Map<String, PSTNTrunk> pstnTrunkMap = new HashMap<>();
    Map<String, B2BTrunk> b2bTrunkMap = new HashMap<>();
    Map<String, CallingTrunk> ccTrunkMap = new HashMap<>();

    pstnTrunkMap.put("UsPoolA", pstnTrunk1);
    pstnTrunkMap.put("UsPoolB", pstnTrunk2);
    b2bTrunkMap.put("Antares", b2BTrunk);
    b2bTrunkMap.put("beech", b2BTrunk2);
    ccTrunkMap.put("NS1", callingTrunk);

    configurationProperties.setPSTN(pstnTrunkMap);
    configurationProperties.setB2B(b2bTrunkMap);
    configurationProperties.setCallingCore(ccTrunkMap);

    Assert.assertEquals(pstnTrunkMap, configurationProperties.getPstnTrunkMap());
    Assert.assertEquals(
        b2bTrunkMap.get("Antares"), configurationProperties.getB2BTrunkMap().get("Antares"));
    Assert.assertEquals(ccTrunkMap, configurationProperties.getCallingTrunkMap());
    ServerGroups sgs3 = ServerGroups.builder().setSg("SG3").setWeight(80).setPriority(5).build();

    egress1.setServerGroups(Collections.singletonList(sgs3));
    // throws exception as SG is not present in commonConfiguration
    configurationProperties.setPSTN(pstnTrunkMap);
  }

  @Test
  public void testEqualsAndHashCodeOfEgressIngress() {
    // Test Egress
    EqualsVerifier.simple()
        .forClass(Egress.class)
        .withOnlyTheseFields("lbType", "serverGroupsConfig")
        .verify();
    // Test Ingress
    EqualsVerifier.simple().forClass(Ingress.class).verify();
  }
}
