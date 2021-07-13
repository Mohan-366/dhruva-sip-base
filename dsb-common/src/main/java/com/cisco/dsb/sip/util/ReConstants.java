package com.cisco.dsb.sip.util;

public class ReConstants {
  public static final String DELIMITER_STR =
      System.getProperty("com.cisco.re.util.PARAMS_DELIMITER", "$");
  public static final char DELIMITER_CHAR = DELIMITER_STR.charAt(0);

  public static final char EQUAL_CHAR = '=';
  public static final String EQUAL_STR = "=";

  public static final String N = "n";
  public static final String NETWORK_TOKEN = N + EQUAL_CHAR;
  public static final String BS_NETWORK_TOKEN = new String(NETWORK_TOKEN);

  public static final String RR = "rr";
  public static final String RR_TOKEN = RR + DELIMITER_CHAR;
  public static final String BS_RR_TOKEN = new String(RR_TOKEN);
  public static final String RR_TOKEN1 = DELIMITER_CHAR + RR;
  public static final String RR_TOKEN2 = DELIMITER_CHAR + RR + DELIMITER_CHAR;

  public static final String PR_TOKEN = "pr" + DELIMITER_CHAR;
  public static final String BS_PR_TOKEN = new String(PR_TOKEN);
  public static final String PR_TOKEN1 = DELIMITER_CHAR + "pr";
  public static final String PR_TOKEN2 = DELIMITER_CHAR + "pr" + DELIMITER_CHAR;

  public static final String SR_TOKEN = "sr" + DELIMITER_CHAR;
  public static final String SR_TOKEN1 = DELIMITER_CHAR + "sr";
  public static final String SR_TOKEN2 = DELIMITER_CHAR + "sr" + DELIMITER_CHAR;

  // different types for setting proxy params.
  public static final short RECORD_ROUTE = 0;
  public static final short PATH = 1;
  public static final short MY_URI = 2;
  public static final short R_URI = 3;
  public static final short ROUTE = 4;
  public static final short P_A_ID = 5;

  public static final String RECORD_ROUTE_STR = "record-route";
  public static final String PATH_STR = "path";
  public static final String MY_URI_STR = "my-uri";
  public static final String R_URI_STR = "request-uri";
  public static final String ROUTE_STR = "route";
  public static final String P_A_ID_STR = "p-asserted-identity";
}
