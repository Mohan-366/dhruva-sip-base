package com.cisco.dsb.config.sip;

import static org.mockito.Mockito.when;

import com.cisco.dsb.sip.stack.dto.SGPolicy;
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

public class DhruvaSIPConfigPropertiesSGPolicyTest {

  @Mock Environment env = new MockEnvironment();
  @InjectMocks DhruvaSIPConfigProperties sipConfigProperties;
  SGPolicy validSGPolicy;

  @BeforeTest
  void init() {
    MockitoAnnotations.initMocks(this);
    validSGPolicy =
        SGPolicy.builder()
            .name("policy1")
            .lbType("call-type")
            .failoverResponseCodes(Arrays.asList(501, 502))
            .retryResponseCode(588)
            .build();
  }

  @Test
  public void getServerGroupFromJSONConfig() {
    when(env.getProperty("sgPolicies"))
        .thenReturn(
            "[{\"name\": \"policy1\", \"lbType\": \"call-type\", \"failoverResponseCodes\": [501,502], \"retryResponseCode\": 588}]");
    List<SGPolicy> expectedSGPolicies = new ArrayList<>();
    expectedSGPolicies.add(validSGPolicy);

    Assert.assertEquals(
        sipConfigProperties.getSGPolicies().toString(), expectedSGPolicies.toString());
  }

  @Test
  public void getDefaultValueFromJSONConfig() {
    when(env.getProperty("sgPolicies")).thenReturn(null);

    List<SGPolicy> expectedSGPolicies = new ArrayList<>();
    SGPolicy defaultSGPolicy = SGPolicy.builder().build();
    expectedSGPolicies.add(defaultSGPolicy);

    Assert.assertEquals(
        sipConfigProperties.getSGPolicies().toString(), expectedSGPolicies.toString());
  }
}
