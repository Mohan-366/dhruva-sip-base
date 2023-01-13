package com.cisco.dhruva.application.calltype;

import com.cisco.dhruva.application.CallTypeConfig;
import com.cisco.dhruva.application.CallingAppConfigurationProperty;
import com.cisco.dhruva.application.errormapping.ErrorMappingPolicy;
import com.cisco.dhruva.application.errormapping.Mappings;
import com.cisco.dhruva.normalisation.callTypeNormalization.DialOutWXCToB2BNorm;
import com.cisco.dsb.common.maintanence.Maintenance;
import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.TrunkManager;
import com.cisco.dsb.trunk.trunks.TrunkType;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.PostConstruct;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@CustomLog
@Component
public class DialOutWxC implements CallType {

  private final CallingAppConfigurationProperty configurationProperty;
  private TrunkManager trunkManager;
  private Normalization normalization;

  @Getter private static final String callTypeNameStr = "dialOutWxC";
  @Getter @Setter private CallTypeConfig callTypeConfig;
  @Getter @Setter private ErrorMappingPolicy errorMappingPolicy;
  @Getter @Setter private Map<Integer, Mappings> errorCodeToMappingMap = new HashMap<>();

  @Autowired
  public DialOutWxC(
      TrunkManager trunkManager,
      CallingAppConfigurationProperty configurationProperty,
      DialOutWXCToB2BNorm dialOutWXCToB2BNorm) {
    this.trunkManager = trunkManager;
    this.configurationProperty = configurationProperty;
    this.normalization = dialOutWXCToB2BNorm;
  }

  @Override
  public TrunkType getIngressTrunk() {
    return TrunkType.Calling_Core;
  }

  @Override
  public TrunkType getEgressTrunk() {
    return TrunkType.B2B;
  }

  @Override
  public String getIngressKey() {
    // This should be AS, but for now NS and AS have same behavior for ingress
    return configurationProperty.getCallingEgress();
  }

  @Override
  public String getEgressKey(ProxySIPRequest proxySIPRequest) {
    return configurationProperty.getB2bEgress();
  }

  @Override
  public TrunkManager getTrunkManager() {
    return trunkManager;
  }

  @Override
  public Normalization getNormalization() {
    return normalization;
  }

  @Override
  public Map<Integer, Mappings> getErrorCodeMapping() {
    return errorCodeToMappingMap;
  }

  @Override
  public ErrorMappingPolicy getErrorMappingPolicy() {
    return this.errorMappingPolicy;
  }

  @Override
  public Maintenance getMaintenance() {
    return configurationProperty.getMaintenance();
  }

  @PostConstruct
  private void init() {
    this.callTypeConfig = this.configurationProperty.getCallTypesMap().get(callTypeNameStr);
    if (Objects.nonNull(this.callTypeConfig)) {
      setErrorMappingPolicy(callTypeConfig.getErrorMappingPolicyConfig());
      // This is more of optimization to make sure in case of error reponses, mapping takes O(1)
      ErrorMappingPolicy errorMappingPolicy = this.getErrorMappingPolicy();
      errorMappingPolicy
          .getMappings()
          .forEach(
              (mapping) -> {
                for (Integer e : mapping.getErrorCodes()) {
                  errorCodeToMappingMap.put(e, mapping);
                }
              });
    }
  }
}
