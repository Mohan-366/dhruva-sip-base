package com.cisco.dsb.common.sip.header;

import java.text.ParseException;
import javax.sip.header.Header;

public interface RequestReceivedHeader extends Header {
  String NAME = "Request-Received";

  void setPort(int port);

  int getPort();

  void setTransport(String transport) throws ParseException;

  String getTransport();

  void setReceived(String received) throws ParseException;

  String getReceived();

  String getEndPointName();

  void setEndPointName(String endpointName) throws ParseException;
}
