package com.cisco.dsb.trunk;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.cisco.dsb.common.circuitbreaker.DsbCircuitBreaker;
import com.cisco.dsb.common.config.RoutePolicy;
import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.loadbalancer.LBType;
import com.cisco.dsb.common.maintanence.MaintenancePolicy;
import com.cisco.dsb.common.servergroup.DnsServerGroupUtil;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.connectivity.monitor.service.OptionsPingController;
import com.cisco.dsb.connectivity.monitor.service.OptionsPingControllerImpl;
import com.cisco.dsb.trunk.trunks.*;
import com.cisco.dsb.trunk.util.SipParamConstants;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TrunkConfigurationPropertiesTest {

  @Test(expectedExceptions = DhruvaRuntimeException.class)
  public void testSettersAndGetters() {
    TrunkConfigurationProperties configurationProperties = new TrunkConfigurationProperties();
    CommonConfigurationProperties commonConfigurationProperties =
        mock(CommonConfigurationProperties.class);
    DnsServerGroupUtil dnsServerGroupUtil = mock(DnsServerGroupUtil.class);
    OptionsPingController opController = new OptionsPingControllerImpl();
    DsbCircuitBreaker dsbCircuitBreaker = new DsbCircuitBreaker();

    configurationProperties.setCommonConfigurationProperties(commonConfigurationProperties);
    configurationProperties.setDnsServerGroupUtil(dnsServerGroupUtil);
    configurationProperties.setOptionsPingController(opController);
    configurationProperties.setDsbCircuitBreaker(dsbCircuitBreaker);

    ServerGroup sg1 = new ServerGroup();
    sg1.setName("SG1");
    ServerGroup sg2 = new ServerGroup();
    sg2.setName("SG2");

    HashMap<String, ServerGroup> serverGroups = new HashMap<>();
    serverGroups.put("SG1", sg1);
    serverGroups.put("SG2", sg2);
    when(commonConfigurationProperties.getServerGroups()).thenReturn(serverGroups);
    RoutePolicy routePolicy = RoutePolicy.builder().build();
    when(commonConfigurationProperties.getRoutePolicyMap())
        .thenReturn(Collections.singletonMap("UsPoolA", routePolicy));
    MaintenancePolicy mPolicyWithValue =
        MaintenancePolicy.builder().setName("mPolicyWithValue").build();
    Map<String, MaintenancePolicy> maintenancePolicyMap = new HashMap<>();
    maintenancePolicyMap.put(mPolicyWithValue.getName(), mPolicyWithValue);
    when(commonConfigurationProperties.getMaintenancePolicyMap()).thenReturn(maintenancePolicyMap);

    Egress egress1 = new Egress();
    egress1.setLbType(LBType.WEIGHT);
    ServerGroups sgs1 = ServerGroups.builder().setSg("SG1").setWeight(100).setPriority(5).build();
    ServerGroups sgs2 = ServerGroups.builder().setSg("SG2").setWeight(100).setPriority(5).build();
    egress1.setServerGroups(Arrays.asList(sgs1, sgs2));
    egress1.setOverallResponseTimeout(300);
    egress1.setRoutePolicyFromConfig(routePolicy);
    PSTNTrunk pstnTrunk1 = PSTNTrunk.builder().setName("UsPoolA").setEgress(egress1).build();
    pstnTrunk1.setEnableCircuitBreaker(true);

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

    Ingress ingress3 = new Ingress();
    ingress3.setName("ingress3");
    ingress3.setMaintenancePolicy("mPolicy");
    B2BTrunk b2BTrunk = new B2BTrunk();
    b2BTrunk.setName("Antares");
    b2BTrunk.setIngress(ingress3);
    b2BTrunk.setEgress(egress1);

    B2BTrunk b2BTrunk2 = new B2BTrunk();
    b2BTrunk2.setName("beech");
    b2BTrunk2.setIngress(ingress3);
    b2BTrunk2.setEgress(egress1);

    Ingress ingress4 = new Ingress();
    ingress4.setName("ingress4");
    ingress4.setMaintenancePolicy("mPolicyWithValue");
    CallingTrunk callingTrunk = new CallingTrunk();
    callingTrunk.setName("NS1");
    callingTrunk.setIngress(ingress4);
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

    /* configuration of trunk to maintenance policy mappings is verified below */
    // UsPoolA trunk has no ingress configured, no trunkToMaintenancePolicy mapping available
    assertNull(configurationProperties.getTrunkToMaintenancePolicyMap().get("UsPoolA"));
    // UsPoolB trunk has ingress, but no mPolicy configured, so no trunkToMaintenancePolicy mapping
    // available
    assertNull(configurationProperties.getTrunkToMaintenancePolicyMap().get("UsPoolB"));
    // Antares trunk has ingress with mPolicy configured, no trunkToMaintenancePolicy mapping
    // available because the mentioned mPolicy does not exist
    assertNull(configurationProperties.getTrunkToMaintenancePolicyMap().get("Antares"));
    // NS1 trunk has ingress with mPolicy configured & mentioned mPolicy exists. So, map has entry
    // for the trunk and its associated mPolicy
    MaintenancePolicy expectedMP =
        configurationProperties.getTrunkToMaintenancePolicyMap().get("NS1");
    assertEquals(expectedMP.getName(), "mPolicyWithValue");
    assertNull(expectedMP.getDropMsgTypes());
    assertEquals(expectedMP.getResponseCode(), 0);

    pstnTrunkMap.put(
        SipParamConstants.DEFAULT_DTG_VALUE_FOR_MIDCALL,
        configurationProperties
            .getPstnTrunkMap()
            .get(SipParamConstants.DEFAULT_DTG_VALUE_FOR_MIDCALL));

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
    EqualsVerifier.simple().forClass(Ingress.class).withOnlyTheseFields("name").verify();
  }
}
