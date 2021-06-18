package com.cisco.dsb.common.dns;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

public class Util {

  static Name getName(String name) {
    try {
      Name retVal = Name.fromString(name);
      return retVal;
    } catch (TextParseException e) {
      throw new RuntimeException(e);
    }
  }

  static List<Record> srvNodes(String... nodeNames) {
    return Stream.of(nodeNames)
        .map(input -> Record.newRecord(getName(input), Type.SRV, DClass.IN))
        .collect(Collectors.toList());
  }

  static List<Record> aNodes(String... nodeNames) {
    return Stream.of(nodeNames)
        .map(input -> Record.newRecord(getName(input), Type.A, DClass.IN))
        .collect(Collectors.toList());
  }

  private static class TestDNSServer {
    private Thread thread = null;
    private volatile boolean running = false;
    private static final int UDP_SIZE = 512;
    private final int port;
    private int requestCount = 0;

    TestDNSServer(int port) {
      this.port = port;
    }

    public void start() {
      running = true;
      thread =
          new Thread(
              () -> {
                try {
                  serve();
                } catch (IOException ex) {
                  stop();
                  throw new RuntimeException(ex);
                }
              });
      thread.start();
    }

    public void stop() {
      running = false;
      thread.interrupt();
      thread = null;
    }

    public int getRequestCount() {
      return requestCount;
    }

    private void serve() throws IOException {
      DatagramSocket socket = new DatagramSocket(port);
      while (running) {
        process(socket);
      }
    }

    private void process(DatagramSocket socket) throws IOException {
      byte[] in = new byte[UDP_SIZE];

      // Read the request
      DatagramPacket indp = new DatagramPacket(in, UDP_SIZE);
      socket.receive(indp);
      ++requestCount;
      // logger.info(String.format("processing... %d", requestCount));

      // Build the response
      Message request = new Message(in);
      Message response = new Message(request.getHeader().getID());
      response.addRecord(request.getQuestion(), Section.QUESTION);
      // Add answers as needed
      response.addRecord(
          Record.fromString(Name.root, Type.A, DClass.IN, 86400, "1.2.3.4", Name.root),
          Section.ANSWER);

      // Make it timeout, comment this section if a success response is needed
      try {
        Thread.sleep(5000);
      } catch (InterruptedException ex) {
        // logger.error("Interrupted");
        return;
      }

      byte[] resp = response.toWire();
      DatagramPacket outdp =
          new DatagramPacket(resp, resp.length, indp.getAddress(), indp.getPort());
      socket.send(outdp);
    }
  }
}
