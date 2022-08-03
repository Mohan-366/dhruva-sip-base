package com.cisco.dhruva.application.calltype;

import com.cisco.dhruva.application.CallingAppConfigurationProperty;
import com.cisco.dhruva.normalisation.callTypeNormalization.DialOutB2BToPSTNNorm;
import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.TrunkManager;
import com.cisco.dsb.trunk.trunks.TrunkType;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.address.SipUri;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@CustomLog
@Component
public class DialOutB2B implements CallType {
  private final CallingAppConfigurationProperty configurationProperty;
  private TrunkManager trunkManager;
  private static final String DTG = "dtg";
  private Normalization normalization;

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
}
