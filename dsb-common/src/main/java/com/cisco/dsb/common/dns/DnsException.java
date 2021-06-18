package com.cisco.dsb.common.dns;

public class DnsException extends RuntimeException {

  public static final int QUERY_TYPE_SRV = 1;
  public static final int QUERY_TYPE_A = 33;
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
