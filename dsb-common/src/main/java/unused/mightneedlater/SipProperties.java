package unused.mightneedlater;

import com.cisco.dsb.common.sip.stack.dto.SipListenPoint;
import com.cisco.dsb.common.transport.Transport;
import gov.nist.javax.sip.header.Server;
import java.util.List;
import java.util.Set;

/** Interface to deal with properties of the sip stack */
public interface SipProperties {

  /**
   * Returns the address in which the SIP server is listening Defaults to IPvAddress
   *
   * @return sip address
   */
  String getSipAddress();

  /**
   * Flag whether to build a key store from {@link #getSipCertificate()} and {@link
   * #getSipPrivateKey()} or load a keystore from {@link #getSipKeyStore()} or {@link
   * #getSipKeyStoreEncoded()}.
   *
   * @return
   */
  boolean useLoadedKeyStore();

  /**
   * Get key store path for this server
   *
   * @return
   */
  String getSipKeyStore();

  /**
   * Get key store for this server, base 64 encoded
   *
   * @return
   */
  String getSipKeyStoreEncoded();

  /**
   * Get password for key store
   *
   * @return
   */
  String getSipKeyStorePassword();

  /**
   * Get key store type
   *
   * @return
   */
  String getSipKeyStoreType();

  /**
   * Get the list of listening points for sip proxy stack.
   *
   * @return
   */
  List<SipListenPoint> getListeningPoints();

  /**
   * get sip stack address for SIP UA server. Default value should be 127.0.0.1
   *
   * @return
   */
  String getLocalUAAddress();

  /**
   * get sip stack port for SIP UA server. Default value should be 5060
   *
   * @return
   */
  int getLocalUAPort();

  /**
   * Returns the transport used for Proxy <-> UA communication.
   *
   * @return
   */
  Transport getUAProxyInternalTransport();

  /**
   * get sip stack port for SIP Proxy server. Default value should be 5060
   *
   * @return
   */
  int getProxyInternalPort();

  /**
   * Get the list of LB's used for the cluster. Default should be #{getSipContactAddress}
   *
   * @return
   */
  List<String> getLBAddressList();

  /** Returns whether TCP port 5060 is enabled on proxy. */
  boolean isTcpEnabled();

  /**
   * Get server instance
   *
   * @return
   */
  Server getSipInstance();

  long getWarmUpWaitTime();

  /** @return Period between SIP RFC 5626 keep alives in milliseconds. */
  long getKeepAlivePeriod();

  /** Metrics properties. */
  long getMetricsTimerCacheSize();

  long getMetricsInviteCacheSize();

  long getConnectionSamplePeriod();

  boolean isLocalDevEnvironment();

  boolean isPreflightEnvironment();

  boolean isEcpEnvironment();

  String getSipCertificate();

  String getSipPrivateKey();

  String getSipPrivateKeyPassword();

  boolean isNioEnabled();

  boolean isAsyncForwardingEnabled();

  // RequestErrorUtilsSettings getRequestErrorUtilsSettings();

  long getInviteCacheDurationMinutes();

  long getCacheDeferredSubscribeDurationSeconds();

  /** @return Set of SIP connection settings. */
  // List<SipConnectionSettings> getSipConnectionSettings();

  boolean isLogKeepAlivesEnabled();

  Set<String> getLogKeepAliveAddresses();

  String[] getSipTlsProtocols();

  int getSslHandshakeTimeout();

  int getInviteResponseTimeoutSecs();

  int getInviteTxTimeoutSecs();

  int getNonInviteTxTimeoutSecs();

  String getPstnCallbackDisplayName();

  String getPstnCallbackNumber();

  String getNomadAllocId();

  String toTraceString();

  boolean isSEEnabled();
  // UriAndDomainWhiteList getSessionRefreshWhiteList();
  int getMinSE();

  int getMaxHttpRequestForward();

  boolean isCanary();

  boolean isSipRateLimiterValveEnabled();

  boolean sipRateLimiterValveCheckClientCert();

  Set<String> getSitesToAdvertiseUpdates();

  boolean advertiseUpdateForAllCalls();

  Set<String> getSitesToAdvertiseCallInfo();

  String getCiscoVideoTrafficClass();

  boolean enableVoipBlockListPolicy();

  /**
   * Add a new property to check if loadbalancer is configured for this node.
   *
   * @return true, when load balancer is configured. else false.
   */
  boolean isLoadBalancerConfigured();

  public boolean includeOldLoopbackParam();

  public boolean enableSSLSocketWrapperForIncoming();

  /**
   * Method to get MeetingIdHostKeyParserFactory from the meetingIdHostKeyParsingSettings of the
   * service.
   *
   * @return
   */
  // MeetingIdHostKeyParserFactory meetingIdHostKeyParsingSettings();
}
