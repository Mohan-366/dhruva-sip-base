package com.cisco.dsb.common.sip.tls;

import com.cisco.wx2.certs.common.util.CertHelper;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.security.cert.X509Certificate;
import java.util.List;
import lombok.CustomLog;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@CustomLog
public class CertificateInfo {

  private String subjectName;
  private List<String> sans;

  @JsonCreator
  public CertificateInfo(
      @JsonProperty("subjectName") String subjectName, @JsonProperty("SANs") List<String> sans) {
    this.subjectName = subjectName;
    this.sans = sans;
  }

  public static CertificateInfo createFromX509Certificate(X509Certificate certificate) {
    String subjectName = null;
    try {
      subjectName = certificate.getSubjectX500Principal().getName();
    } catch (Exception e) {
      logger.debug("Error getting subject name", e);
    }

    List<String> names = null;
    try {
      names = CertHelper.extractNamesFromCert(certificate);
      logDomainExtractionDiffs(certificate, names);
    } catch (Exception e) {
      logger.debug("Error extracting names", e);
    }

    return new CertificateInfo(subjectName, names);
  }

  // Log whether updating cert domain extraction changes results.
  public static void logDomainExtractionDiffs(X509Certificate certificate, List<String> names) {
    List<String> nameDiffs = CertHelper.getCertsHardeningDiff(certificate, names);
    if (!nameDiffs.isEmpty()) {
      logger.info(
          "RFC hardened cert domains and current extraction domains don't match. Diffs: {}",
          nameDiffs);
    }
  }

  public String getSubjectName() {
    return subjectName;
  }

  public List<String> getSans() {
    return sans;
  }

  @Override
  public String toString() {
    return String.format("Subject: %s, Common Names and SANs: %s", subjectName, sans);
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || other.getClass() != this.getClass()) {
      return false;
    }

    CertificateInfo otherCertificateInfo = (CertificateInfo) other;
    return new EqualsBuilder()
        .append(this.subjectName, otherCertificateInfo.subjectName)
        .append(this.sans, otherCertificateInfo.sans)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(this.subjectName).append(this.sans).toHashCode();
  }
}
