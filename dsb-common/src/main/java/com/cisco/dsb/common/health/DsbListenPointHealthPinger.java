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
import lombok.Getter;
import lombok.Setter;
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

  @Autowired SocketFactory socketFactory;

  @Setter private SSLSocketFactory sslSocketFactory;
  @Getter @Setter private DatagramSocket datagramSocket;

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

    ServiceState serviceState;

    if (isServiceUnhealthy.get()) {
      serviceState = ServiceState.OFFLINE;
      serviceHealthManager.setServiceHealthResponseCode(500);
      logger.warn(
          "Setting the service status for service: {} as offline with /ping response code 500",
          SERVICE_NAME);

    } else {
      serviceState = ServiceState.ONLINE;
      serviceHealthManager.setServiceHealthResponseCode(200);
      logger.debug(
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

    Socket socket = null;

    try {
      if (StringUtils.equalsIgnoreCase(Transport.UDP.name(), transport)) {

        checkUdpListenPoint(host, port);

      } else if (StringUtils.equalsIgnoreCase(Transport.TCP.name(), transport)) {
        socket = (Socket) socketFactory.getSocket(transport, host, port);
      } else if (StringUtils.equalsIgnoreCase(Transport.TLS.name(), transport)) {
        if (sslSocketFactory == null) {
          logger.warn(
              "DsbHealthPinger: SslSocketFactory was not initialized, could not perform health check");
          return false;
        }
        socket = this.sslSocketFactory.createSocket(host, port);
      }
      return true;
    } catch (ConnectException | SocketTimeoutException | PortUnreachableException e) {
      logger.warn(
          "{} health check: unable to validate listen point for network : {} with error : {}",
          SERVICE_NAME,
          networkName,
          e.getMessage());
      return false;
    } catch (Exception e) {
      logger.info("Some other exception during health check has occurred");
      return true;
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

  public void checkUdpListenPoint(String host, int port) throws IOException {
    int timeoutMilis = 100;

    datagramSocket = (DatagramSocket) socketFactory.getSocket(Transport.UDP.name(), host, port);

    byte[] buf = "ping".getBytes();

    DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(host), port);
    datagramSocket.setSoTimeout(timeoutMilis);
    datagramSocket.connect(InetAddress.getByName(host), port);
    datagramSocket.send(packet);
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
