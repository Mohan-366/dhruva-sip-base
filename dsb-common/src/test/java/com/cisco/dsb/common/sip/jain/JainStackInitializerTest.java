package com.cisco.dsb.common.sip.jain;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.sip.jain.channelCache.DsbJainSipMessageProcessorFactory;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.tls.DsbNetworkLayer;
import com.google.common.collect.Iterators;
import gov.nist.javax.sip.ListeningPointImpl;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.SipStackImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TooManyListenersException;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.sip.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class JainStackInitializerTest {

  @Mock CommonConfigurationProperties commonConfigurationProperties;
  @Mock DhruvaExecutorService executorService;
  @Mock TrustManager trustManager;
  @Mock KeyManager keyManager;
  @Mock DsbJainSipMessageProcessorFactory mockDsbJainSipMessageProcessorFactory;

  @BeforeClass
  public void before() {
    MockitoAnnotations.initMocks(this);
    when(commonConfigurationProperties.getTlsOcspResponseTimeoutInSeconds()).thenReturn(1);
  }

  @Test(
      description =
          "checks creation of stacks, listening points, sip providers, sip listeners  and attaching them with each other")
  public void testCreateStacks()
      throws PeerUnavailableException, TransportNotSupportedException, InvalidArgumentException,
          ObjectInUseException, TransportAlreadySupportedException, TooManyListenersException {
    // Stack creation
    SipFactory sipFactory = SipFactory.getInstance();
    String stackName1 = "Test-1";
    Properties prop1 = new Properties();
    prop1.setProperty("javax.sip.STACK_NAME", stackName1);
    List<Properties> props = new ArrayList<>();
    props.add(prop1);

    List<SipStack> stacks = JainStackInitializer.createSipStacks(sipFactory, "gov.nist", props);
    Assert.assertEquals(stacks.size(), 1);
    Assert.assertEquals(stacks.get(0).getStackName(), stackName1);

    // LPs creation for a stack
    // (sipStack1 has listenPoints -> lp1, lp2)
    SipStack sipStack1 = stacks.get(0);

    ListeningPoint lp1 =
        JainStackInitializer.createListeningPointForSipStack(sipStack1, "127.0.0.1", 5061, "tcp");
    Assert.assertEquals(lp1.getIPAddress(), "127.0.0.1");
    Assert.assertEquals(lp1.getPort(), 5061);
    Assert.assertEquals(lp1.getTransport(), "tcp");

    ListeningPoint lp2 =
        JainStackInitializer.createListeningPointForSipStack(sipStack1, "127.0.0.1", 5062, "udp");
    Assert.assertEquals(lp2.getIPAddress(), "127.0.0.1");
    Assert.assertEquals(lp2.getPort(), 5062);
    Assert.assertEquals(lp2.getTransport(), "udp");
    // verifies if LPs attached to the stack
    Assert.assertEquals(Iterators.size(sipStack1.getListeningPoints()), 2);

    // SP creation for a stack
    // (sp1 attached to lp1)
    List<ListeningPoint> oneLpList = new ArrayList<>();
    oneLpList.add(lp1);

    List<SipProvider> oneSpList =
        JainStackInitializer.createSipProvidersForListenPoints(
            sipStack1, oneLpList); // add LP to a SP during creation
    Assert.assertEquals(oneSpList.size(), 1);
    // verifies if SP is attached to the LP and stack
    SipProvider sp1 = oneSpList.get(0);
    Assert.assertEquals(sp1.getListeningPoints().length, 1);
    Assert.assertEquals(sp1.getListeningPoint("tcp"), lp1);
    Assert.assertEquals(sp1.getSipStack(), sipStack1);
    Assert.assertEquals(((ListeningPointImpl) lp1).getProvider(), sp1);
    Assert.assertEquals(Iterators.size(sipStack1.getSipProviders()), 1);

    // add LPs to an already created SP - lp2 now being added to sp1
    // (sp1 attached to lp1, lp2)
    oneLpList.remove(lp1);
    oneLpList.add(lp2);

    JainStackInitializer.addListeningPointsToSipProvider(sp1, oneLpList);
    // verifies if SP is attached to the LPs
    Assert.assertEquals(sp1.getListeningPoints().length, 2);
    Assert.assertEquals(sp1.getListeningPoint("udp"), lp2);
    Assert.assertEquals(((ListeningPointImpl) lp2).getProvider(), sp1);

    // add listener to SPs
    // (mockListener -> sp1 (lp1,lp2) -> sipStack1)
    SipListener mockListener = mock(SipListener.class);

    JainStackInitializer.addSipListenerToSipProviders(oneSpList, mockListener);
    // verifies if listener is attached to SP
    Assert.assertEquals(((SipStackImpl) sipStack1).getSipListener(), mockListener);
    Assert.assertEquals(((SipProviderImpl) sp1).getSipListener(), mockListener);
  }

  @Test(description = "simple stack creation (1 Stack, 1 LP, 1 SP, 1 Listener)")
  public void testSimpleStackCreation() throws Exception {

    SipFactory mockFactory = mock(SipFactory.class);
    Properties mockProps = mock(Properties.class);
    SipStackImpl mockStack = mock(SipStackImpl.class);
    ListeningPoint mockLp = mock(ListeningPoint.class);
    SipProvider mockSp = mock(SipProvider.class);
    SipListener mockListener = mock(SipListener.class);
    DhruvaNetwork.setDhruvaConfigProperties(commonConfigurationProperties);
    DsbNetworkLayer networkLayer = new DsbNetworkLayer();
    networkLayer.init(trustManager, keyManager);
    when(mockFactory.createSipStack(mockProps)).thenReturn(mockStack);
    when(mockStack.createListeningPoint(anyString(), anyInt(), anyString())).thenReturn(mockLp);
    when(mockStack.createSipProvider(mockLp)).thenReturn(mockSp);
    when(mockStack.getNetworkLayer()).thenReturn(networkLayer);

    SipStack simpleStack =
        JainStackInitializer.getSimpleStack(
            commonConfigurationProperties,
            mockFactory,
            "gov.nist",
            mockProps,
            "1.1.1.1",
            5060,
            "tcp",
            mockListener,
            executorService,
            trustManager,
            keyManager,
            null);
    Assert.assertEquals(simpleStack, mockStack);
  }
}
