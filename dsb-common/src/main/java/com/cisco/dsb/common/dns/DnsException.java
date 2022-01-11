package com.cisco.dsb.common.dns;

public class DnsException extends RuntimeException {

  private int queryType;
  private String query;
  protected DnsErrorCode errorCode = DnsErrorCode.ERROR_UNKNOWN;

  public DnsException(String message) {
    super(message);
  }

  public DnsException(int queryType, String query, DnsErrorCode errorCode) {
    super("query:" + query + "; type:" + queryType + "; description:" + errorCode.getDescription());
    this.queryType = queryType;
    this.query = query;
    this.errorCode = errorCode;
  }

  public int getQueryType() {
    return queryType;
  }

  public DnsErrorCode getErrorCode() {
    return errorCode;
  }

  public String getQuery() {
    return query;
  }

  public String toString() {
    return "query: "
        + query
        + " type: "
        + queryType
        + " description: "
        + errorCode.getDescription();
  }
}
