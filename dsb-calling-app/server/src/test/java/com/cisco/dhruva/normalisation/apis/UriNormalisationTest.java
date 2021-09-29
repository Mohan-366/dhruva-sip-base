package com.cisco.dhruva.normalisation.apis;

import gov.nist.core.NameValue;
import gov.nist.javax.sip.address.SipUri;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class UriNormalisationTest {

  SipUri uri;

  @BeforeClass
  public void init() throws ParseException {
    uri = new SipUri();
    uri.setUser("bob");
    uri.setHost("bob@example.com");
  }

  @Test
  public void testUriParamManipulations() throws ParseException {
    UriNormalisationImpl uriNorm = new UriNormalisationImpl(uri);
    Map<String, String> params = new HashMap<>();
    params.put("param-1", "value-1");
    params.put("x-cisco-test", "sample");
    NameValue pair = new NameValue("param-2", "value-2");

    // add new parameters
    uriNorm.setParams(params).setParam(pair);
    Assert.assertTrue(uri.hasParameter("param-1"));
    Assert.assertTrue(uri.hasParameter("param-2"));

    // match and update a parameter value
    String newValue1 = "new1";
    uriNorm.matchAndUpdate("param-1", "value[^.]*", newValue1);
    Assert.assertEquals(uri.getParameter("param-1"), newValue1);

    // update an existing parameter value
    String newValue2 = "new2";
    uriNorm.setParam("param-2", newValue2);
    Assert.assertEquals(uri.getParameter("param-2"), newValue2);

    // remove parameters
    uriNorm.removeParams(Arrays.asList("x-cisco-test")).removeParam("param-1").removeAllParams();
    Assert.assertTrue(uri.getParameters().isEmpty());
    Assert.assertEquals(uriNorm.getUri(), uri);
  }
}
