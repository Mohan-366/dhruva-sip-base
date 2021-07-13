package com.cisco.dsb.sip.parser;

import com.cisco.dsb.sip.header.XCiscoGeoLocationInfo;
import com.cisco.dsb.sip.util.SipConstants;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import gov.nist.javax.sip.header.SIPHeader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// X-Cisco-Geo-Location-Info: <clientRegionCode="clientRegionCodeValue">,
// <clientCountryCode="clientCountryCode">, <sipCloudIngressDC="sipCloudIngressDCValue">,
// <geoDNSDialled="<true|false>">
@SuppressFBWarnings(value = "REDOS", justification = "Erroneous warning")
public class XCiscoGeoLocationInfoParser {

  private static Logger logger = DhruvaLoggerFactory.getLogger(ExecutorService.class);

  public static XCiscoGeoLocationInfo parse(SIPHeader sipHeader) {
    if (sipHeader == null) {
      return null;
    }

    String headerVal = sipHeader.getHeaderValue();

    List<String> params = new ArrayList<>();
    // This is used to check that the entire header value is formatted properly
    Matcher matchWholeValue =
        Pattern.compile("^\\s*(<\\w+=\"[\\w\\-]+\">)(,\\s*<\\w+=\"[\\w\\-]+\">)*\\s*$")
            .matcher(headerVal);
    Matcher matchParams = Pattern.compile("(<\\w+=\"[\\w\\-]+\">)").matcher(headerVal);

    if (matchWholeValue.find()) {
      while (matchParams.find()) {
        params.add(matchParams.group(0));
      }

      XCiscoGeoLocationInfo geoLocationInfo = new XCiscoGeoLocationInfo();

      for (String param : params) {
        // Strip the leading "<" and the trailing ">"
        param = param.substring(1, param.length() - 1);
        String[] paramParts = param.split("=", 2);

        String paramName = paramParts[0];
        String paramValue = paramParts[1];
        // The param value is wrapped in quotes
        paramValue = paramValue.substring(1, paramValue.length() - 1);

        if (paramName.equalsIgnoreCase(SipConstants.Client_Region_Code)) {
          geoLocationInfo.setClientRegionCode(paramValue);
        } else if (paramName.equalsIgnoreCase(SipConstants.Client_Country_Code)) {
          geoLocationInfo.setClientCountryCode(paramValue);
        } else if (paramName.equalsIgnoreCase(SipConstants.Sip_Cloud_Ingress_DC)) {
          geoLocationInfo.setSipCloudIngressDC(paramValue);
        } else if (paramName.equalsIgnoreCase(SipConstants.Geo_Dns_Dialled)) {
          geoLocationInfo.setGeoDNSDialled(Boolean.parseBoolean(paramValue));
        } else {
          logger.info("Unexpected parameter in X-Cisco-Geo-Location-Info header: {}", param);
        }
      }
      return geoLocationInfo;
    } else {
      logger.error("Malformed X-Cisco-Geo-Location-Info header: {}", headerVal);
      return null;
    }
  }
}
