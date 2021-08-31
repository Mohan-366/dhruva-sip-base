package com.cisco.dsb.common.sip.stack.dto;

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

  private String serverGroupName = "DefaultSG";
  private String sgPolicy = "global";

  @JsonPOJOBuilder(withPrefix = "")
  public static class DynamicServerBuilder {
    private String serverGroupName = "DefaultSG";
    private String sgPolicy = "global";
  }
}
