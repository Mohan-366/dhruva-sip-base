package com.cisco.dsb.common.sip;

import static org.mockito.Mockito.when;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.sip.stack.dto.DynamicServer;
import java.util.ArrayList;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class DhruvaSIPConfigPropertiesDynamicServerGroupsTest {
  @Mock Environment env = new MockEnvironment();
  @InjectMocks DhruvaSIPConfigProperties sipConfigProperties;

  DynamicServer validDynamicServerGroup;

  @BeforeTest
  void init() {
    MockitoAnnotations.initMocks(this);

    validDynamicServerGroup = null;
    validDynamicServerGroup =
        DynamicServer.builder().serverGroupName("cisco.webex.com").sgPolicy("policy1").build();
  }

  @Test
  public void getServerGroupFromJSONConfig() {

    when(env.getProperty("sipDynamicServerGroups"))
        .thenReturn("[{\"serverGroupName\": \"cisco.webex.com\",\"sgPolicy\": \"policy1\"}]");

    List<DynamicServer> expectedDynamicServer = new ArrayList<>();
    expectedDynamicServer.add(validDynamicServerGroup);

    Assert.assertEquals(
        sipConfigProperties.getDynamicServerGroups().toString(), expectedDynamicServer.toString());
  }

  @Test
  public void getDefaultValueFromJSONConfig() {
    when(env.getProperty("sipDynamicServerGroups")).thenReturn(null);
    Assert.assertEquals(sipConfigProperties.getDynamicServerGroups(), null);
  }
}
