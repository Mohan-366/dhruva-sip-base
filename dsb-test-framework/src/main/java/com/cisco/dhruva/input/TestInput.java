package com.cisco.dhruva.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Getter;
import lombok.ToString;

public class TestInput {
  public TestInput() {}

  @JsonProperty("testCaseConfig")
  @Getter
  TestCaseConfig[] testCaseConfig;

  public static class TestCaseConfig {
    public TestCaseConfig() {}

    @Getter String description;
    @Getter String id;
    @Getter private DSB dsb;

    @JsonProperty("uac")
    @Getter
    UacConfig uacConfig;

    @JsonProperty("uasGroup")
    @Getter
    UasConfig[] uasConfigs;

    @JsonProperty("skipTest")
    @Getter
    boolean skipTest;
  }

  public enum Transport {
    tcp,
    tls,
    udp
  };

  public enum ElementStatus {
    up,
    down
  };

  public enum Type {
    request,
    response
  };

  public enum Direction {
    sends,
    receives
  }

  public static class UacUasCommonConfig {

    @Getter String myUri;
    @Getter String ip;
    @Getter int port;
    @Getter Transport transport;
    @Getter Message[] messages;
  }

  public static class UacConfig extends UacUasCommonConfig {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class UasConfig extends UacUasCommonConfig {}

  @ToString
  public static class Message {
    public Message() {}

    @JsonProperty("description")
    @JsonIgnore
    @Getter
    private String description;

    @JsonProperty("type")
    @Getter
    private Type type;

    @JsonProperty("name")
    @Getter
    private String name;

    @JsonProperty("direction")
    @Getter
    private Direction direction;

    @JsonProperty("forRequest")
    @Getter
    private String forRequest;

    @JsonProperty("parameters")
    @Getter
    Parameters parameters;

    @JsonProperty("validation")
    @Getter
    private Map<String, Object> validation;

    @JsonProperty("optional")
    @Getter
    private boolean optional;
  }

  public static class Parameters {
    public Parameters() {}

    @JsonProperty("requestParameters")
    @Getter
    RequestParameters requestParameters;

    @JsonProperty("responseParameters")
    @Getter
    ResponseParameters responseParameters;
  }

  public static class RequestParameters {
    public RequestParameters() {}

    @JsonProperty("headerAdditions")
    @Getter
    Map<String, String> headerAdditions;

    @JsonProperty("headerReplacements")
    @Getter
    Map<String, String> headerReplacements;
  }

  public static class ResponseParameters {
    public ResponseParameters() {}

    @JsonProperty("headerAdditions")
    @Getter
    Map<String, String> headerAdditions;

    @JsonProperty("headerReplacements")
    @Getter
    Header headerReplacements[];

    @Getter String responseCode;

    @JsonProperty("reasonPhrase")
    @Getter
    String reasonPhrase;
  }

  public static class Header {
    @JsonProperty("headerName")
    @Getter
    String headerName;

    @JsonProperty("address")
    @Getter
    String address;

    @JsonProperty("headerParams")
    @Getter
    Map<String, String> headerParams;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class DSB {

    // DSB IP for client communication - Public IP
    @Getter private ProxyCommunication clientCommunicationInfo;
  }

  /** Nic - Network Interface Card */
  @ToString
  public static class ProxyCommunication {

    @Getter private String ip;
    @Getter private int port;
    @Getter private String transport;
  }
}
