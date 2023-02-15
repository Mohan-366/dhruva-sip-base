package com.cisco.dsb.common.config;

import com.cisco.dsb.common.dto.TrustedSipSources;
import com.cisco.wx2.dto.ErrorInfo;
import com.cisco.wx2.dto.ErrorList;
import gov.nist.javax.sip.stack.ClientAuthType;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@CustomLog
@ToString
public class CertConfigurationProperties {
  // all permissive truststore
  private boolean trustAllCerts = false;
  /**
   * Valid values for clientAuthType are Enabled, Want, Disabled or DisabledAll. Set to Enabled if
   * you want the SSL stack to require a valid certificate chain from the client before accepting a
   * connection. Set to Want if you want the SSL stack to request a client Certificate, but not fail
   * if one isn't presented. A Disabled value will not require a certificate chain for the Server
   * Connection. A DisabledAll will not require a certificate chain for both Server and Client
   * Connections.
   */
  private ClientAuthType clientAuthType = ClientAuthType.Disabled;

  // revocation | ocsp  | Action |
  // ___________|_______|________|
  // true       | true  |ocsp and fallback to crl|
  // true       | false | just crl |
  // false      | X     | no check |
  private boolean revocationCheck = true;

  private boolean revocationSoftfail = true;
  private boolean ocsp = true;
  private String trustedSipSources = "";
  private boolean requiredTrustedSipSources = false;
  private boolean acceptedIssuersEnabled = false;

  public TrustedSipSources getTrustedSipSources() {
    TrustedSipSources trustedSipSources = new TrustedSipSources(this.trustedSipSources);
    // Log and purge any values that are not valid
    ErrorList errorList = trustedSipSources.validateSources();
    for (ErrorInfo errorInfo : errorList) {
      logger.warn("trustedSipSources included invalid entry: {}", errorInfo.getDescription());
      trustedSipSources.remove(errorInfo.getDescription());
    }
    return trustedSipSources;
  }
}
