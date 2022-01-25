package com.cisco.dsb.common.sip.stack.dto;

import static org.mockito.Mockito.*;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.transport.Transport;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sip.SipProvider;
import org.junit.Assert;
import org.testng.annotations.Test;

public class DhruvaNetworkTest {

  @Test
  public void testDhruvaNetworkCreation() throws DhruvaException {
    SIPListenPoint lp = mock(SIPListenPoint.class);
    when(lp.getName()).thenReturn("lp");
    when(lp.getTransport()).thenReturn(Transport.TCP);

    // create a network where network name and sipListenPoint name matches
    DhruvaNetwork.createNetwork("lp", lp);
    Optional<DhruvaNetwork> createdNetworkOptional =
        DhruvaNetwork.getNetwork("lp"); // created network should be present in networkMap
    Assert.assertTrue(createdNetworkOptional.isPresent());

    DhruvaNetwork createdNetwork = createdNetworkOptional.get();
    Assert.assertEquals(createdNetwork.getListenPoint(), lp);
    Assert.assertEquals(createdNetwork.getName(), "lp");
    Assert.assertEquals(createdNetwork.getTransport(), DhruvaNetwork.getTransport("lp").get());

    DhruvaNetwork.removeNetwork("lp");

    // create a network where network name and sipListenPoint name does not match - throws exception
    try {
      DhruvaNetwork.createNetwork("nonExistingLp", lp);
    } catch (DhruvaException e) {
      Assert.assertFalse(
          DhruvaNetwork.getNetwork("nonExistingLp")
              .isPresent()); // mentioned network does not exist
    }
  }

  @Test
  public void testNetworkToSipProviderMapping() {
    SipProvider sp = mock(SipProvider.class);

    // add a sip provider to the network
    DhruvaNetwork.setSipProvider("sp", sp);
    // verify sipProvider using network name
    Assert.assertEquals(DhruvaNetwork.getProviderFromNetwork("sp").get(), sp);
    // Assert.assertFalse(DhruvaNetwork.getProviderFromNetwork("nonExistingSp").isPresent());

    DhruvaNetwork.setSipProvider(
        "newSp", sp); // two entries for same sp, returns the first hit from map
    // verify network using sipProvider
    Assert.assertEquals(DhruvaNetwork.getNetworkFromProvider(sp).get(), "newSp");

    DhruvaNetwork.removeSipProvider("sp");
    Assert.assertFalse(DhruvaNetwork.getProviderFromNetwork("sp").isPresent());
    DhruvaNetwork.clearSipProviderMap();
    Assert.assertFalse(DhruvaNetwork.getProviderFromNetwork("newSp").isPresent());
  }

  @Test
  public void testDefaultNetwork() {
    CommonConfigurationProperties ccProps = mock(CommonConfigurationProperties.class);
    SIPListenPoint lp = mock(SIPListenPoint.class);
    List<SIPListenPoint> lpList = new ArrayList<>();
    lpList.add(lp);
    when(ccProps.getListenPoints()).thenReturn(lpList);

    DhruvaNetwork.setDhruvaConfigProperties(ccProps);
    Assert.assertEquals(DhruvaNetwork.getDefault().getListenPoint(), lp);
    Assert.assertTrue(DhruvaNetwork.getNetwork(DhruvaNetwork.STR_DEFAULT).isPresent());

    DhruvaNetwork.removeNetwork(DhruvaNetwork.STR_DEFAULT);
    Assert.assertFalse(DhruvaNetwork.getNetwork(DhruvaNetwork.STR_DEFAULT).isPresent());
  }
}
