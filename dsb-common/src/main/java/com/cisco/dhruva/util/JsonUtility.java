package com.cisco.dhruva.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public class JsonUtility {

  private ObjectMapper mapper;

  public JsonUtility(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public <T> T toObject(String content, Class<T> classType) throws JSONUtilityException {
    try {
      return mapper.readValue(content, classType);
    } catch (IOException e) {
      throw new JSONUtilityException(e);
    }
  }
}
