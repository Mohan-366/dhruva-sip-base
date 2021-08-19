package com.cisco.dsb.common.sip.stack.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Arrays;
import java.util.List;
import lombok.*;

@JsonDeserialize(builder = StaticServer.StaticServerBuilder.class)
@Getter
@ToString
@Builder(builderClassName = "StaticServerBuilder", toBuilder = true)
public class StaticServer {

  private String serverGroupName = "DefaultSG";
  private String networkName = "DefaultNetwork";
  private String lbType = "call-id";
  private List<ServerGroupElement> elements = Arrays.asList(ServerGroupElement.builder().build());
  private String sgPolicy = "global";

  @JsonPOJOBuilder(withPrefix = "")
  public static class StaticServerBuilder {
    private String serverGroupName = "DefaultSG";
    private String networkName = "DefaultNetwork";
    private String lbType = "call-id";
    private List<ServerGroupElement> elements = Arrays.asList(ServerGroupElement.builder().build());
    private String sgPolicy = "global";
  }
}
