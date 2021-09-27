package com.cisco.dsb.trunk.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@JsonDeserialize(builder = DynamicServer.DynamicServerBuilder.class)
@Getter
@ToString
@Builder(builderClassName = "DynamicServerBuilder", toBuilder = true)
public class DynamicServer {

  private String serverGroupName;
  private String sgPolicy;

  @JsonPOJOBuilder(withPrefix = "")
  public static class DynamicServerBuilder {
    private String serverGroupName;
    private String sgPolicy;
  }
}
