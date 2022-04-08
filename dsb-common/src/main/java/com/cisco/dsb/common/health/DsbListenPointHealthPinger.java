package com.cisco.dsb.common.health;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.tls.DsbTrustManager;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.wx2.client.health.ServiceHealthPinger;
import com.cisco.wx2.dto.health.ServiceHealth;
import com.cisco.wx2.dto.health.ServiceState;
import com.cisco.wx2.dto.health.ServiceType;
import com.cisco.wx2.server.health.ServiceHealthManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.net.*;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.PostConstruct;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import lombok.CustomLog;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@CustomLog
@Component
public class DsbListenPointHealthPinger implements ServiceHealthPinger {

  public static final String TLS_PROTOCOL = "TLSv1.2";
  public static final String SERVICE_NAME = "dsb-calling-app";
  @Autowired CommonConfigurationProperties commonConfigurationProperties;

  @Autowired ServiceHealthManager serviceHealthManager;

  @Autowired DsbTrustManager dsbTrustManager;

  @Nullable @Autowired KeyManager keyManager;

  private SSLSocketFactory sslSocketFactory;

  /* @Autowired
  OptionsPingTransaction optionsPingTransaction;*/

  @PostConstruct
  public void initialize() throws Exception {
    List<SIPListenPoint> listenPoints = commonConfigurationProperties.getListenPoints();

    if (listenPoints.stream()
        .filter(Objects::nonNull)
        .anyMatch(
            listenPoint ->
                StringUtils.equalsIgnoreCase(
                    Transport.TLS.name(), listenPoint.getTransport().name()))) {
      this.init(dsbTrustManager, keyManager);
    }

    /*    for (SIPListenPoint eachListenPoint : listenPoints) {
      if (StringUtils.equalsIgnoreCase(Transport.TLS.name(), eachListenPoint.getTransport().name())) {
        this.init(dsbTrustManager, keyManager);
      }
    }*/

  }

  /**
   * This API periodically checks periodically if the listenpoints are available, and based on that
   * sets the response code of '/ping' API
   *
   * @return
   */
  @SneakyThrows
  @Override
  public ServiceHealth ping() {
    List<SIPListenPoint> listenPoints = commonConfigurationProperties.getListenPoints();

    AtomicBoolean isServiceUnhealthy = new AtomicBoolean(false);
    StringBuilder messageBuilder = new StringBuilder();

    listenPoints.forEach(
        listenPoint -> {
          boolean isListening =
              isListening(
                  listenPoint.getName(),
                  listenPoint.getHostIPAddress(),
                  listenPoint.getPort(),
                  listenPoint.getTransport().name());

          messageBuilder
              .append(listenPoint.getName())
              .append(" is healthy: ")
              .append(isListening)
              .append(" ");
          if (!isListening) {
            isServiceUnhealthy.set(true);
          }
        });

    /*
        for (SIPListenPoint eachListenPoint : listenPoints) {

          boolean isListening =
              isListening(
                  eachListenPoint.getName(),
                  eachListenPoint.getHostIPAddress(),
                  eachListenPoint.getPort(),
                  eachListenPoint.getTransport().name());

          messageBuilder
              .append(eachListenPoint.getName())
              .append(" is healthy: ")
              .append(isListening)
              .append(" ");
          if (!isListening) {
            isServiceUnhealthy.set(true);
            break;
          }
        }
    */

    ServiceState serviceState;

    if (isServiceUnhealthy.get()) {
      serviceState = ServiceState.OFFLINE;
      serviceHealthManager.setServiceHealthResponseCode(500);
      logger.info(
          "Setting the service status for service: {} as offline with /ping response code 500",
          SERVICE_NAME);

    } else {
      serviceState = ServiceState.ONLINE;
      serviceHealthManager.setServiceHealthResponseCode(200);
      logger.info(
          "Setting the service status for service: {} as online with /ping response code 200",
          SERVICE_NAME);
    }

    return ServiceHealth.builder()
        .serviceName(SERVICE_NAME)
        .serviceType(ServiceType.REQUIRED)
        .serviceState(serviceState)
        .message(messageBuilder.toString())
        .build();
  }

  /**
   * This method checks the transport and based on that finds if the listenpoint ports are available
   * or not
   *
   * @param networkName
   * @param host
   * @param port
   * @param transport
   * @return
   */
  @SuppressFBWarnings(value = "UNENCRYPTED_SOCKET", justification = "baseline suppression")
  public boolean isListening(String networkName, String host, int port, String transport) {
    DatagramSocket datagramSocket = null;
    Socket socket = null;

    try {
      if (StringUtils.equalsIgnoreCase(Transport.UDP.name(), transport)) {
        datagramSocket = new DatagramSocket();
        checkUdpListenPoint(datagramSocket, host, port);
        // scanUdpAddress(host,port,100);
      } else if (StringUtils.equalsIgnoreCase(Transport.TCP.name(), transport)) {
        socket = new Socket(host, port);
      } else {

        socket = this.sslSocketFactory.createSocket(host, port);
      }
      return true;
    } catch (Exception e) {
      logger.warn(
          "{} health check: unable to validate listen point for network : {} with error : {}",
          SERVICE_NAME,
          networkName,
          e.getMessage());
      return false;
    } finally {
      if (socket != null)
        try {
          socket.close();
        } catch (Exception e) {
          logger.warn("unable to close socket after checking listen point");
        }

      if (datagramSocket != null) {
        try {
          datagramSocket.close();
        } catch (Exception e) {
          logger.warn("unable to close socket after checking listen point");
        }
      }
    }
  }

  private void checkUdpListenPoint(DatagramSocket datagramSocket, String host, int port)
      throws IOException {
    int timeoutMilis = 100;

    byte[] buf = new byte[128];

    DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(host), port);
    datagramSocket.setSoTimeout(timeoutMilis);
    datagramSocket.connect(InetAddress.getByName(host), port);
    datagramSocket.send(packet);
    // datagramSocket.isConnected();
    datagramSocket.receive(packet);
  }

  public static boolean scanUdpAddress(String host, int portNo, int timeoutMillis) {
    try {
      InetAddress ia = InetAddress.getByName(host);
      byte[] bytes = new byte[128];
      DatagramPacket dp = new DatagramPacket(bytes, bytes.length);
      DatagramSocket ds = new DatagramSocket();
      ds.setSoTimeout(timeoutMillis);
      ds.connect(ia, portNo);
      ds.send(dp);
      ds.isConnected();
      ds.receive(dp);
      ds.close();
    } catch (SocketTimeoutException e) {
      return true;
    } catch (Exception ignore) {
    }
    return false;
  }

  public void init(@NotNull TrustManager trustManager, @NotNull KeyManager keyManager)
      throws Exception {
    if (trustManager == null || keyManager == null) {
      throw new IllegalArgumentException("trustManager and keyManager cannot be null");
    }
    SecureRandom secureRandom = new SecureRandom();
    secureRandom.nextInt();

    SSLContext sslContext = SSLContext.getInstance(TLS_PROTOCOL);
    sslContext.init(new KeyManager[] {keyManager}, new TrustManager[] {trustManager}, secureRandom);

    sslSocketFactory = sslContext.getSocketFactory();
  }
}
