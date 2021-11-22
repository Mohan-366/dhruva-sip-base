package com.cisco.dsb.trunk.dto;

import com.cisco.dsb.common.transport.Transport;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@JsonDeserialize(builder = ServerGroupElement.ServerGroupElementBuilder.class)
public class ServerGroupElement {

  @Builder.Default @JsonProperty String ipAddress = "129.0.0.1";

  @Builder.Default @JsonProperty Integer port = 5060;

  @Builder.Default @JsonProperty Transport transport = Transport.TLS;

  @Builder.Default @JsonProperty float qValue = 0.9f;

  @Builder.Default @JsonProperty Integer weight = -1;
}
