package com.cisco.dsb.config.sip;

import static org.mockito.Mockito.when;

import com.cisco.dsb.sip.stack.dto.ServerGroupElement;
import com.cisco.dsb.sip.stack.dto.StaticServer;
import com.cisco.dsb.transport.Transport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class DhruvaSIPConfigPropertiesServerGroupsTest {
  @Mock Environment env = new MockEnvironment();
  @InjectMocks DhruvaSIPConfigProperties sipConfigProperties;

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
            .lbType("call-id")
            .elements(serverGroupElementList)
            .build();
  }

  @Test
  public void getServerGroupFromJSONConfig() {

    when(env.getProperty("sipServerGroups"))
        .thenReturn(
            "[{\"serverGroupName\": \"SG1\", \"networkName\": \"net_me_tls\", \"lbType\": \"call-id\", \"elements\": [{\"ipAddress\": \"127.0.0.1\", \"port\": \"5060\", \"transport\": \"TLS\", \"qValue\": 0.9, \"weight\": 0}] }]");

    List<StaticServer> expectedServerGroup = new ArrayList<>();
    expectedServerGroup.add(validStaticServerGroup);

    Assert.assertEquals(
        sipConfigProperties.getServerGroups().toString(), expectedServerGroup.toString());
  }

  @Test
  public void getDefaultValueFromJSONConfig() {
    when(env.getProperty("sipServerGroups")).thenReturn(null);

    List<StaticServer> expectedServerGroup = new ArrayList<>();
    StaticServer defaultServerGroup = StaticServer.builder().build();
    expectedServerGroup.add(defaultServerGroup);

    Assert.assertEquals(
        sipConfigProperties.getServerGroups().toString(), expectedServerGroup.toString());
  }
}