package com.cisco.dhruva.application.calltype;

import com.cisco.dhruva.application.CallTypeConfig;
import com.cisco.dhruva.application.CallingAppConfigurationProperty;
import com.cisco.dhruva.application.errormapping.ErrorMappingPolicy;
import com.cisco.dhruva.application.errormapping.Mappings;
import com.cisco.dhruva.normalisation.callTypeNormalization.DialOutB2BToPSTNNorm;
import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.TrunkManager;
import com.cisco.dsb.trunk.trunks.TrunkType;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.address.SipUri;
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
public class DialOutB2B implements CallType {
  private final CallingAppConfigurationProperty configurationProperty;
  private TrunkManager trunkManager;
  private static final String DTG = "dtg";
  private Normalization normalization;
  @Getter private static final String callTypeNameStr = "dialOutB2B";
  @Getter @Setter private CallTypeConfig callTypeConfig;
  @Getter @Setter private ErrorMappingPolicy errorMappingPolicy;
  @Getter @Setter private Map<Integer, Mappings> errorCodeToMappingMap = new HashMap<>();

  @Autowired
  public DialOutB2B(
      TrunkManager trunkManager,
      CallingAppConfigurationProperty configurationProperty,
      DialOutB2BToPSTNNorm dialOutB2BToPSTNNorm) {
    this.trunkManager = trunkManager;
    this.configurationProperty = configurationProperty;
    this.normalization = dialOutB2BToPSTNNorm;
  }

  private String getDtg(ProxySIPRequest proxySIPRequest) {
    if (proxySIPRequest.isMidCall()) {
      return SipParamConstants.DEFAULT_DTG_VALUE_FOR_MIDCALL;
    }
    return ((SipUri) proxySIPRequest.getRequest().getRequestURI()).getParameter(DTG);
  }

  @Override
  public TrunkType getIngressTrunk() {
    return TrunkType.B2B;
  }

  @Override
  public TrunkType getEgressTrunk() {
    return TrunkType.PSTN;
  }

  @Override
  public String getIngressKey() {
    return configurationProperty.getB2bEgress();
  }

  @Override
  public String getEgressKey(ProxySIPRequest proxySIPRequest) {
    return getDtg(proxySIPRequest);
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
