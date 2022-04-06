package com.cisco.dhruva.application.health;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.tls.DsbTrustManager;
import com.cisco.wx2.client.health.ServiceHealthPinger;
import com.cisco.wx2.dto.health.ServiceHealth;
import com.cisco.wx2.dto.health.ServiceState;
import com.cisco.wx2.dto.health.ServiceType;
import com.cisco.wx2.server.health.ServiceHealthManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.CustomLog;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.List;

@CustomLog
@Component
public class DsbListenPointHealthPinger implements ServiceHealthPinger {

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

    for (SIPListenPoint eachListenPoint : listenPoints) {
      if (StringUtils.equalsIgnoreCase("TLS", eachListenPoint.getTransport().name())) {
        this.init(dsbTrustManager, keyManager);
      }
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

    boolean isServiceUnhealthy = false;
    StringBuilder messageBuilder = new StringBuilder();

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
        isServiceUnhealthy = true;
        break;
      }
    }

    ServiceState serviceState;

    if (isServiceUnhealthy) {
      serviceState = ServiceState.OFFLINE;
      serviceHealthManager.setServiceHealthResponseCode(503);

    } else {
      serviceState = ServiceState.ONLINE;
      serviceHealthManager.setServiceHealthResponseCode(200);
    }

    return ServiceHealth.builder()
        .serviceName("calling-app")
        .serviceType(ServiceType.REQUIRED)
        // .serviceInstance(csbEvaluatedServiceHealth.getServiceInstance())
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
      if (StringUtils.equalsIgnoreCase("UDP", transport)) {
        checkUdpListenPoint(host, port);
      } else if (StringUtils.equalsIgnoreCase("TCP", transport)) {
        socket = new Socket(host, port);
      } else {
        socket = this.sslSocketFactory.createSocket(host, port);
      }
      return true;
    } catch (Exception e) {
      logger.warn(
          "calling-app health check: unable to validate listen point for network : {} with error : {}",
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

  private void checkUdpListenPoint(String host, int port) throws IOException {
    DatagramSocket datagramSocket;
    byte[] buf = new byte[256];
    datagramSocket = new DatagramSocket();
    DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(host), port);
    datagramSocket.send(packet);
  }

  public void init(@NotNull TrustManager trustManager, @NotNull KeyManager keyManager)
      throws Exception {
    if (trustManager == null || keyManager == null) {
      throw new IllegalArgumentException("trustManager and keyManager cannot be null");
    }
    SecureRandom secureRandom = new SecureRandom();
    secureRandom.nextInt();

    SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
    sslContext.init(new KeyManager[] {keyManager}, new TrustManager[] {trustManager}, secureRandom);

    sslSocketFactory = sslContext.getSocketFactory();
  }
}
