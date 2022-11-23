package com.cisco.dsb.common.executor;

/** Enum hold the list of all possible services requiring executor service */
public enum ExecutorType {
  HEALTH_MONITOR_SERVICE,
  METRIC_SERVICE,
  DNS_LOCATOR_SERVICE,
  PROXY_CLIENT_TIMEOUT,
  PROXY_SEND_MESSAGE,
  PROXY_PROCESSOR,
  KEEP_ALIVE_SERVICE,
  OPTIONS_PING;

  ExecutorType() {}

  /**
   * @param serverName
   * @return String having server and the executor type e.g dsb_proxy_client_timeout
   */
  String getExecutorName(String serverName) {
    return serverName + "_" + this.toString().toLowerCase();
  }
}
