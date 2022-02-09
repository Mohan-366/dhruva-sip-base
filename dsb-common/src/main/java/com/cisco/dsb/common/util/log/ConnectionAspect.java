package com.cisco.dsb.common.util.log;

import com.cisco.dsb.common.sip.jain.channelCache.DsbJainSipTLSMessageProcessor;
import com.cisco.dsb.common.sip.jain.channelCache.DsbNioTCPMessageProcessor;
import com.cisco.dsb.common.sip.jain.channelCache.DsbNioTlsMessageProcessor;
import com.cisco.dsb.common.sip.jain.channelCache.DsbSipTCPMessageProcessor;
import com.cisco.dsb.common.util.log.event.Event;
import gov.nist.javax.sip.stack.*;
import java.net.InetAddress;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import lombok.CustomLog;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.annotation.Order;

@Aspect
@CustomLog
public class ConnectionAspect {

  /**
   * Aspect to capture IO exceptions and create DSB connection failure Event for procesing messages
   * via IO handler
   *
   * @param jp
   * @param ex
   */
  @AfterThrowing(
      value = "execution(* gov.nist.javax.sip.stack.IOHandler.sendBytes(..))",
      throwing = "ex")
  public void logIOExceptionOnBlockingConnection(JoinPoint jp, Exception ex) {
    logger.debug("exception from io handler found");
    this.logDSBEventForIOException(jp, ex);
  }

  private void logDSBEventForIOException(JoinPoint jp, Exception ex) {
    Object[] args = jp.getArgs();

    InetAddress senderAddress = (InetAddress) args[0];
    InetAddress receiverAddress = (InetAddress) args[1];
    int contactPort = (int) args[2];
    String transport = String.valueOf(args[3]);
    boolean isClient = (Boolean) args[5];
    MessageChannel messageChannel = (MessageChannel) args[6];

    Event.emitConnectionErrorEvent(transport, null, ex);
  }

  /**
   * Aspect to capture IO exceptions and create DSB connection failure Event for procesing messages
   * via non blocking IO handler
   *
   * @param jp
   * @param ex
   */
  @AfterThrowing(
      value = "execution(* gov.nist.javax.sip.stack.NIOHandler.sendBytes(..))",
      throwing = "ex")
  public void logIOExceptionOnNBConnection(JoinPoint jp, Exception ex) {
    logger.debug("exception from non blocking io handler found");
    this.logDSBEventForIOException(jp, ex);
  }

  /**
   * Aspect for capturing successful sslHandshakeCompletion
   *
   * @param pjp
   * @param handshakeCompletedEvent
   * @throws Throwable
   */
  @Around(
      "execution(public void gov.nist.javax.sip.stack.HandshakeCompletedListenerImpl.handshakeCompleted(javax.net.ssl.HandshakeCompletedEvent)) && args(handshakeCompletedEvent)")
  public void handshakeCompleted(
      ProceedingJoinPoint pjp, HandshakeCompletedEvent handshakeCompletedEvent) throws Throwable {
    logger.info(
        "HandshakeCompletedListenerImpl.handshakeCompleted: listener{} event:\n{}",
        traceHashcode(pjp.getTarget()),
        handshakeCompletedEventToString(handshakeCompletedEvent));
    pjp.proceed();
  }

  @Around(
      "execution(public javax.net.ssl.HandshakeCompletedEvent gov.nist.javax.sip.stack.HandshakeCompletedListenerImpl.getHandshakeCompletedEvent())")
  public HandshakeCompletedEvent getHandshakeCompletedEvent(ProceedingJoinPoint pjp)
      throws Throwable {
    logger.debug(
        "HandshakeCompletedListenerImpl.getHandshakeCompletedEvent enter: listener{}",
        traceHashcode(pjp.getTarget()));
    HandshakeCompletedEvent event = (HandshakeCompletedEvent) pjp.proceed();

    logger.debug(
        "HandshakeCompletedListenerImpl.getHandshakeCompletedEvent exit: listener{} handshakeCompletedEvent{}",
        traceHashcode(pjp.getTarget()),
        traceHashcode(event));
    return event;
  }

  @Around(
      "execution(public void gov.nist.javax.sip.stack.TLSMessageChannel.setHandshakeCompletedListener(javax.net.ssl.HandshakeCompletedListener)) && args(handshakeCompletedListener)")
  public void setHandshakeCompletedListener(
      ProceedingJoinPoint pjp, HandshakeCompletedListener handshakeCompletedListener)
      throws Throwable {
    logger.info(
        "TLSMessageChannel.setHandshakeCompletedListener channel{} listener{}",
        traceHashcode(pjp.getTarget()),
        traceHashcode(handshakeCompletedListener));
    pjp.proceed();
  }

  private String traceHashcode(Object o) {
    return (o == null) ? "=null" : ".hashcode=" + Integer.toString(o.hashCode());
  }

  private String handshakeCompletedEventToString(HandshakeCompletedEvent event) {
    if (event == null) return "null";
    StringBuilder sb = new StringBuilder();
    sb.append("session=[" + sslSessionToString(event.getSession()) + "]\n");
    sb.append("socket=[" + sslSocketToString(event.getSocket()) + "]\n");
    sb.append("hashCode=" + event.hashCode());
    return sb.toString();
  }

  private String sslSocketToString(SSLSocket socket) {
    if (socket == null) return "null";
    return socket.toString()
        + " "
        + "session=["
        + sslSessionToString(socket.getSession())
        + "] "
        + "needClientAuth="
        + (socket.getNeedClientAuth() ? "true" : "false")
        + " "
        + "useClientMode="
        + (socket.getUseClientMode() ? "true" : "false");
  }

  private String sslSessionToString(SSLSession session) {
    if (session == null) return "null";
    return String.format("peer=[%s:%d]", session.getPeerHost(), session.getPeerPort());
  }

  /**
   * Aspect for logging with information when TCP connection is closing
   *
   * @param jp
   * @throws Throwable
   */
  @After(
      value =
          "execution(public void gov.nist.javax.sip.stack.TCPMessageChannel.close(boolean , boolean))")
  @Order(0)
  public void logWhenTcpChannelClosed(JoinPoint jp) throws Throwable {

    Object[] args = jp.getArgs();
    boolean removeSocket = (boolean) args[0];
    boolean stopKeepAliveTask = (boolean) args[1];

    try {
      TCPMessageChannel tcpMessageChannel = ((TCPMessageChannel) jp.getTarget());
      if (tcpMessageChannel.getMessageProcessor() instanceof DsbSipTCPMessageProcessor) {
        DsbSipTCPMessageProcessor dsbJainSipTCPMessageProcessor =
            (DsbSipTCPMessageProcessor) tcpMessageChannel.getMessageProcessor();

        logger.info(
            "TCPMessageChannel connection closing {}, removeSocket {}, stopKeepAliveTask {}, peerHostPort {}, viaHostPort {}, stackName {}, MessageProcessor IP {} port {}",
            tcpMessageChannel.getKey(),
            removeSocket,
            stopKeepAliveTask,
            tcpMessageChannel.getPeerHostPort().toString(),
            tcpMessageChannel.getViaHostPort(),
            dsbJainSipTCPMessageProcessor.getStackName(),
            dsbJainSipTCPMessageProcessor.getIpAddress(),
            dsbJainSipTCPMessageProcessor.getPort());
      }

      if (tcpMessageChannel.getMessageProcessor() instanceof DsbNioTCPMessageProcessor) {
        DsbNioTCPMessageProcessor dsbNioTCPMessageProcessor =
            (DsbNioTCPMessageProcessor) tcpMessageChannel.getMessageProcessor();

        logger.info(
            "TCPMessageChannel connection closing {}, removeSocket {}, stopKeepAliveTask {}, peerHostPort {}, viaHostPort {}, stackName {}, MessageProcessor IP {} port {}",
            tcpMessageChannel.getKey(),
            removeSocket,
            stopKeepAliveTask,
            tcpMessageChannel.getPeerHostPort().toString(),
            tcpMessageChannel.getViaHostPort(),
            dsbNioTCPMessageProcessor.getStackName(),
            dsbNioTCPMessageProcessor.getIpAddress(),
            dsbNioTCPMessageProcessor.getPort());
      }

    } catch (Exception var) {
      logger.warn("Error while process TCP close Aspect {} ", var);
    }
  }

  /**
   * Aspect for logging with information when TLS connection is closing
   *
   * @param jp
   * @throws Throwable
   */
  @After(
      value =
          "execution(public void gov.nist.javax.sip.stack.TLSMessageChannel.close(boolean , boolean))")
  @Order(0)
  public void logWhenTlsChannelClosed(JoinPoint jp) throws Throwable {

    Object[] args = jp.getArgs();
    boolean removeSocket = (boolean) args[0];
    boolean stopKeepAliveTask = (boolean) args[1];

    try {
      TLSMessageChannel tlsMessageChannel = ((TLSMessageChannel) jp.getTarget());
      if (tlsMessageChannel.getMessageProcessor() instanceof DsbJainSipTLSMessageProcessor) {
        DsbJainSipTLSMessageProcessor dsbJainSipTLSMessageProcessor =
            (DsbJainSipTLSMessageProcessor) tlsMessageChannel.getMessageProcessor();

        logger.info(
            "TLSMessageChannel connection closing {}, removeSocket {}, stopKeepAliveTask {}, peerHostPort {}, viaHostPort {}, stackName {}, MessageProcessor IP {} port {}",
            tlsMessageChannel.getKey(),
            removeSocket,
            stopKeepAliveTask,
            tlsMessageChannel.getPeerHostPort().toString(),
            tlsMessageChannel.getViaHostPort(),
            dsbJainSipTLSMessageProcessor.getStackName(),
            dsbJainSipTLSMessageProcessor.getIpAddress(),
            dsbJainSipTLSMessageProcessor.getPort());
      }

      if (tlsMessageChannel.getMessageProcessor() instanceof DsbNioTlsMessageProcessor) {
        DsbNioTlsMessageProcessor dsbNioTLSMessageProcessor =
            (DsbNioTlsMessageProcessor) tlsMessageChannel.getMessageProcessor();

        logger.info(
            "TLSMessageChannel connection closing {}, removeSocket {}, stopKeepAliveTask {}, peerHostPort {}, viaHostPort {}, stackName {}, MessageProcessor IP {} port {}",
            tlsMessageChannel.getKey(),
            removeSocket,
            stopKeepAliveTask,
            tlsMessageChannel.getPeerHostPort().toString(),
            tlsMessageChannel.getViaHostPort(),
            dsbNioTLSMessageProcessor.getStackName(),
            dsbNioTLSMessageProcessor.getIpAddress(),
            dsbNioTLSMessageProcessor.getPort());
      }

    } catch (Exception var) {
      logger.warn("Error while process TLS close Aspect {} ", var);
    }
  }
}
