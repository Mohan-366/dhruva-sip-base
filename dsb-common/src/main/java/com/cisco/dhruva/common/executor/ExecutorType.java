package com.cisco.dhruva.common.executor;

/** Enum hold the list of all possible services requiring executor service */
public enum ExecutorType {
  METRIC_SERVICE(6),
  DNS_LOCATOR_SERVICE(8);

  ExecutorType(int val) {}

  /**
   * @param serverName
   * @return String having executor type and the server e.g SIP_TRANSACTION_PROCESSOR-dhruva
   */
  String getExecutorName(String serverName) {
    return serverName.replace("%", "%%") + this.toString();
  }
}
