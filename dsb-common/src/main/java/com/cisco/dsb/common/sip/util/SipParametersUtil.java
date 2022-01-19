package com.cisco.dsb.common.sip.util;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.sip.address.URI;
import javax.sip.header.Header;
import javax.sip.header.HeaderAddress;
import javax.sip.header.Parameters;
import javax.sip.message.Request;

/**
 * This class provides an utility methods to perform operations on parameters from SIP objects
 * (SipURI, TelURL, Header, etc.).
 *
 * <p>1. Get parameters in the form of a Map<String, String> 2. Querying the parameters map to check
 * if a key exists (flag parameter) or if the expected value exists. 3. There are also convenience
 * methods to check for common parameter names/values.
 */
public class SipParametersUtil {

  public enum ParametersType {
    SIP_URI,
    HEADER,
    BOTH
  }

  /**
   * Returns the following parameters based on the ParametersType 1. {@link ParametersType#SIP_URI}
   * - SIP URI parameters if is a HeaderAddress type (To, From, Contact, RemotePartyID, etc.) 2.
   * {@link ParametersType#HEADER} - Header parameters if header is of Parameters type (Most
   * headers) 3. {@link ParametersType#BOTH} - Returns parameters on both SIP URI and the header
   *
   * @param header
   * @return
   */
  public static Map<String, String> getParameters(Header header, ParametersType parametersType) {
    HashMap<String, String> parameters = new HashMap<>();
    if (parametersType == ParametersType.SIP_URI || parametersType == ParametersType.BOTH) {
      if (header instanceof HeaderAddress) {
        HeaderAddress addressHeader = (HeaderAddress) header;
        if (addressHeader.getAddress() != null) {
          parameters.putAll(getParameters(addressHeader.getAddress().getURI()));
        }
      }
    }
    if (parametersType == ParametersType.HEADER || parametersType == ParametersType.BOTH) {
      if (header instanceof Parameters) {
        parameters.putAll(getParams((Parameters) header));
      }
    }
    return parameters;
  }

  /**
   * Convenience method to return the SIP URI methods, if the URI is a SipURI. Returns an empty map
   * for TelURL.
   *
   * @param uri
   * @return
   */
  public static Map<String, String> getParameters(URI uri) {
    if (uri != null && uri.isSipURI()) {
      // SipURI implements Parameters
      return getParams((Parameters) uri);
    }
    return new HashMap<>();
  }

  public static Map<String, String> getParameters(Request request) {
    return request == null ? new HashMap<>() : getParameters(request.getRequestURI());
  }

  /*public static boolean hasCallTypeParameter(Map<String, String> parameters, SipConstants.CallType callType) {
      return hasParameter(parameters, SipConstants.CALLTYPE_PARAM, callType.toString());
  }*/

  /*public static boolean hasXCiscoCrid(Map<String, String> requestUriParameters) {
      return requestUriParameters != null
          && requestUriParameters.containsKey(SipConstants.X_Cisco_Crid);
    }

    public static boolean hasXCiscoCrid(SIPRequest request) {
      return SipParametersUtil.hasXCiscoCrid(SipParametersUtil.getParameters(request));
    }

    public static String getXCiscoCrid(Map<String, String> parameters) {
      return hasXCiscoCrid(parameters) ? parameters.get(SipConstants.X_Cisco_Crid) : null;
    }

    public static String getXCiscoCrid(SIPRequest request) {
      return getXCiscoCrid(getParameters(request));
    }
  */
  /**
   * Checks if a parameter exists in the map (such as flag parameter). Ignores any value for the
   * parameter that may be present.
   *
   * @param parameters - the parameters map
   * @param name - the parameter name
   * @return
   */
  public static boolean hasParameter(Map<String, String> parameters, String name) {
    return hasParameter(parameters, name, null);
  }

  /**
   * Returns whether the given parameter map contains a parameter with the given name and value. If
   * only checking if the parameter exists, regardless of value, pass null or empty string for
   * expectedValue.
   *
   * @param parameters
   * @param name
   * @param expectedValue
   * @return
   */
  public static boolean hasParameter(
      Map<String, String> parameters, String name, String expectedValue) {
    Preconditions.checkNotNull(name, "name");
    if (parameters == null) {
      return false;
    }

    // Check for URI flags
    if (Strings.isNullOrEmpty(expectedValue)) {
      return parameters.containsKey(name);
    }

    String actualValue = parameters.get(name);
    return expectedValue.equalsIgnoreCase(actualValue);
  }

  /**
   * Returns a name->value map of the parameters on the given SIP Parameters object (SipURI, TelURL,
   * Header, etc.).
   *
   * @param parameters
   * @return
   */
  @SuppressWarnings("unchecked")
  private static Map<String, String> getParams(Parameters parameters) {
    Map<String, String> params = new HashMap<>();
    if (parameters != null) {
      Iterator parameterNames = parameters.getParameterNames();
      while (parameterNames.hasNext()) {
        String parameter = (String) parameterNames.next();
        params.put(parameter, parameters.getParameter(parameter));
      }
    }
    return params;
  }
}
