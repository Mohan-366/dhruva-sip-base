package com.cisco.dhruva.sip.controller.util;

import com.cisco.dhruva.sip.controller.AppParamsInterface;
import com.cisco.dhruva.sip.controller.ControllerConfig;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.sip.util.ReConstants;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.SpringApplicationContext;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import gov.nist.javax.sip.header.Route;
import gov.nist.javax.sip.header.ims.PAssertedIdentityHeader;
import gov.nist.javax.sip.message.SIPRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.HeaderAddress;

public class ParseProxyParamUtil {

  // Order in which the transport is selected.
  private static final Transport[] Transports = {Transport.TLS, Transport.TCP, Transport.UDP};
  protected static final Logger logger = DhruvaLoggerFactory.getLogger(ParseProxyParamUtil.class);

  private static ControllerConfig controllerConfig;

  static {
    controllerConfig = SpringApplicationContext.getAppContext().getBean(ControllerConfig.class);
  }

  private ParseProxyParamUtil() {}

  public static AppParamsInterface getAppParamsInterface(SIPRequest request) {

    return () -> {
      try {
        return getParsedProxyParams(request, ReConstants.MY_URI, false, ReConstants.DELIMITER_STR);
      } catch (DhruvaException e) {
        logger.error("Unable to get parsed proxy params for MY_URI.", e);
      }
      return null;
    };
  }

  public static Map<String, String> getParsedProxyParams(
      SIPRequest request, int type, boolean decompress, String delimiter) throws DhruvaException {
    logger.info("Dhruva getParsedProxyParams" + type);
    String userPortion = null;
    HeaderAddress header = null;
    switch (type) {
      case ReConstants.MY_URI:
        // TODO DSB
        // userPortion = getUserPortionFromUri(request.getRequestURI());
        userPortion = null;
        break;
      case ReConstants.R_URI:
        userPortion = getUserPortionFromUri(request.getRequestURI());
        break;
      case ReConstants.P_A_ID:
        header = (HeaderAddress) request.getHeader(PAssertedIdentityHeader.NAME);
        if (header != null) {
          userPortion = getUserPortionFromUri(header.getAddress().getURI());
        }
        break;
      case ReConstants.ROUTE:
        header = (HeaderAddress) request.getHeader(Route.NAME);
        if (header != null) {
          userPortion = getUserPortionFromUri(header.getAddress().getURI());
        }
        break;
      default:
        break;
    }
    logger.info("Dhruva getParsedProxyParams" + userPortion);
    if (userPortion == null) {
      return null;
    }

    HashMap<String, String> parsedProxyParams = new HashMap<>();
    String nameValue;
    StringTokenizer st = new StringTokenizer(userPortion.toString(), delimiter);
    while (st.hasMoreTokens()) {
      nameValue = st.nextToken();
      parseNameValue(nameValue, parsedProxyParams);
    }
    return parsedProxyParams;
  }

  private static void parseNameValue(String nameValue, HashMap<String, String> params) {
    int i = nameValue.indexOf(ReConstants.EQUAL_CHAR);
    if (i >= 0) {
      String name = nameValue.substring(0, i);
      String value = nameValue.substring(i + 1);
      params.put(name, value);
    } else {
      params.put(nameValue, nameValue);
    }
  }

  private static String getUserPortionFromUri(URI uri) throws DhruvaException {
    if (uri == null) {
      return null;
    }
    String userPortion = null;
    if (uri.isSipURI()) {
      userPortion = ((SipURI) uri).getUser();
    } else {
      userPortion = uri.getScheme();
    }
    return userPortion;
  }

  public static Transport getNetworkTransport(DhruvaNetwork network) {
    Transport networkTransport = Transport.NONE;
    for (Transport transport : Transports) {
      if (network != null && controllerConfig.getInterface(transport, network) != null) {
        networkTransport = transport;
        break;
      }
    }
    return networkTransport;
  }
}
