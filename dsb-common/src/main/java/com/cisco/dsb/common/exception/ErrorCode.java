package com.cisco.dsb.common.exception;

import lombok.Getter;

public enum ErrorCode {
  DESTINATION_UNREACHABLE(502, Action.RETRY),
  TRANSACTION_ERROR(500, Action.SEND_ERR_RESPONSE),
  UNKNOWN_DESTINATION_TYPE(502, Action.SEND_ERR_RESPONSE),
  FETCH_ENDPOINT_ERROR(502, Action.SEND_ERR_RESPONSE),
  PROCESS_OUTBOUND_DESTINATION_ERR(500, Action.SEND_ERR_RESPONSE),
  NO_OUTGOING_NETWORK(500, Action.SEND_ERR_RESPONSE),
  NO_INCOMING_NETWORK(500, Action.SEND_ERR_RESPONSE),
  REQUEST_PARSE_ERROR(500, Action.SEND_ERR_RESPONSE),
  PROXY_REQ_PROC_ERR(500, Action.SEND_ERR_RESPONSE),
  CREATE_ERR_RESPONSE(500),
  REQUEST_NO_PROVIDER(500),
  RESPONSE_PARSE_ERROR(0),
  RESPONSE_NO_VIA(0),
  INVALID_PARAM(500, Action.SEND_ERR_RESPONSE),
  INVALID_STATE(500, Action.SEND_ERR_RESPONSE),
  UNKNOWN_ERROR_REQ(500, Action.SEND_ERR_RESPONSE),
  SEND_RESPONSE_ERR(0),
  UNKNOWN_ERROR_RES(0),
  APP_REQ_PROC(500, Action.SEND_ERR_RESPONSE),
  NOT_FOUND(404, Action.SEND_ERR_RESPONSE),
  TRUNK_RETRY_NEXT(0),
  TRUNK_NO_RETRY(500, Action.SEND_ERR_RESPONSE),
  INIT(0);

  @Getter private final int responseCode;
  @Getter private final Action action;

  ErrorCode(int value) {
    this(value, Action.DROP);
  }

  ErrorCode(int value, Action action) {
    this.responseCode = value;
    this.action = action;
  }

  public enum Action {
    DROP(0),
    RETRY(1),
    SEND_ERR_RESPONSE(2);

    private final int value;

    Action(int value) {
      this.value = value;
    }
  }
}
