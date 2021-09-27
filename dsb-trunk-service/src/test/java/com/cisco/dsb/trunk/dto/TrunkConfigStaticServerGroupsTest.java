package com.cisco.dsb.trunk.dto;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertThrows;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.ServerGroupElement;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.JsonSchemaValidator;
import com.cisco.dsb.trunk.config.TrunkConfigProperties;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.mockito.*;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class TrunkConfigStaticServerGroupsTest {
  @Mock Environment env = new MockEnvironment();

  @Spy List<SIPListenPoint> listenPoints;
  @InjectMocks TrunkConfigProperties trunkConfigProperties;

  StaticServer validStaticServerGroup;

  @BeforeTest
  void init() {
    MockitoAnnotations.initMocks(this);
    ServerGroupElement element =
        ServerGroupElement.builder()
            .ipAddress("127.0.0.1")
            .port(5060)
            .qValue(0.9f)
            .weight(0)
            .transport(Transport.TLS)
            .build();

    List<ServerGroupElement> serverGroupElementList = Arrays.asList(element);

    validStaticServerGroup = null;
    validStaticServerGroup =
        StaticServer.builder()
            .serverGroupName("SG1")
            .networkName("net_me_tls")
            .elements(serverGroupElementList)
            .build();
  }

  @Test
  public void getServerGroupFromJSONConfig() {

    when(env.getProperty("sipServerGroups"))
        .thenReturn(
            "[{\"serverGroupName\": \"SG1\", \"networkName\": \"net_me_tls\", \"elements\": [{\"ipAddress\": \"127.0.0.1\", \"port\": 5060, \"transport\": \"TLS\", \"qValue\": 0.9, \"weight\": 0}] }]");

    List<SIPListenPoint> ss = new ArrayList<>();
    SIPListenPoint listenPoint =
        new SIPListenPoint.SIPListenPointBuilder().setName("net_me_tls").build();
    ss.add(listenPoint);
    Stream<SIPListenPoint> streamOfArray = Stream.of(listenPoint);
    when(listenPoints.stream()).thenReturn(streamOfArray);
    when(listenPoints.isEmpty()).thenReturn(false);
    List<StaticServer> expectedServerGroup = new ArrayList<>();
    expectedServerGroup.add(validStaticServerGroup);
    Assert.assertEquals(
        trunkConfigProperties.getServerGroups().toString(), expectedServerGroup.toString());
  }

  @Test(description = "validating static server schema,  valid and invalid")
  public void schemaValidation() throws IOException, ProcessingException {
    String staticServer =
        "[{\"serverGroupName\": \"SG1\", \"networkName\": \"DhruvaTCP\", \"elements\": [{\"ipAddress\": \"127.0.0.1\", \"port\": 5060, \"transport\": \"TLS\", \"qValue\": 0.9, \"weight\": 0}] }]";
    TrunkConfigProperties trunkConfig = new TrunkConfigProperties();
    Assert.assertTrue(
        JsonSchemaValidator.validateSchema(staticServer, TrunkConfigProperties.STATIC_SCHEMA));

    String wrongStaticServer =
        "[{\"serverGroupName\": \"SG1\", \"networkName\": \"DhruvaTCP\", \"elems\": [{\"ipAddress\": \"127.0.0.1\", \"port\": 5060, \"transport\": \"TLS\", \"qValue\": 0.9, \"weight\": 0}] }]";
    Assert.assertFalse(
        JsonSchemaValidator.validateSchema(wrongStaticServer, TrunkConfigProperties.STATIC_SCHEMA));
  }

  @Test(
      description =
          "Network exists: validating config, if network not present sg shouldn't be created")
  public void validNetworkTest() throws DhruvaException {
    List<SIPListenPoint> ss = new ArrayList<>();
    SIPListenPoint listenPoint =
        new SIPListenPoint.SIPListenPointBuilder().setName("DhruvaUDP").build();
    ss.add(listenPoint);
    Stream<SIPListenPoint> streamOfArray = Stream.of(listenPoint);
    when(listenPoints.stream()).thenReturn(streamOfArray);
    List<StaticServer> expectedServerGroup = new ArrayList<>();
    validStaticServerGroup = StaticServer.builder().networkName("DhruvaUDP").build();
    expectedServerGroup.add(validStaticServerGroup);

    trunkConfigProperties.validateNetwork(expectedServerGroup);
    Assert.assertEquals(expectedServerGroup.size(), 1);
  }

  @Test(
      description =
          "Network doesn't exist: validating config, if network not present sg shouldn't be created: FAIL")
  public void invalidNetworkTest() throws DhruvaException {

    List<SIPListenPoint> ss = new ArrayList<>();
    SIPListenPoint listenPoint =
        new SIPListenPoint.SIPListenPointBuilder().setName("DhruvaUDP").build();
    ss.add(listenPoint);

    Stream<SIPListenPoint> streamOfArray = Stream.of(listenPoint);
    when(listenPoints.stream()).thenReturn(streamOfArray);
    when(listenPoints.isEmpty()).thenReturn(false);
    List<StaticServer> expectedServerGroup = new ArrayList<>();
    validStaticServerGroup = StaticServer.builder().networkName("test").build();

    expectedServerGroup.add(validStaticServerGroup);

    assertThrows(
        DhruvaException.class, () -> trunkConfigProperties.validateNetwork(expectedServerGroup));

  }
}
