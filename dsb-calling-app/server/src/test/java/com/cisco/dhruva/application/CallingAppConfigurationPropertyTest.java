package com.cisco.dhruva.application;

import static org.testng.Assert.assertEquals;

import com.cisco.dhruva.application.calltype.DialInPSTN;
import com.cisco.dhruva.application.errormapping.ErrorMappingPolicy;
import com.cisco.dhruva.application.errormapping.Mappings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class CallingAppConfigurationPropertyTest {
  private String networkPSTN = "net_sp";
  private String networkB2B = "net_antares";
  private String networkCallingCore = "net_cc";
  private String b2bEgress = "antares";
  private String callingEgress = "NS";
  private String pstnIngress = "UsPoolA";

  Map<String, CallTypeConfigProperties> callTypesPropertiesMap = new HashMap<>();
  Map<String, ErrorMappingPolicy> errorMappingPolicyMap = new HashMap<>();

  Map<String, CallTypeConfig> callTypeConfigMap = new HashMap<>();
  ErrorMappingPolicy errorMappingPolicy = new ErrorMappingPolicy();
  List<Integer> errorCodes1 = new ArrayList<>();
  List<Integer> errorCodes2 = new ArrayList<>();

  @BeforeTest
  public void init() {
    errorMappingPolicy.setName("testCallType");
    List<Mappings> mappingsList = new ArrayList<>();
    errorCodes1.add(503);
    errorCodes1.add(404);
    errorCodes2.add(408);
    errorCodes2.add(500);
    mappingsList.add(
        Mappings.builder()
            .setMappedResponseCode(502)
            .setMappedResponsePhrase("damn")
            .setErrorCodes(errorCodes1)
            .build());
    mappingsList.add(
        Mappings.builder()
            .setMappedResponseCode(503)
            .setMappedResponsePhrase("damn")
            .setErrorCodes(errorCodes2)
            .build());
    errorMappingPolicy.setMappings(mappingsList);

    errorMappingPolicyMap.put(errorMappingPolicy.getName(), errorMappingPolicy);

    CallTypeConfigProperties callTypeConfigProperties = new CallTypeConfigProperties();
    callTypeConfigProperties.setName("dialInPSTN");
    callTypeConfigProperties.setErrorMappingPolicy(errorMappingPolicy.getName());

    callTypesPropertiesMap.put("dialInPSTN", callTypeConfigProperties);
    callTypeConfigMap.put(
        DialInPSTN.getCallTypeNameStr(), new CallTypeConfig("abc", errorMappingPolicy));
  }

  @Test
  public void testGetterSetter() {

    CallingAppConfigurationProperty configurationProperty = new CallingAppConfigurationProperty();
    configurationProperty.setCallingEgress(callingEgress);
    configurationProperty.setNetworkCallingCore(networkCallingCore);
    configurationProperty.setB2bEgress(b2bEgress);
    configurationProperty.setNetworkPSTN(networkPSTN);
    configurationProperty.setNetworkB2B(networkB2B);
    configurationProperty.setPstnIngress(pstnIngress);
    configurationProperty.setErrorMappingPolicy(errorMappingPolicyMap);
    configurationProperty.setCallTypes(callTypesPropertiesMap);

    assertEquals(callingEgress, configurationProperty.getCallingEgress());
    assertEquals(networkCallingCore, configurationProperty.getNetworkCallingCore());
    assertEquals(b2bEgress, configurationProperty.getB2bEgress());
    assertEquals(networkPSTN, configurationProperty.getNetworkPSTN());
    assertEquals(networkB2B, configurationProperty.getNetworkB2B());
    assertEquals(pstnIngress, configurationProperty.getPstnIngress());

    // Validate CallType configs
    Assert.notNull(
        configurationProperty.getCallTypesMap().get("dialInPSTN"),
        "expected a value , should be set");
    assertEquals(
        configurationProperty.getCallTypesMap().get("dialInPSTN").getErrorMappingPolicyConfig(),
        errorMappingPolicyMap.get(errorMappingPolicy.getName()));
  }
}
