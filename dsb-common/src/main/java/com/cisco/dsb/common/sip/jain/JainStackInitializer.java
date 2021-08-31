package com.cisco.dsb.common.sip.jain;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.sip.jain.channelCache.DsbJainSipMessageProcessorFactory;
import com.google.common.base.Preconditions;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.stack.NIOMode;
import java.util.*;
import javax.annotation.Nonnull;
import javax.sip.*;
import javax.validation.constraints.PositiveOrZero;

public class JainStackInitializer {

  private JainStackInitializer() {}

  /**
   * Creates a sipstack of specified implementation and properties
   *
   * @param sipFactory created using SipFactory.getInstance()
   * @param path path to the stack implementation
   * @param properties sip stack properties
   * @return SipStack
   * @throws PeerUnavailableException if "javax.sip.STACK_NAME property is missing"
   */
  public static SipStack createSipStack(SipFactory sipFactory, String path, Properties properties)
      throws PeerUnavailableException {
    sipFactory.setPathName(path);
    return sipFactory.createSipStack(properties);
  }

  /**
   * Create 'n' sipstacks of same implementation but same/different properties
   *
   * @param sipFactory created using SipFactory.getInstance()
   * @param path path to the stack implementation
   * @param properties sip stack properties
   * @return list of SipStacks
   * @throws PeerUnavailableException if "javax.sip.STACK_NAME property is missing"
   */
  public static List<SipStack> createSipStacks(
      SipFactory sipFactory, String path, List<Properties> properties)
      throws PeerUnavailableException {
    List<SipStack> sipStacks = new ArrayList<>();
    for (Properties props : properties) {
      SipStack sipStack = createSipStack(sipFactory, path, props);
      sipStacks.add(sipStack);
    }
    return sipStacks;
  }

  /**
   * Create a jain sip listening point for a sipStack
   *
   * @param sipStack already created to which the listen point will be attached
   * @param ip to bbe associated with the listen point
   * @param port listening port
   * @param transport transport on which listen point listens
   * @return ListeningPoint
   * @throws InvalidArgumentException if a listen point cannot be created from the ip, port &
   *     transport provided
   * @throws TransportNotSupportedException if transport is something other than 'udp', 'tls',
   *     'tcp', 'sctp', 'ws' and 'wss'
   */
  public static ListeningPoint createListeningPointForSipStack(
      SipStack sipStack, @Nonnull String ip, @PositiveOrZero int port, @Nonnull String transport)
      throws InvalidArgumentException, TransportNotSupportedException {
    Objects.requireNonNull(ip, "ip address for listen point is not provided");
    Objects.requireNonNull(transport, "transport for listen point is not provided");
    Preconditions.checkArgument(port > 0, "port should be non-zero positive");
    return sipStack.createListeningPoint(ip, port, transport);
  }

  /**
   * Attach the ListeningPoint of a SipStack to a SipProvider i.e 1:1 mapping of
   * ListenPoint:SipProvider
   *
   * @param sipStack already created to which a SipProvider with ListeningPoint will be attached
   * @param listenPoint ListeningPoint for which SipProvider is created
   * @return SipProvider (SP with LP)
   * @throws ObjectInUseException if provided ListeningPoint has a SipProvider already
   */
  public static SipProvider createSipProviderForListenPoint(
      SipStack sipStack, @Nonnull ListeningPoint listenPoint) throws ObjectInUseException {
    Objects.requireNonNull(listenPoint, "listening point should be provided");
    return sipStack.createSipProvider(listenPoint);
  }

  /**
   * Attach a list of ListeningPoints of a SipStack to a list of SipProviders i.e 1:1 mapping of
   * ListenPoint:SipProvider
   *
   * @param sipStack already created to which the list of SipProviders each with a ListeningPoint
   *     will be attached
   * @param lps list of ListeningPoints
   * @return list of SipProviders (eg: SP1 with LP1, SP2 with LP2, etc)
   * @throws ObjectInUseException if provided ListeningPoint has a SipProvider already
   */
  public static List<SipProvider> createSipProvidersForListenPoints(
      SipStack sipStack, List<ListeningPoint> lps) throws ObjectInUseException {
    List<SipProvider> sipProviders = new ArrayList<>();
    for (ListeningPoint lp : lps) {
      SipProvider sipProvider = createSipProviderForListenPoint(sipStack, lp);
      sipProviders.add(sipProvider);
    }
    return sipProviders;
  }

  /**
   * Add multiple ListeningPoints of a SipStack to a single SipProvider i.e n:1 mapping of
   * ListenPoint:SipProvider
   *
   * @param sipProvider already created with at least one ListeningPoint
   * @param lps list os ListeningPoints
   * @throws ObjectInUseException if provided ListeningPoint has a SipProvider already
   * @throws TransportAlreadySupportedException if there is already a ListeningPoint associated to
   *     this SipProvider with the same transport
   */
  public static void addListeningPointsToSipProvider(
      SipProvider sipProvider, List<ListeningPoint> lps)
      throws ObjectInUseException, TransportAlreadySupportedException {
    for (ListeningPoint lp : lps) {
      sipProvider.addListeningPoint(lp);
    }
  }

  /**
   * Attach the given SipListener to 'n' SipProviders i.e n:1 mapping of SipProvider:SipListener
   *
   * @param sipProviders already created list of SipProviders
   * @param sipListener - application view to a SIP stack
   * @throws TooManyListenersException if SipStack already has a SipListener. Only one listener per
   *     stack is allowed (i.e No multiple listeners per stack) Note: However multiple stacks per
   *     listener is possible
   */
  public static void addSipListenerToSipProviders(
      List<SipProvider> sipProviders, SipListener sipListener) throws TooManyListenersException {
    for (SipProvider sipProvider : sipProviders) {
      sipProvider.addSipListener(sipListener);
    }
  }

  /**
   * Creates a simple Jain SIP Stack model which is 1 Stack with 1 ListeningPoint attached to 1
   * SipProvider and with 1 SipListener
   *
   * @param sipFactory created using SipFactory.getInstance()
   * @param path path to the stack implementation
   * @param properties sip stack properties
   * @param ip to bbe associated with the listen point
   * @param port listening port
   * @param transport transport on which listen point listens
   * @param listener - application view to a SIP stack
   * @return SipStack
   * @throws PeerUnavailableException if "javax.sip.STACK_NAME property is missing"
   * @throws TransportNotSupportedException if transport is something other than 'udp', 'tls',
   *     'tcp', 'sctp', 'ws' and 'wss'
   * @throws InvalidArgumentException if a listen point cannot be created from the ip, port &
   *     transport provided
   * @throws ObjectInUseException if provided ListeningPoint has a SipProvider already
   * @throws TooManyListenersException if SipStack already has a SipListener. Only one listener per
   *     stack is allowed (i.e No multiple listeners per stack) Note: However multiple stacks per
   *     listener is possible
   */
  public static SipStack getSimpleStack(
      DhruvaSIPConfigProperties dhruvaSIPConfigProperties,
      SipFactory sipFactory,
      String path,
      Properties properties,
      @Nonnull String ip,
      @PositiveOrZero int port,
      @Nonnull String transport,
      SipListener listener)
      throws PeerUnavailableException, TransportNotSupportedException, InvalidArgumentException,
          ObjectInUseException, TooManyListenersException {
    sipFactory.setPathName(path);
    SipStack sipStack = sipFactory.createSipStack(properties);
    if (sipStack instanceof SipStackImpl) {
      SipStackImpl sipStackImpl = (SipStackImpl) sipStack;
      ((DsbJainSipMessageProcessorFactory) sipStackImpl.messageProcessorFactory)
          .initFromApplication(dhruvaSIPConfigProperties);
      sipStackImpl.nioMode = NIOMode.BLOCKING;
    }
    ListeningPoint lp = createListeningPointForSipStack(sipStack, ip, port, transport);
    SipProvider sipProvider = createSipProviderForListenPoint(sipStack, lp);
    sipProvider.addSipListener(listener);
    return sipStack;
  }
}
