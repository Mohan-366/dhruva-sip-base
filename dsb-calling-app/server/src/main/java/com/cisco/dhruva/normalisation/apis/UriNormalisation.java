package com.cisco.dhruva.normalisation.apis;

import gov.nist.core.NameValue;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

public interface UriNormalisation {

  /** remove params from a sip uri * */
  UriNormalisation removeParam(String paramName);

  UriNormalisation removeParams(List<String> paramName);

  UriNormalisation removeAllParams();

  /** add new params/update the values of existing params in sip uri * */
  UriNormalisation setParam(String paramName, String value) throws ParseException;

  UriNormalisation setParam(NameValue nameValue);

  UriNormalisation setParams(Map<String, String> params) throws ParseException;

  /** match a param value to a pattern. update it with new value if pattern matches * */
  UriNormalisation matchAndUpdate(String paramName, String pattern, String newValue)
      throws ParseException;
}
