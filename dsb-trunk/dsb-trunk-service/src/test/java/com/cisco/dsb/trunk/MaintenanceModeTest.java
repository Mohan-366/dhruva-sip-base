package com.cisco.dsb.trunk;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.maintanence.Maintenance;
import com.cisco.dsb.common.maintanence.MaintenancePolicy;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.trunks.AbstractTrunk;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.util.HashMap;
import java.util.Map;
import javax.sip.InvalidArgumentException;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.message.Response;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class MaintenanceModeTest {

  MaintenanceModeImpl maintenanceMode;
  Maintenance mConfig;
  TrunkConfigurationProperties trunkConfigurationProperties;
  ProxySIPRequest proxySIPRequest;
  AbstractTrunk trunk;

  @BeforeMethod
  public void setup() {
    mConfig = Maintenance.MaintenanceBuilder().build();
    trunkConfigurationProperties = mock(TrunkConfigurationProperties.class);
    trunk = mock(AbstractTrunk.class);
    proxySIPRequest = mock(ProxySIPRequest.class);

    maintenanceMode = MaintenanceModeImpl.getInstance(mConfig, trunkConfigurationProperties, trunk);
  }

  @Test(
      description =
          "condition for triggering/not triggering maintenance mode behaviour is evaluated")
  public void testMaintenanceEntryCheck() {
    SIPRequest request = mock(SIPRequest.class);

    // maintenance mode enabled & non mid-dialog requests
    mConfig.setEnabled(true);
    when(proxySIPRequest.getRequest()).thenReturn(request);
    when(request.getToTag()).thenReturn(null);
    assertTrue(maintenanceMode.isInMaintenanceMode().test(proxySIPRequest));

    // maintenance mode enabled & mid-dialog requests
    when(request.getToTag()).thenReturn("abcd1234");
    assertFalse(maintenanceMode.isInMaintenanceMode().test(proxySIPRequest));

    // maintenance mode is not enabled
    mConfig.setEnabled(false);
    assertFalse(maintenanceMode.isInMaintenanceMode().test(proxySIPRequest));
  }

  @Test(
      description =
          "checks the scenarios when a trunk's maintenance policy does not apply. Instead global/default behaviour will be invoked")
  public void testTrunkMaintenancePolicyNotTakingEffect() {
    MaintenanceModeImpl spyMaintenanceMode = spy(maintenanceMode);

    // trunk, maintenancePolicy map is empty (maintenance policies are not available)
    Map<String, MaintenancePolicy> trunkToMPMap = new HashMap<>();
    when(trunkConfigurationProperties.getTrunkToMaintenancePolicyMap()).thenReturn(trunkToMPMap);
    triggerAndVerifyMaintenanceBehaviourNotInvoked(spyMaintenanceMode, 1);

    // trunk, maintenancePolicy map is !empty, (policy configured in the trunk is not available)
    MaintenancePolicy mPolicy = MaintenancePolicy.builder().setName("mPolicy").build();
    trunkToMPMap.put("trunkWithMP", mPolicy);
    when(trunkConfigurationProperties.getTrunkToMaintenancePolicyMap()).thenReturn(trunkToMPMap);
    when(trunk.getName()).thenReturn("trunkWithoutMP");
    triggerAndVerifyMaintenanceBehaviourNotInvoked(spyMaintenanceMode, 2);

    // trunk, maintenancePolicy map has a mapping for the chosen trunk.
    // But, the maintenancePolicy has no dropMsgTypes/responseCode configured
    // 1. dropMsgTypes = null; responseCode = 0;
    when(trunk.getName()).thenReturn("trunkWithMP");
    triggerAndVerifyMaintenanceBehaviourNotInvoked(spyMaintenanceMode, 3);
    // 2. dropMsgTypes = []; responseCode = 0;
    mPolicy.setDropMsgTypes(new String[0]);
    triggerAndVerifyMaintenanceBehaviourNotInvoked(spyMaintenanceMode, 4);
    // 3. dropMsgTypes = ["OPTIONS"]; incoming request is INVITE
    SIPRequest request = mock(SIPRequest.class);
    when(proxySIPRequest.getRequest()).thenReturn(request);
    when(request.getMethod()).thenReturn("INVITE");
    mPolicy.setDropMsgTypes(new String[] {"OPTIONS"});
    triggerAndVerifyMaintenanceBehaviourNotInvoked(spyMaintenanceMode, 5);
  }

  public void triggerAndVerifyMaintenanceBehaviourNotInvoked(
      MaintenanceModeImpl spyMaintenanceMode, int invocations) {
    doReturn(null).when(spyMaintenanceMode).tryGlobalMaintenancePolicy(proxySIPRequest);
    assertNull(spyMaintenanceMode.maintenanceBehaviour().apply(proxySIPRequest));
    verify(spyMaintenanceMode, times(invocations)).tryGlobalMaintenancePolicy(proxySIPRequest);
  }

  @Test(
      description =
          "with maintenance mode enabled & mp configured - check if incoming msg is dropped if its configured in 'dropMsgTypes' of maintenancePolicy"
              + "& dhruva should respond to a msg with a response if its configured in 'responseCode' of maintenancePolicy."
              + "When both 'dropMsgTypes' & 'responseCodes' are configured, drop takes precedence")
  public void testTrunkMaintenancePolicyTakingEffect()
      throws InvalidArgumentException, SipException {
    MaintenanceModeImpl spyMaintenanceMode = spy(maintenanceMode);

    // in maintenance policy, when dropMsgTypes is applied
    Map<String, MaintenancePolicy> trunkToMPMap = new HashMap<>();
    MaintenancePolicy mPolicy =
        MaintenancePolicy.builder()
            .setName("mPolicy")
            .setDropMsgTypes(new String[] {"OPTIONS"})
            .setResponseCode(400)
            .build();
    trunkToMPMap.put("trunkWithMP", mPolicy);
    when(trunkConfigurationProperties.getTrunkToMaintenancePolicyMap()).thenReturn(trunkToMPMap);
    when(trunk.getName()).thenReturn("trunkWithMP");
    SIPRequest request = new SIPRequest();
    request.setMethod("OPTIONS");
    when(proxySIPRequest.getRequest()).thenReturn(request);
    assertNull(spyMaintenanceMode.maintenanceBehaviour().apply(proxySIPRequest));

    // in maintenance policy, when responseCode is applied
    mPolicy.setDropMsgTypes(null);
    ServerTransaction mockST = mock(ServerTransaction.class);
    when(proxySIPRequest.getServerTransaction()).thenReturn(mockST);

    ArgumentCaptor<SIPResponse> captor = ArgumentCaptor.forClass(SIPResponse.class);
    assertNull(spyMaintenanceMode.maintenanceBehaviour().apply(proxySIPRequest));
    verify(mockST).sendResponse(captor.capture());
    assertEquals(captor.getValue().getStatusCode(), 400);
  }

  @Test(
      description =
          "check scenarios when global maintenance policy is applied "
              + "& when default behaviour of returning 503 is applied")
  public void testGlobalMaintenancePolicyAndDefaultBehaviour()
      throws InvalidArgumentException, SipException {
    CommonConfigurationProperties commonConfigProps = mock(CommonConfigurationProperties.class);
    Map<String, MaintenancePolicy> mpMap = new HashMap<>();

    // if there are no maintenance policies available in config
    when(trunkConfigurationProperties.getCommonConfigurationProperties())
        .thenReturn(commonConfigProps);
    when(commonConfigProps.getMaintenancePolicyMap()).thenReturn(mpMap);
    ServerTransaction mockST = mock(ServerTransaction.class);
    SIPRequest request = new SIPRequest();
    when(proxySIPRequest.getRequest()).thenReturn(request);
    when(proxySIPRequest.getServerTransaction()).thenReturn(mockST);
    triggerAndVerifyGlobalMPAndDefaultBehaviour(mockST, 1, Response.SERVICE_UNAVAILABLE);

    // maintenance policies are configured, but there is no global policy
    MaintenancePolicy mPolicy = MaintenancePolicy.builder().setName("mPolicy").build();
    mpMap.put(mPolicy.getName(), mPolicy);
    triggerAndVerifyGlobalMPAndDefaultBehaviour(mockST, 2, Response.SERVICE_UNAVAILABLE);

    // global maintenance Policy is available, but responseCode is not configured
    MaintenancePolicy globalPolicy =
        MaintenancePolicy.builder().setName(MaintenanceModeImpl.GLOBAL_MAINTENANCE_POLICY).build();
    mpMap.put(globalPolicy.getName(), globalPolicy);
    triggerAndVerifyGlobalMPAndDefaultBehaviour(mockST, 3, Response.SERVICE_UNAVAILABLE);

    // global maintenance Policy with responseCode configured
    globalPolicy.setResponseCode(501);
    triggerAndVerifyGlobalMPAndDefaultBehaviour(mockST, 4, 501);
  }

  private void triggerAndVerifyGlobalMPAndDefaultBehaviour(
      ServerTransaction mockST, int invocations, int expectedResponse)
      throws InvalidArgumentException, SipException {
    ArgumentCaptor<SIPResponse> captor = ArgumentCaptor.forClass(SIPResponse.class);
    assertNull(maintenanceMode.tryGlobalMaintenancePolicy(proxySIPRequest));
    verify(mockST, times(invocations)).sendResponse(captor.capture());
    assertEquals(captor.getValue().getStatusCode(), expectedResponse);
  }
}
