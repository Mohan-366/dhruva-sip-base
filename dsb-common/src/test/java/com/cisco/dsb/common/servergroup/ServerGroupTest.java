package com.cisco.dsb.common.servergroup;

import com.cisco.dsb.common.transport.Transport;
import java.util.ArrayList;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ServerGroupTest {

  @Test
  public void testEqualsSGPolicy() {

    SGPolicy policy = new SGPolicy();
    OptionsPingPolicy optionsPingPolicy = new OptionsPingPolicy();
    optionsPingPolicy.setName("policyForCalling");
    Assert.assertFalse(policy.equals(optionsPingPolicy));
  }

  @Test
  public void testEqualsSG() {
    ServerGroup sg = new ServerGroup();

    sg.setHostName("antheres");
    sg.setSgPolicy("mediaPolicy");
    sg.setOptionsPingPolicy("UDPPolicy");
    ServerGroup sg1 = new ServerGroup();
    sg1.setHostName("antheres");
    sg1.setSgPolicy("mediaPolicy");
    sg1.setOptionsPingPolicy("UDPPolicy");

    Assert.assertTrue(sg.equals(sg1));

    ServerGroupElement sge1 = new ServerGroupElement();
    Assert.assertFalse(sg.equals(sge1));
    Assert.assertNull(sg.getElements());
  }

  @Test
  public void testEqualsSGE() {
    ServerGroupElement sge = new ServerGroupElement();
    sge.setIpAddress("127.0.0.1");
    sge.setPort(5060);
    sge.setPriority(10);
    sge.setTransport(Transport.TLS);
    sge.setWeight(90);

    ServerGroupElement sge1 = new ServerGroupElement();
    sge1.setIpAddress("127.0.0.1");
    sge1.setPort(5060);
    sge1.setPriority(10);
    sge1.setTransport(Transport.TLS);
    sge1.setWeight(90);

    Assert.assertTrue(sge.equals(sge1));
    ServerGroup sg = new ServerGroup();
    Assert.assertFalse(sge.equals(sg));
  }

  @Test
  public void testDomainName() {

    ServerGroupElement sge = new ServerGroupElement();

    Assert.assertTrue(sge.compareDomainNames("alpha.webex.com", "go.webex.com") < 0);
    Assert.assertTrue(sge.compareDomainNames("beech.com", "go.webex.com") < 0);
    Assert.assertTrue(sge.compareDomainNames("127.0.0.2", "127.0.0.1") > 0);
  }

  @Test
  public void testEqualsOptionsPing() {
    OptionsPingPolicy op = new OptionsPingPolicy();
    op.setName("opPing1");
    op.setUpTimeInterval(5000);
    op.setDownTimeInterval(50000);
    ArrayList<Integer> failOverCodes = new ArrayList<>();
    failOverCodes.add(503);
    op.setFailoverResponseCodes(failOverCodes);
    op.setPingTimeOut(500);
    ServerGroupElement sge = new ServerGroupElement();
    Assert.assertFalse(op.equals(sge));
  }
}
