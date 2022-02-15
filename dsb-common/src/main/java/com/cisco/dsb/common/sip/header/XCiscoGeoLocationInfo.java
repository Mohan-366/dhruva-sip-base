package com.cisco.dsb.common.sip.header;

import com.cisco.wx2.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class XCiscoGeoLocationInfo {
  // Note that Orpheus and Cloud Proxy name some of these fields differently, hence the JsonProperty
  // annotations,
  // which will cause the generated JSON to use different names than the parsed header fields
  @JsonProperty("clientSourceRegion")
  private String clientRegionCode;

  private String clientCountryCode;

  @JsonProperty("cloudIngressRegion")
  private String sipCloudIngressDC;

  @JsonProperty("useCloudIngressRegion")
  private Boolean geoDNSDialled;

  public XCiscoGeoLocationInfo() {}

  public XCiscoGeoLocationInfo(
      String clientRegionCode,
      String clientCountryCode,
      String sipCloudIngressDC,
      Boolean geoDNSDialled) {
    this.clientRegionCode = clientRegionCode;
    this.clientCountryCode = clientCountryCode;
    this.sipCloudIngressDC = sipCloudIngressDC;
    this.geoDNSDialled = geoDNSDialled;
  }

  public String getClientRegionCode() {
    return this.clientRegionCode;
  }

  public void setClientRegionCode(String clientRegionCode) {
    this.clientRegionCode = clientRegionCode;
  }

  public String getClientCountryCode() {
    return this.clientCountryCode;
  }

  public void setClientCountryCode(String clientCountryCode) {
    this.clientCountryCode = clientCountryCode;
  }

  public void setSipCloudIngressDC(String sipCloudIngressDC) {
    this.sipCloudIngressDC = sipCloudIngressDC;
  }

  public String getSipCloudIngressDC() {
    return this.sipCloudIngressDC;
  }

  public Boolean getGeoDNSDialled() {
    return this.geoDNSDialled;
  }

  public void setGeoDNSDialled(boolean geoDNSDialled) {
    this.geoDNSDialled = geoDNSDialled;
  }

  public JsonNode toJson() {
    return JsonUtil.getObjectMapper().convertValue(this, JsonNode.class);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof XCiscoGeoLocationInfo) {
      XCiscoGeoLocationInfo that = (XCiscoGeoLocationInfo) o;
      return new EqualsBuilder()
          .append(clientRegionCode, that.clientRegionCode)
          .append(clientCountryCode, that.clientCountryCode)
          .append(sipCloudIngressDC, that.sipCloudIngressDC)
          .append(geoDNSDialled, that.geoDNSDialled)
          .isEquals();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(clientRegionCode)
        .append(clientCountryCode)
        .append(sipCloudIngressDC)
        .append(geoDNSDialled)
        .toHashCode();
  }
}
