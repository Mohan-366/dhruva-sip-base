package com.cisco.dsb.trunk;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.cisco.dsb.connectivity.monitor.service.OptionsPingController;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.trunks.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import org.junit.Assert;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class TrunkManagerTest {
  @Mock TrunkConfigurationProperties trunkConfigurationProperties;
  @Mock DhruvaExecutorService dhruvaExecutorService;

  @Mock Executor executorService;
  OptionsPingController optionsPingController;
  TrunkManager trunkManager;
  TrunkPlugins trunkPlugins;
  @Mock ProxySIPResponse proxySIPResponse;
  @Mock ProxySIPRequest proxySIPRequest;
  MetricService metricService;
  @Mock CommonConfigurationProperties commonConfigurationProperties;
  ApplicationContext context;
  SpringApplicationContext springApplicationContext = new SpringApplicationContext();

  @BeforeTest
  public void init() {
    MockitoAnnotations.openMocks(this);
    metricService = mock(MetricService.class);
    optionsPingController = mock(OptionsPingController.class);
  }

  @BeforeMethod
  public void setup() {
    reset(metricService);

    context = mock(ApplicationContext.class);
    when(context.getBean(MetricService.class)).thenReturn(metricService);

    springApplicationContext.setApplicationContext(context);
    when(context.getBean(MetricService.class)).thenReturn(metricService);
  }

  @DataProvider
  public Object[] trunkType() {
    Map<String, PSTNTrunk> pstnTrunkMap = new HashMap<>();
    PSTNTrunk pstnTrunk1 = mock(PSTNTrunk.class);
    PSTNTrunk pstnTrunk2 = mock(PSTNTrunk.class);
    pstnTrunkMap.put("provider1", pstnTrunk1);
    pstnTrunkMap.put("provider2", pstnTrunk2);

    Map<String, String> selector = new HashMap<>();
    selector.put("dtg", "provider1");

    Map<String, String> selector1 = new HashMap<>();
    selector1.put("dtg", "provider2");
    Egress egress = mock(Egress.class);
    when(pstnTrunkMap.get("provider1").getEgress()).thenReturn(egress);

    when(pstnTrunkMap.get("provider1").getEgress().getSelector()).thenReturn(selector);
    Egress egress1 = mock(Egress.class);

    when(pstnTrunkMap.get("provider2").getEgress()).thenReturn(egress1);
    when(pstnTrunkMap.get("provider2").getEgress().getSelector()).thenReturn(selector1);

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
    trunkManager =
        new TrunkManager(
            trunkConfigurationProperties,
            trunkPlugins,
            metricService,
            commonConfigurationProperties,
            null,
            optionsPingController);
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

  @Test
  public void testTrunkStatusMetric() {

    TrunkConfigurationProperties trunkConfig = new TrunkConfigurationProperties();
    Map<String, Collection<ServerGroup>> trunkSG = new HashMap<>();

    ServerGroup trunkAsg1 = ServerGroup.builder().setName("elementA1").setPingOn(true).build();
    ServerGroup trunkAsg2 = ServerGroup.builder().setName("elementA2").setPingOn(true).build();
    ServerGroup trunkAsg3 = ServerGroup.builder().setName("elementA3").build();

    trunkSG.put("trunkA", Arrays.asList(trunkAsg1, trunkAsg2, trunkAsg3));

    ServerGroup trunkBsg1 = ServerGroup.builder().setName("elementB1").build();
    ServerGroup trunkBsg2 = ServerGroup.builder().setName("elementB2").build();
    ServerGroup trunkBsg3 = ServerGroup.builder().setName("elementB3").build();

    trunkSG.put("trunkB", Arrays.asList(trunkBsg1, trunkBsg2, trunkBsg3));

    ServerGroup trunkCsg1 = ServerGroup.builder().setName("elementC1").setPingOn(true).build();
    ServerGroup trunkCsg2 = ServerGroup.builder().setName("elementC2").setPingOn(true).build();

    trunkSG.put("trunkC", Arrays.asList(trunkCsg1, trunkCsg2));

    trunkConfigurationProperties = Mockito.spy(trunkConfig);

    when(optionsPingController.getStatus(any(ServerGroup.class)))
        .thenAnswer(
            invocation -> {
              Object argument = invocation.getArguments()[0];
              if (argument.equals(trunkAsg1)) {
                return true;
              } else if (argument.equals(trunkAsg2)) {
                return false;
              } else if (argument.equals(trunkCsg1)) {
                return false;
              } else if (argument.equals(trunkCsg2)) {
                return false;
              }

              return argument;
            });

    when(trunkConfigurationProperties.getTrunkServerGroupHashMap()).thenReturn(trunkSG);

    trunkPlugins = new TrunkPlugins(trunkConfigurationProperties);

    MetricService ms = new MetricService(null, dhruvaExecutorService, executorService);
    metricService = Mockito.spy(ms);

    doNothing().when(metricService).emitTrunkCPSMetricPerInterval(anyInt(), any());
    doNothing().when(metricService).emitTrunkLBDistribution(anyInt(), any());
    doNothing().when(metricService).emitTrunkStatusSupplier(anyString());
    doNothing().when(metricService).emitServerGroupStatusSupplier(anyString());

    TrunkManager trunkManager =
        new TrunkManager(
            trunkConfigurationProperties,
            trunkPlugins,
            metricService,
            commonConfigurationProperties,
            null,
            optionsPingController);
    trunkManager.probeTrunkSGStatus();

    String arr1[] = {"true", "elementA3,elementA2,elementA1"};
    String arr2[] = {"disabled", "elementB3,elementB2,elementB1"};
    String arr3[] = {"false", "elementC2,elementC1"};

    Map<String, String[]> trunkStatusMap = new HashMap<>();
    trunkStatusMap.put("trunkA", arr1);
    trunkStatusMap.put("trunkB", arr2);
    trunkStatusMap.put("trunkC", arr3);
    Assert.assertTrue(compareHashMap(trunkStatusMap, metricService.getTrunkStatusMap()));
  }

  private boolean compareHashMap(Map<String, String[]> expected, Map<String, String[]> actual) {
    if (expected.size() != actual.size()) {
      return false;
    }
    return expected.entrySet().stream()
        .allMatch(e -> Arrays.equals(e.getValue(), actual.get(e.getKey())));
  }
}
