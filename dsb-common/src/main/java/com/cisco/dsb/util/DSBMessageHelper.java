package com.cisco.dsb.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DSBMessageHelper {

  public static String getUserPortion(String reqUri) {

    Pattern pattern = Pattern.compile("((sip:)(.+)@(.+))");
    Matcher matcher = pattern.matcher(reqUri);
    if (!matcher.find()) return null;
    String user = matcher.group(3);
    return user;
  }

  public static String getHostPortion(String reqUri) {
    Pattern pattern = Pattern.compile("((sip:)(.+)@(.+))");
    Matcher matcher = pattern.matcher(reqUri);
    if (!matcher.find()) return null;
    String host = matcher.group(4);
    return host.split(";")[0];
  }
}
