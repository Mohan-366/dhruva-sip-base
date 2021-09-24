package com.cisco.dsb.trunk.dto;

import static org.mockito.Mockito.when;

import com.cisco.dsb.common.util.JsonSchemaValidator;
import com.cisco.dsb.trunk.config.TrunkConfigProperties;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import java.io.IOException;
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

public class TrunkConfigSGPolicyTest {

  @Mock Environment env = new MockEnvironment();
  @InjectMocks TrunkConfigProperties trunkConfigProperties;
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
        trunkConfigProperties.getSGPolicies().toString(), expectedSGPolicies.toString());
  }

  @Test(description = "validating sgpolicy schema, valid and invalid")
  public void schemaValidation() throws IOException, ProcessingException {
    String sgPolicy =
        "[{\"name\": \"policy1\", \"lbType\": \"call-type\", \"failoverResponseCodes\": [501,502], \"retryResponseCode\": 588}]";
    TrunkConfigProperties trunkConfig = new TrunkConfigProperties();
    Assert.assertTrue(
        JsonSchemaValidator.validateSchema(sgPolicy, TrunkConfigProperties.SGPOLICY_SCHEMA));
    String wrongSgPolicy =
        "[{\"serverGroupName\": \"SG1\", \"networkName\": \"DhruvaTCP\", \"elems\": [{\"ipAddress\": \"127.0.0.1\", \"port\": 5060, \"transport\": \"TLS\", \"qValue\": 0.9, \"weight\": 0}] }]";
    Assert.assertFalse(
        JsonSchemaValidator.validateSchema(wrongSgPolicy, TrunkConfigProperties.SGPOLICY_SCHEMA));
  }
}