package com.cisco.dsb.common.health;

import com.cisco.dsb.common.transport.Transport;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Socket;

@Component
@SuppressFBWarnings(value = "UNENCRYPTED_SOCKET", justification = "baseline suppression")
public class SocketFactory {

  public Object getSocket(String transport, String host, int port) throws IOException {
    if (transport == null) {
      return null;
    } else if (StringUtils.equalsIgnoreCase(Transport.UDP.name(), transport)) {
      return new DatagramSocket();
    } else {
      return new Socket(host, port);
    }
  }
}
