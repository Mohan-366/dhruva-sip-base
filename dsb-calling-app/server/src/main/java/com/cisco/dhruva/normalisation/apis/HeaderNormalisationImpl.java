package com.cisco.dhruva.normalisation.apis;

import gov.nist.javax.sip.message.SIPMessage;
import java.util.List;
import javax.sip.SipException;
import javax.sip.header.Header;

public class HeaderNormalisationImpl implements HeaderNormalisation {

  SIPMessage msg;

  public HeaderNormalisationImpl(SIPMessage msg) {
    this.msg = msg;
  }

  public HeaderNormalisationImpl addHeaderToMsg(Header header) {
    msg.addHeader(header);
    return this;
  }

  public HeaderNormalisationImpl addHeaderStringToMsg(String header) {
    msg.addHeader(header);
    return this;
  }

  public HeaderNormalisationImpl addHeaderAtTop(Header header) throws SipException {
    msg.addFirst(header);
    return this;
  }

  public HeaderNormalisationImpl addHeaderAtLast(Header header) throws SipException {
    msg.addLast(header);
    return this;
  }

  public HeaderNormalisationImpl addHeaders(List<Header> headers) {
    headers.forEach(h -> msg.addHeader(h));
    return this;
  }

  public HeaderNormalisationImpl removeHeaderFromMsg(String headerName) {
    msg.removeHeader(headerName);
    return this;
  }

  public HeaderNormalisationImpl removeHeaderFromMsg(String headerName, boolean first) {
    msg.removeHeader(headerName, first);
    return this;
  }

  public HeaderNormalisationImpl removeHeaderAtTop(String headerName) {
    msg.removeFirst(headerName);
    return this;
  }

  public HeaderNormalisationImpl removeHeaderAtLast(String headerName) {
    msg.removeLast(headerName);
    return this;
  }

  public HeaderNormalisationImpl removeHeaders(List<String> headerNames) {
    headerNames.forEach(h -> msg.removeHeader(h));
    return this;
  }

  public HeaderNormalisationImpl modifyHeader(Header sipHeader) {
    msg.setHeader(sipHeader);
    return this;
  }

  public HeaderNormalisationImpl modifyHeaders(List<Header> headers) {
    headers.forEach(h -> msg.setHeader(h));
    return this;
  }

  public SIPMessage getMsg() {
    return msg;
  }
}
