package com.cisco.dsb.common.sip.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cisco.dsb.common.sip.header.RemotePartyIDHeader;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.parser.RemotePartyIDParser;
import com.google.common.collect.ImmutableMap;
import gov.nist.javax.sip.header.Accept;
import gov.nist.javax.sip.header.To;
import java.util.HashMap;
import java.util.Map;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.Header;
import javax.sip.header.HeaderAddress;
import javax.sip.header.Parameters;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
public class SipParametersUtilTest {

  static {
    RemotePartyIDParser.init();
  }

  @DataProvider
  public Object[][] headerParametersData() throws Exception {
    return new Object[][] {
      // Positive test cases
      {
        JainSipHelper.getHeaderFactory()
            .createHeader(ToHeader.NAME, "\"alice\" <sip:alice@example.com;p1=p1val>;p2=p2val"),
        SipParametersUtil.ParametersType.SIP_URI,
        ImmutableMap.builder().put("p1", "p1val").build()
      },
      {
        JainSipHelper.getHeaderFactory()
            .createHeader(ToHeader.NAME, "\"alice\" <sip:alice@example.com;p1=p1val>;p2=p2val"),
        SipParametersUtil.ParametersType.HEADER,
        ImmutableMap.builder().put("p2", "p2val").build()
      },
      {
        JainSipHelper.getHeaderFactory()
            .createHeader(ToHeader.NAME, "\"alice\" <sip:alice@example.com;p1=p1val>;p2=p2val"),
        SipParametersUtil.ParametersType.BOTH,
        ImmutableMap.builder().put("p1", "p1val").put("p2", "p2val").build()
      },
      {
        JainSipHelper.getHeaderFactory()
            .createHeader(ToHeader.NAME, "\"alice\" <sip:alice@example.com>"),
        SipParametersUtil.ParametersType.BOTH,
        ImmutableMap.builder().build()
      },
      {
        JainSipHelper.getHeaderFactory()
            .createHeader(
                ToHeader.NAME,
                "\"alice\" <sip:alice@example.com;p1=p1val;p2=p2val>;p3=p3val;p4=p4val"),
        SipParametersUtil.ParametersType.BOTH,
        ImmutableMap.builder()
            .put("p1", "p1val")
            .put("p2", "p2val")
            .put("p3", "p3val")
            .put("p4", "p4val")
            .build()
      },
      {
        JainSipHelper.getHeaderFactory()
            .createHeader(
                ToHeader.NAME,
                "\"alice\" <sip:alice@example.com;p1=p1val;p2=p2val>;p3=p3val;p4=p4val"),
        SipParametersUtil.ParametersType.BOTH,
        ImmutableMap.builder()
            .put("p1", "p1val")
            .put("p2", "p2val")
            .put("p3", "p3val")
            .put("p4", "p4val")
            .build()
      },
      {
        JainSipHelper.getHeaderFactory()
            .createHeader(
                RemotePartyIDHeader.NAME,
                "\"User21 VSPstnShared21\" <sip:+14084744574@192.168.91.101;x-cisco-number=+14084744493>;party=calling;screen=yes;privacy=off;x-cisco-tenant=7e88d491-d6ca-4786-82ed-cbe9efb02ad2;no-anchor"),
        SipParametersUtil.ParametersType.SIP_URI,
        ImmutableMap.builder().put("x-cisco-number", "+14084744493").build()
      },
      {
        JainSipHelper.getHeaderFactory()
            .createHeader(
                RemotePartyIDHeader.NAME,
                "\"User21 VSPstnShared21\" <sip:+14084744574@192.168.91.101;x-cisco-number=+14084744493>;party=calling;screen=yes;privacy=off;x-cisco-tenant=7e88d491-d6ca-4786-82ed-cbe9efb02ad2;no-anchor"),
        SipParametersUtil.ParametersType.HEADER,
        ImmutableMap.builder()
            .put("party", "calling")
            .put("screen", "yes")
            .put("privacy", "off")
            .put("x-cisco-tenant", "7e88d491-d6ca-4786-82ed-cbe9efb02ad2")
            .put("no-anchor", "")
            .build()
      },
      {
        JainSipHelper.getHeaderFactory()
            .createHeader(
                RemotePartyIDHeader.NAME,
                "\"User21 VSPstnShared21\" <sip:+14084744574@192.168.91.101;x-cisco-number=+14084744493>;party=calling;screen=yes;privacy=off;x-cisco-tenant=7e88d491-d6ca-4786-82ed-cbe9efb02ad2;no-anchor"),
        SipParametersUtil.ParametersType.BOTH,
        ImmutableMap.builder()
            .put("x-cisco-number", "+14084744493")
            .put("party", "calling")
            .put("screen", "yes")
            .put("privacy", "off")
            .put("x-cisco-tenant", "7e88d491-d6ca-4786-82ed-cbe9efb02ad2")
            .put("no-anchor", "")
            .build()
      }
    };
  }

  @Test(dataProvider = "headerParametersData")
  public void testHeaderParameters(
      Header header,
      SipParametersUtil.ParametersType parametersType,
      Map<String, String> expectedParameters) {
    Map<String, String> parameters = SipParametersUtil.getParameters(header, parametersType);
    Assert.assertEquals(parameters, expectedParameters);
  }

  public void testHeaderParametersNegativeCases() {
    Parameters params = new Accept();
    Map<String, String> param1 =
        SipParametersUtil.getParameters((Header) params, SipParametersUtil.ParametersType.SIP_URI);
    Assert.assertTrue(param1.isEmpty());

    HeaderAddress ha = new To();
    Map<String, String> param2 =
        SipParametersUtil.getParameters((Header) ha, SipParametersUtil.ParametersType.SIP_URI);
    Assert.assertTrue(param2.isEmpty());
  }

  /*@DataProvider
  public Object[][] sipUriCallTypeData() throws Exception {
      return new Object[][] {
              // Positive test cases
              { createUri("sip:locus-cd352dae-7915-43ef-81c5-d38480cafe53@sparkcmr-wpa.ciscospark.com;x-cisco-svc-type=spark-mm;call-type=VideoDialout"), SipConstants.CallType.VIDEO_DIALOUT, true },
              { createUri("sip:babecafec65b38b3-6fb7-4b44-ab86-1efb978e2b05@sparkcmr-wpa.ciscospark.com;x-cisco-ivrid=8b090e60-e460-11e8-810d-6da220d19ff2;x-cisco-svc-type=spark-mm;call-type=l2sip;x-cisco-opn=ecptn10010;x-cisco-dpn=icwbx10010;x-cisco-dtg=8080801101;transport=tls"), SipConstants.CallType.L2SIP, true },
              { createUri("sip:alice@acme.webex.com;call-type=sip"), SipConstants.CallType.SIP, true },
              // Negative test cases
              { null, SipConstants.CallType.VIDEO_DIALOUT, false },
              { createUri("sip:alice@example.com"), SipConstants.CallType.VIDEO_DIALOUT, false },
              { createUri("sip:locus-cd352dae-7915-43ef-81c5-d38480cafe53@sparkcmr-wpa.ciscospark.com"), SipConstants.CallType.VIDEO_DIALOUT, false },
              { createUri("sip:babecafec65b38b3-6fb7-4b44-ab86-1efb978e2b05@sparkcmr-wpa.ciscospark.com;x-cisco-ivrid=8b090e60-e460-11e8-810d-6da220d19ff2"), SipConstants.CallType.L2SIP, false },
              { createUri("sip:babecafeuser@meet.ciscospark.com"), SipConstants.CallType.VIDEO_DIALOUT, false },
              { createUri("sip:meet.ciscospark.com"), SipConstants.CallType.VIDEO_DIALOUT, false }
      };
  }

  @Test(dataProvider = "sipUriCallTypeData")
  public void testCallType(SipURI sipUri, SipConstants.CallType callType, boolean hasCallType) {
      Map<String, String> parameters = SipParametersUtil.getParameters(sipUri);
      Assert.assertEquals(SipParametersUtil.hasCallTypeParameter(parameters, callType), hasCallType);
  }*/

  private SipURI createUri(String uri) throws Exception {
    return (SipURI) JainSipHelper.getAddressFactory().createURI(uri);
  }

  @DataProvider
  public Object[][] sipUriFlagParameterData() throws Exception {
    return new Object[][] {
      // Positive test cases
      {createUri("sip:alice@example.com;flag"), "flag", true},
      {createUri("sip:alice@example.com;flag=value"), "flag", true},
      // Negative test cases
      {null, "flag", false},
      {createUri("sip:alice@example.com"), "flag", false},
    };
  }

  @Test(dataProvider = "sipUriFlagParameterData")
  public void testUriFlag(SipURI sipUri, String flagName, boolean hasFlag) {
    Request request = mock(Request.class);
    when(request.getRequestURI()).thenReturn(sipUri);
    Map<String, String> parameters = SipParametersUtil.getParameters(sipUri);
    Assert.assertEquals(SipParametersUtil.hasParameter(parameters, flagName), hasFlag);
  }

  @Test
  public void testUriFlagFromRequest() {
    Map<String, String> parameters = SipParametersUtil.getParameters((Request) null);
    Assert.assertTrue(parameters.isEmpty());

    Request request = mock(Request.class);
    URI uri = mock(URI.class);
    when(request.getRequestURI()).thenReturn(uri);
    when(uri.isSipURI()).thenReturn(false);
    Assert.assertTrue(SipParametersUtil.getParameters(request).isEmpty());
  }

  @Test
  public void testHasParameter() {
    Assert.assertFalse(SipParametersUtil.hasParameter(null, "test", null));
    Assert.assertFalse(SipParametersUtil.hasParameter(new HashMap<>(), "test", null));
    Map<String, String> paramMap = new HashMap<>();
    paramMap.put("test", "test-value");
    Assert.assertTrue(SipParametersUtil.hasParameter(paramMap, "test", "test-value"));
  }
}
