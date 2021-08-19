package com.cisco.dsb.common.sip.header;

import com.cisco.wx2.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

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
    if (o == null || getClass() != o.getClass()) return false;
    XCiscoGeoLocationInfo that = (XCiscoGeoLocationInfo) o;
    return Objects.equals(clientRegionCode, that.clientRegionCode)
        && Objects.equals(clientCountryCode, that.clientCountryCode)
        && Objects.equals(sipCloudIngressDC, that.sipCloudIngressDC)
        && Objects.equals(geoDNSDialled, that.geoDNSDialled);
  }

  @Override
  public int hashCode() {
    return Objects.hash(clientRegionCode, clientCountryCode, sipCloudIngressDC, geoDNSDialled);
  }
}
