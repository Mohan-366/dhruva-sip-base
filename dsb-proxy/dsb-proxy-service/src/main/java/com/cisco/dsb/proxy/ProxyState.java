package com.cisco.dsb.proxy;

import com.cisco.dsb.common.record.DhruvaState;

public enum ProxyState implements DhruvaState {
  IN_SIP_REQUEST_RECEIVED("proxy request message received"),
  IN_PROXY_VALIDATIONS("proxy request validations done"),
  IN_PROXY_SERVER_CREATED("proxy server transaction created"),
  IN_PROXY_CONTROLLER_CREATED("controller created for incoming proxy request"),
  IN_PROXY_APP_RECEIVED("proxy request message received by app"),
  IN_PROXY_APP_PROCESSING_FAILED("proxy request processing failed in app"),
  IN_PROXY_TRUNK_PROCESS_REQUEST("proxy request trunk ingress processing"),
  IN_PROXY_NORMALIZATION_APPLIED("proxy request normalizations applied"),
  IN_PROXY_PROCESS_ENDPOINT("proxy request outbound endpoint resolved"),
  OUT_PROXY_CLIENT_CREATED("proxy request outbound client transaction created"),
  OUT_PROXY_RECORD_ROUTE_ADDED("proxy request record route added"),
  OUT_PROXY_MESSAGE_SENT("proxy request send to outbound destination"),
  OUT_PROXY_DNS_RESOLUTION("proxy request dns resolution"),
  OUT_PROXY_SEND_FAILED("proxy request send failed");

  private String state;

  ProxyState(String state) {
    this.state = state;
  }

  @Override
  public String getState() {
    return state;
  }
}
