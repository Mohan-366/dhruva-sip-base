package com.cisco.dhruva.sip.hostPort;


import com.cisco.dhruva.sip.controller.ControllerConfig;
import com.cisco.dhruva.sip.proxy.ListenInterface;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.sip.util.ListenIf;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.SpringApplicationContext;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;


import javax.sip.address.SipURI;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.function.Predicate;

public class HostPortUtil {

  private static final Logger Log = DhruvaLoggerFactory.getLogger(HostPortUtil.class);
  private static ControllerConfig controllerConfig;
  static {
    controllerConfig =
            SpringApplicationContext.getAppContext().getBean(ControllerConfig.class);
  }

  private HostPortUtil() {}

  private static Predicate<ListenInterface> hostPortCheck =
          (ListenInterface listenIf) ->
                  DhruvaNetwork.getDhruvaSIPConfigProperties().isHostPortEnabled()
                          && listenIf != null
                          && listenIf.shouldAttachExternalIp();

  private static Optional<String> getHostInfo(ListenInterface listenIf) {
    if (hostPortCheck.test(listenIf)) {
      return Optional.ofNullable(DhruvaNetwork.getDhruvaSIPConfigProperties().getHostInfo());
    }
    return Optional.empty();
  }

  /**
   * replace local IP with Host IP/FQDN for public network in 'user' portion of RR.
   *
   * <p>Used during RR addition and modification
   *
   * @param uri
   * @return DsByteString of the resulting IP
   */
  public static String convertLocalIpToHostInfo(SipURI uri) {
    try {
      String transportStr =
              uri.getTransportParam();
      Transport transport = Transport.valueOf(transportStr);

      ListenIf listenIf =
              (ListenIf)
                      controllerConfig
                              .getInterface(
                                      InetAddress.getByName(uri.getHost()),
                                      transport,
                                      uri.getPort());

      Optional<String> hostInfo = getHostInfo(listenIf);

      return hostInfo
              .map(
                      h -> {
                        Log.debug("Host IP/FQDN {} obtained for {}", h, uri);
                        return h;
                      })
              .orElseGet(
                      () -> {
                        Log.debug("No host IP/FQDN found. Use local IP from {}", uri);
                        return uri.getHost();
                      });

    } catch (UnknownHostException e) {
      Log.warn("No IP address for the host[{}] found ", uri.getHost());
      return uri.getHost();
    }
  }

  /**
   * This method is also used to fetch local IP or host IP/FQDN as previous method.
   *
   * <p>Used for Via header addition
   *
   * @param listenIf
   * @return DsByteString of the resulting IP
   */
  public static String convertLocalIpToHostInfo(ListenInterface listenIf) {


    Optional<String> hostInfo = getHostInfo(listenIf);

    return hostInfo
            .map(
                    h -> {
                      Log.debug("Host IP/FQDN {} obtained for {}", h, listenIf);
                      return h;
                    })
            .orElseGet(
                    () -> {
                      Log.debug("No host IP/FQDN found. Use local IP from {}", listenIf);
                      return listenIf.getAddress();
                    });
  }

  /**
   * when 'hostPort' feature is enabled & if host IP/FQDN is attached to the URL -> get the
   * corresponding network's local IP when disabled -> host itself contains local IP, hence use that
   *
   * <p>Used for RR modification and Route header removal
   *
   * @param uri
   * @return DsByteString of the resulting IP
   */
  public static String reverseHostInfoToLocalIp(SipURI uri) {

    String transportStr =
            uri.getTransportParam();
    Transport transport = Transport.valueOf(transportStr);

    ListenIf listenIf =
            (ListenIf)
                    controllerConfig.getInterface(uri.getPort(), transport);

    if (hostPortCheck.test(listenIf)) {
      Log.debug("Local IP {} found for {}", listenIf.getAddress(), uri);
      return listenIf.getAddress();
    }
    Log.debug("No host IP/FQDN found. Use local IP for {}", uri);
    return uri.getHost();
  }
}
