package com.cisco.dsb.common.util;

import com.cisco.wx2.util.JsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtilFactory {

  private static JsonUtility csbJsonUtility = new JsonUtility(JsonUtil.getObjectMapper());

  // Workaround Until https://sqbu-github.cisco.com/WebExSquared/cisco-spark-base/issues/1291 is
  // fixed
  private static JsonUtility locaJsonUtility = new JsonUtility(new ObjectMapper());

  public enum JsonUtilType {
    CSB,
    LOCAL
  }

  public static JsonUtility getInstance() {
    return csbJsonUtility;
  }

  public static JsonUtility getInstance(JsonUtilType jsonUtilType) {
    JsonUtility jsonUtility = csbJsonUtility;
    switch (jsonUtilType) {
      case CSB:
        jsonUtility = csbJsonUtility;
        break;
      case LOCAL:
        jsonUtility = locaJsonUtility;
        break;
    }
    return jsonUtility;
  }
}
