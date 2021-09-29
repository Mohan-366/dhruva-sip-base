package com.cisco.dhruva.normalisation.apis;

import gov.nist.core.NameValue;
import gov.nist.javax.sip.address.SipUri;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UriNormalisationImpl implements UriNormalisation {

  SipUri uri;

  public UriNormalisationImpl(SipUri uri) {
    this.uri = uri;
  }

  @Override
  public UriNormalisationImpl removeParam(String paramName) {
    uri.removeParameter(paramName);
    return this;
  }

  @Override
  public UriNormalisationImpl removeParams(List<String> paramName) {
    paramName.forEach(p -> uri.removeParameter(p));
    return this;
  }

  @Override
  public UriNormalisationImpl removeAllParams() {
    uri.removeParameters();
    return this;
  }

  @Override
  public UriNormalisationImpl setParam(String paramName, String value) throws ParseException {
    uri.setParameter(paramName, value);
    return this;
  }

  @Override
  public UriNormalisationImpl setParam(NameValue nameValue) {
    uri.setUriParameter(nameValue);
    return this;
  }

  @Override
  public UriNormalisationImpl setParams(Map<String, String> params) throws ParseException {
    for (Map.Entry<String, String> entry : params.entrySet()) {
      uri.setParameter(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public UriNormalisationImpl matchAndUpdate(String paramName, String pattern, String newValue)
      throws ParseException {
    Pattern re = Pattern.compile(pattern);
    Matcher m = re.matcher(uri.getParameter(paramName));
    if (m.matches()) {
      uri.setParameter(paramName, newValue);
    }
    return this;
  }

  public SipUri getUri() {
    return uri;
  }
}
