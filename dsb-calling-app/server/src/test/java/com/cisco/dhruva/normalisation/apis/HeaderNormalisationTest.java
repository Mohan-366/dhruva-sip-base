package com.cisco.dhruva.normalisation.apis;

import com.cisco.dsb.common.sip.jain.JainSipHelper;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.RequestLine;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.header.ViaList;
import gov.nist.javax.sip.message.SIPRequest;
import java.text.ParseException;
import java.util.Arrays;
import javax.sip.SipException;
import javax.sip.header.Header;
import org.testng.Assert;
import org.testng.annotations.Test;

public class HeaderNormalisationTest {

  @Test
  public void testHeaderManipulations() throws ParseException, SipException {

    SIPRequest request = new SIPRequest();
    SipUri uri = new SipUri();
    uri.setUser("bob");
    uri.setHost("example.com");

    RequestLine line = new RequestLine();
    line.setUri(uri);

    request.setRequestLine(line);

    Header rpid =
        JainSipHelper.getHeaderFactory().createHeader("Remote-Party-ID", "sip:bob@example.com");
    Header via =
        JainSipHelper.getHeaderFactory()
            .createHeader("Via", "SIP/2.0/TCP 127.0.0.1:5070;branch=z9hG4bK-1");
    Header topVia =
        JainSipHelper.getHeaderFactory()
            .createHeader("Via", "SIP/2.0/TCP topVia:5071;branch=z9hG4bK-2");
    Header lastVia =
        JainSipHelper.getHeaderFactory()
            .createHeader("Via", "SIP/2.0/TCP lastVia:5072;branch=z9hG4bK-3");
    Header cseq = JainSipHelper.getHeaderFactory().createHeader("CSeq", "1 INVITE");

    HeaderNormalisationImpl headerNorm = new HeaderNormalisationImpl(request);

    // add headers in different format to sip message
    headerNorm
        .addHeaderToMsg(rpid)
        .addHeaders(Arrays.asList(via))
        .addHeaderAtTop(topVia)
        .addHeaderAtLast(lastVia)
        .addHeaderStringToMsg("CSeq:1 INVITE");

    ViaList expectedVias = new ViaList();
    expectedVias.add(0, (Via) topVia);
    expectedVias.add(1, (Via) via);
    expectedVias.add(2, (Via) lastVia);
    Assert.assertEquals(request.getViaHeaders(), expectedVias);
    Assert.assertEquals(request.getViaHeaders().size(), 3);
    Assert.assertEquals(request.getHeader("Remote-Party-ID"), rpid);
    Assert.assertEquals(request.getHeader("CSeq"), cseq);

    // modify headers
    Header modified_cseq = JainSipHelper.getHeaderFactory().createHeader("CSeq", "100 INVITE");
    Header modified_rpid =
        JainSipHelper.getHeaderFactory().createHeader("Remote-Party-ID", "sip:alice@example.com");
    headerNorm.modifyHeader(modified_cseq).modifyHeaders(Arrays.asList(modified_rpid));
    Assert.assertEquals(request.getHeader("CSeq"), modified_cseq);
    Assert.assertEquals(request.getHeader("Remote-Party-ID"), modified_rpid);

    // remove headers
    headerNorm
        .removeHeaders(Arrays.asList("Remote-Party-ID"))
        .removeHeaderAtLast("CSeq")
        .removeHeaderFromMsg("Via", true);
    Assert.assertFalse(request.hasHeader("Remote-Party-ID"));
    Assert.assertFalse(request.hasHeader("CSeq"));
    Assert.assertEquals(
        request.getTopmostViaHeader(),
        via); // actual top via is removed, so should match the next in line
    Assert.assertEquals(request.getViaHeaders().size(), 2);

    headerNorm.removeHeaderAtTop("Via");
    Assert.assertEquals(
        request.getTopmostViaHeader(), lastVia); // the last via header is the remaining one
    Assert.assertEquals(request.getViaHeaders().size(), 1);

    headerNorm.removeHeaderFromMsg("Via");
    Assert.assertNull(request.getViaHeaders());

    Assert.assertEquals(headerNorm.getMsg(), request);
  }
}
