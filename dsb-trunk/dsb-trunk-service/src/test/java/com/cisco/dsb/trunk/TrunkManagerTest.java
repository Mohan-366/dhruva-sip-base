package com.cisco.dsb.trunk;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.trunks.*;
import java.util.HashMap;
import java.util.Map;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class TrunkManagerTest {
  @Mock TrunkConfigurationProperties trunkConfigurationProperties;
  TrunkManager trunkManager;
  TrunkPlugins trunkPlugins;
  @Mock ProxySIPResponse proxySIPResponse;
  @Mock ProxySIPRequest proxySIPRequest;

  @BeforeTest
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @DataProvider
  public Object[] trunkType() {
    Map<String, PSTNTrunk> pstnTrunkMap = new HashMap<>();
    PSTNTrunk pstnTrunk1 = mock(PSTNTrunk.class);
    PSTNTrunk pstnTrunk2 = mock(PSTNTrunk.class);
    pstnTrunkMap.put("provider1", pstnTrunk1);
    pstnTrunkMap.put("provider2", pstnTrunk2);

    Map<String, B2BTrunk> b2BTrunkMap = new HashMap<>();
    AntaresTrunk antares1 = mock(AntaresTrunk.class);
    AntaresTrunk antares2 = mock(AntaresTrunk.class);
    b2BTrunkMap.put("antares1", antares1);
    b2BTrunkMap.put("antares2", antares2);

    Map<String, CallingTrunk> callingTrunkMap = new HashMap<>();
    CallingTrunk callingTrunk1 = mock(CallingTrunk.class);
    CallingTrunk callingTrunk2 = mock(CallingTrunk.class);
    callingTrunkMap.put("calling1", callingTrunk1);
    callingTrunkMap.put("calling2", callingTrunk2);

    Map<String, DefaultTrunk> defaultTrunkMap = new HashMap<>();
    DefaultTrunk defaultTrunk1 = mock(DefaultTrunk.class);
    DefaultTrunk defaultTrunk2 = mock(DefaultTrunk.class);
    defaultTrunkMap.put("default1", defaultTrunk1);
    defaultTrunkMap.put("default2", defaultTrunk2);
    when(trunkConfigurationProperties.getPstnTrunkMap()).thenReturn(pstnTrunkMap);
    when(trunkConfigurationProperties.getB2BTrunkMap()).thenReturn(b2BTrunkMap);
    when(trunkConfigurationProperties.getCallingTrunkMap()).thenReturn(callingTrunkMap);
    when(trunkConfigurationProperties.getDefaultTrunkMap()).thenReturn(defaultTrunkMap);
    trunkPlugins = new TrunkPlugins(trunkConfigurationProperties);
    trunkManager = new TrunkManager(trunkConfigurationProperties, trunkPlugins);
    return new Object[][] {
      {TrunkType.PSTN, "provider1", pstnTrunkMap},
      {TrunkType.B2B, "antares2", b2BTrunkMap},
      {TrunkType.Calling_Core, "calling1", callingTrunkMap},
      {TrunkType.DEFAULT, "default1", defaultTrunkMap}
    };
  }

  @Test(description = "test egress for all types of trunks", dataProvider = "trunkType")
  public void testEgress(TrunkType type, String key, Map<String, AbstractTrunk> trunkMap) {

    // Test handle egress for type and key present
    doAnswer(invocationOnMock -> Mono.just(proxySIPResponse))
        .when(trunkMap.get(key))
        .processEgress(proxySIPRequest);
    StepVerifier.create(trunkManager.handleEgress(type, proxySIPRequest, key))
        .assertNext(
            proxySIPResponse1 -> {
              assertEquals(proxySIPResponse1, proxySIPResponse);
            })
        .verifyComplete();
    // Test, handle egress for Trunk type not found
    StepVerifier.create(trunkManager.handleEgress(TrunkType.NOT_FOUND, proxySIPRequest, key))
        .expectErrorMatches(
            err ->
                err instanceof DhruvaRuntimeException
                    && err.getMessage().equals("Trunk Type \"NOT_FOUND\" not registered"))
        .verify();

    // Test, handle egress for PSTN, key not found
    StepVerifier.create(trunkManager.handleEgress(type, proxySIPRequest, "key_not_present"))
        .expectErrorMatches(
            err ->
                err instanceof DhruvaRuntimeException
                    && err.getMessage()
                        .equals("Key \"key_not_present\" does not match trunk of type " + type))
        .verify();
  }

  @Test(
      description = "test ingress for all types of trunks",
      dataProvider = "trunkType",
      expectedExceptions = DhruvaRuntimeException.class)
  public void testIngress1(TrunkType type, String key, Map<String, AbstractTrunk> trunkMap) {

    // Test handle ingress, trunk type and key present
    doAnswer(invocationOnMock -> proxySIPRequest)
        .when(trunkMap.get(key))
        .processIngress(proxySIPRequest);
    assertEquals(trunkManager.handleIngress(type, proxySIPRequest, key), proxySIPRequest);
    // Test, handle ingress for Trunk type not found
    trunkManager.handleIngress(TrunkType.NOT_FOUND, proxySIPRequest, key);
  }

  @Test(
      description = "exception thrown when key is not present",
      dependsOnMethods = "testIngress1",
      expectedExceptions = DhruvaRuntimeException.class)
  public void testIngress2() {
    trunkManager.handleIngress(TrunkType.PSTN, proxySIPRequest, "key_not_present");
  }
}
