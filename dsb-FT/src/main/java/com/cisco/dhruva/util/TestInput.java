package com.cisco.dhruva.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

public class TestInput {
  public TestInput() {}

  @JsonProperty("cpProperties")
  Map<String, Object> cpProperties;

  @JsonProperty("testCaseConfig")
  TestCaseConfig[] testCaseConfig;

  public Map<String, Object> getCpProperties() {
    return cpProperties;
  }

  public void setCpProperties(Map<String, Object> cpProperties) {
    this.cpProperties = cpProperties;
  }

  public TestCaseConfig[] getConfig() {
    return testCaseConfig;
  }

  public void setConfig(TestCaseConfig[] config) {
    this.testCaseConfig = config;
  }

  public static class TestCaseConfig {
    public TestCaseConfig() {}

    String description;
    String id;
    private DSB DSB;
    private List<DnsLookup> dnsLookups;
    private MRS mrs;
    private U2C u2c;

    @JsonProperty("uac")
    UacConfig uac;

    @JsonProperty("uasGroup")
    UasConfig[] uas;

    @JsonProperty("skipTest")
    boolean skipTest;

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public UacConfig getUacConfig() {
      return uac;
    }

    public void setUacConfig(UacConfig uacConfig) {
      this.uac = uacConfig;
    }

    public UasConfig[] getUasCofig() {
      return uas;
    }

    public void setUasCofig(UasConfig[] uasCofig) {
      this.uas = uasCofig;
    }

    public DSB getDSB() {
      return DSB;
    }

    public void setDSB(DSB DSB) {
      this.DSB = DSB;
    }

    public List<DnsLookup> getDnsLookups() {
      return dnsLookups;
    }

    public void setDnsLookups(List<DnsLookup> dnsLookups) {
      this.dnsLookups = dnsLookups;
    }

    public MRS getMrs() {
      return mrs;
    }

    public void setMrs(MRS mrs) {
      this.mrs = mrs;
    }

    public U2C getU2c() {
      return u2c;
    }

    public void setU2c(U2C u2c) {
      this.u2c = u2c;
    }

    public boolean isSkipTest() {
      return skipTest;
    }

    public void setSkipTest(boolean skipTest) {
      this.skipTest = skipTest;
    }
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

    String myUri;
    String ip;
    int port;
    Transport transport;
    Message[] messages;

    public String getMyUri() {
      return myUri;
    }

    public void setMyUri(String myUri) {
      this.myUri = myUri;
    }

    public String getIp() {
      return ip;
    }

    public void setIp(String ip) {
      this.ip = ip;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
    }

    public Transport getTransport() {
      return transport;
    }

    public void setTransport(Transport transport) {
      this.transport = transport;
    }

    public Message[] getMessages() {
      return messages;
    }

    public void setMessages(Message[] messages) {
      this.messages = messages;
    }
  }

  public static class UacConfig extends UacUasCommonConfig {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class UasConfig extends UacUasCommonConfig {}

  public static class Message {
    public Message() {}

    @JsonProperty("description")
    @JsonIgnore
    private String description;

    @JsonProperty("type")
    private Type type;

    @JsonProperty("name")
    private String name;

    @JsonProperty("direction")
    private Direction direction;

    @JsonProperty("forRequest")
    private String forRequest;

    @JsonProperty("parameters")
    Parameters parameters;

    @JsonProperty("validation")
    private Map<String, Object> validation;

    @JsonProperty("optional")
    private boolean optional;

    public Direction getDirection() {
      return direction;
    }

    public void setDirection(Direction direction) {
      this.direction = direction;
    }

    public Type getType() {
      return type;
    }

    public void setType(Type type) {
      this.type = type;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getForRequest() {
      return forRequest;
    }

    public void setForRequest(String forRequest) {
      this.forRequest = forRequest;
    }

    public Map<String, Object> getValidation() {
      return validation;
    }

    public void setValidation(Map<String, Object> validation) {
      this.validation = validation;
    }

    public Parameters getParameters() {
      return parameters;
    }

    public void setParameters(Parameters parameters) {
      this.parameters = parameters;
    }

    public boolean isOptional() {
      return optional;
    }

    public void setOptional(boolean optional) {
      this.optional = optional;
    }

    @Override
    public String toString() {
      return "Message [description="
          + description
          + ", type="
          + type
          + ", name="
          + name
          + ", direction="
          + direction
          + ", forRequest="
          + forRequest
          + ", parameters="
          + parameters
          + ", validation="
          + validation
          + ", optional="
          + optional
          + "]";
    }
  }

  public static class Parameters {
    public Parameters() {}

    @JsonProperty("requestParameters")
    RequestParameters requestParameters;

    @JsonProperty("responseParameters")
    ResponseParameters responseParameters;

    public RequestParameters getRequestParameters() {
      return requestParameters;
    }

    public void setRequestParameters(RequestParameters requestParameters) {
      this.requestParameters = requestParameters;
    }

    public ResponseParameters getResponseParameters() {
      return responseParameters;
    }

    public void setResponseParameters(ResponseParameters responseParameters) {
      this.responseParameters = responseParameters;
    }
  }

  public static class RequestParameters {
    public RequestParameters() {}

    @JsonProperty("headerAdditions")
    Map<String, String> headerAdditions;

    public Map<String, String> getHeaderAdditions() {
      return headerAdditions;
    }

    public void setHeaderAdditions(Map<String, String> headerAdditions) {
      this.headerAdditions = headerAdditions;
    }
  }

  public static class ResponseParameters {
    public ResponseParameters() {}

    @JsonProperty("headerAdditions")
    Map<String, String> headerAdditions;

    String responseCode;

    @JsonProperty("reasonPhrase")
    String reasonPhrase;

    public Map<String, String> getHeaderAdditions() {
      return headerAdditions;
    }

    public void setHeaderAdditions(Map<String, String> headerAdditions) {
      this.headerAdditions = headerAdditions;
    }

    public String getResponseCode() {
      return responseCode;
    }

    public void setResponseCode(String responseCode) {
      this.responseCode = responseCode;
    }

    public String getResponsePhrase() {
      return reasonPhrase;
    }

    public void setResponsePhrase(String reasonPhrase) {
      this.reasonPhrase = reasonPhrase;
    }
  }

  public static class SRVRecord extends NicIpPort {

    private int weight = 80;
    private int priority = 10;

    public SRVRecord() {}

    public SRVRecord(String ip, int port) {
      super(ip, port);
    }

    public SRVRecord(String ip, int port, int weight, int priority) {
      super(ip, port);
      this.weight = weight;
      this.priority = priority;
    }

    public int getWeight() {
      return weight;
    }

    public void setWeight(int weight) {
      this.weight = weight;
    }

    public int getPriority() {
      return priority;
    }

    public void setPriority(int priority) {
      this.priority = priority;
    }

    @Override
    public String toString() {
      return "SRVRecords [weight="
          + weight
          + ", priority="
          + priority
          + ", getIp()="
          + getIp()
          + ", getPort()="
          + getPort()
          + "]";
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class DSB {

    // Service Provider - Public IP
    private NicIpPort clientCommunicationInfo;

    // Media Engine - Private IP
    private NicIpPort serverCommunicationInfo;

    public NicIpPort getClientCommunicationInfo() {
      return clientCommunicationInfo;
    }

    public void setClientCommunicationInfo(NicIpPort clientCommunicationInfo) {
      this.clientCommunicationInfo = clientCommunicationInfo;
    }

    public NicIpPort getServerCommunicationInfo() {
      return serverCommunicationInfo;
    }

    public void setServerCommunicationInfo(NicIpPort serverCommunicationInfo) {
      this.serverCommunicationInfo = serverCommunicationInfo;
    }

    @Override
    public String toString() {
      return "DSBNetworkDetail [clientCommunicationInfo="
          + clientCommunicationInfo
          + ", serverCommunicationInfo="
          + serverCommunicationInfo
          + "]";
    }
  }

  /**
   * Nic - Network Interface Card
   *
   * @author velasoka
   */
  public static class NicIpPort {

    private String ip;
    private int port;

    public NicIpPort() {}

    public NicIpPort(String ip, int port) {
      this.ip = ip;
      this.port = port;
    }

    public String getIp() {
      return ip;
    }

    public void setIp(String ip) {
      this.ip = ip;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
    }

    public InetAddress getInetAddress() throws UnknownHostException {
      return InetAddress.getByName(ip);
    }

    @Override
    public String toString() {
      return "IpPort [ip=" + ip + ", port=" + port + "]";
    }
  }

  public static class DnsLookup {

    private String host;
    private Transport transport;
    private List<SRVRecord> srvRecords;

    @JsonProperty("aRecords")
    private List<String> aRecords;

    private boolean msFederationLookup;

    public String getHost() {
      return host;
    }

    public void setHost(String host) {
      this.host = host;
    }

    public Transport getTransport() {
      return transport;
    }

    public void setTransport(Transport transport) {
      this.transport = transport;
    }

    public List<SRVRecord> getSrvRecords() {
      return srvRecords;
    }

    public void setSrvRecords(List<SRVRecord> srvRecords) {
      this.srvRecords = srvRecords;
    }

    public List<String> getARecords() {
      return aRecords;
    }

    public void setARecords(List<String> aRecords) {
      this.aRecords = aRecords;
    }

    public boolean getMsFederationLookup() {
      return msFederationLookup;
    }

    public void setMsFederationLookup(boolean msFederationLookup) {
      this.msFederationLookup = msFederationLookup;
    }

    @Override
    public String toString() {
      return "DnsLookup [host="
          + host
          + ", transport="
          + transport
          + ", srvRecords="
          + srvRecords
          + ", aRecords="
          + aRecords
          + ", msFederationLookup="
          + msFederationLookup
          + "]";
    }
  }

  public static class MRS {

    private int responseCode;
    private ResponseBody responseBody;
    private String exception = null;

    public int getResponseCode() {
      return responseCode;
    }

    public void setResponseCode(int responseCode) {
      this.responseCode = responseCode;
    }

    public ResponseBody getResponseBody() {
      return responseBody;
    }

    public void setResponseBody(ResponseBody responseBody) {
      this.responseBody = responseBody;
    }

    public String getException() {
      return exception;
    }

    public void setException(String exception) {
      this.exception = exception;
    }
  }

  public static class U2C {

    private int errorCode;
    private String errorMessage;
    private String l2sipEndPoint;
    private String exception = null;

    public int getErrorCode() {
      return errorCode;
    }

    public void setErrorCode(int errorCode) {
      this.errorCode = errorCode;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
    }

    public void setL2sipEndPoint(String l2sipEndPoint) {
      this.l2sipEndPoint = l2sipEndPoint;
    }

    public String getL2sipEndPoint() {
      return l2sipEndPoint;
    }

    public String getException() {
      return exception;
    }

    public void setException(String exception) {
      this.exception = exception;
    }
  }

  public static class ResponseBody {
    String fullSiteUrl;
    String l2SipEndpoint;
    String cmrVersion;
    boolean redirect;
    String env;
    String location;

    public String getFullSiteUrl() {
      return fullSiteUrl;
    }

    public void setFullSiteUrl(String fullSiteUrl) {
      this.fullSiteUrl = fullSiteUrl;
    }

    public String getL2SipEndpoint() {
      return l2SipEndpoint;
    }

    public void setL2SipEndpoint(String l2SipEndpoint) {
      this.l2SipEndpoint = l2SipEndpoint;
    }

    public String getCmrVersion() {
      return cmrVersion;
    }

    public void setCmrVersion(String cmrVersion) {
      this.cmrVersion = cmrVersion;
    }

    public boolean isRedirect() {
      return redirect;
    }

    public void setRedirect(boolean redirect) {
      this.redirect = redirect;
    }

    public String getEnv() {
      return env;
    }

    public void setEnv(String env) {
      this.env = env;
    }

    public String getLocation() {
      return location;
    }

    public void setLocation(String location) {
      this.location = location;
    }
  }
}
