package com.cisco.dhruva.normalisation.apis;

import java.util.List;
import javax.sip.SipException;
import javax.sip.header.Header;

public interface HeaderNormalisation {

  /**
   * --- HEADER ADDITION APIS ---
   *
   * <p>add header(s) to the top/last(for those that headers that support a list) of a sip msg
   */
  HeaderNormalisation addHeaderToMsg(Header header);

  HeaderNormalisation addHeaderStringToMsg(String header);

  HeaderNormalisation addHeaderAtTop(Header header) throws SipException;

  HeaderNormalisation addHeaderAtLast(Header header) throws SipException;

  HeaderNormalisation addHeaders(List<Header> header);

  /**
   * --- HEADER REMOVAL APIS ---
   *
   * <p>remove header(s) from the top/last(for those that headers that support a list) of a sip msg
   */
  HeaderNormalisation removeHeaderFromMsg(String headerName);

  HeaderNormalisation removeHeaderFromMsg(String headerName, boolean first);

  HeaderNormalisation removeHeaderAtTop(String headerName);

  HeaderNormalisation removeHeaderAtLast(String headerName);

  HeaderNormalisation removeHeaders(List<String> headerNames);

  /**
   * --- HEADER UPDATION APIS ---
   *
   * <p>update the header(s) in a sip msg
   */
  HeaderNormalisation modifyHeader(Header sipHeader);

  HeaderNormalisation modifyHeaders(List<Header> header);
}
