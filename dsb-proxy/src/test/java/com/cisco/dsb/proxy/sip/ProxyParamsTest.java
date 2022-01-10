package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.ProxyConfigurationProperties;
import com.cisco.dsb.proxy.controller.ControllerConfig;
import com.codahale.metrics.MetricRegistry;
import org.springframework.boot.web.reactive.context.StandardReactiveWebEnvironment;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ProxyParamsTest {
  @Test
  void testConstructor() {
    CommonConfigurationProperties props = new CommonConfigurationProperties();
    StandardReactiveWebEnvironment env = new StandardReactiveWebEnvironment();
    SipServerLocatorService sipServerLocatorService =
        new SipServerLocatorService(
            props, new DhruvaExecutorService("Servername", env, new MetricRegistry(), 1, true));

    ProxyParams actualProxyParams =
        new ProxyParams(
            new ProxyParams(
                new ProxyParams(
                    new ProxyParams(
                        new ProxyParams(
                            new ControllerConfig(
                                sipServerLocatorService, new ProxyConfigurationProperties()),
                            "Request Direction"),
                        "Request Direction"),
                    "Request Direction"),
                "Request Direction"),
            "Request Direction");

    Assert.assertTrue(actualProxyParams.doRecordRoute());
    Assert.assertTrue(actualProxyParams.storedIface instanceof ProxyParams);
    Assert.assertNull(actualProxyParams.recordRouteInterface);
    Assert.assertNull(actualProxyParams.reInterface);
    Assert.assertEquals(5060, actualProxyParams.getDefaultPort());
    Assert.assertEquals("Request Direction", actualProxyParams.getRequestDirection());
    Assert.assertEquals(180000L, actualProxyParams.getRequestTimeout());
    Assert.assertNull(actualProxyParams.getDefaultProtocol());
    Assert.assertNull(actualProxyParams.getRecordRouteUserParams());
  }

  @Test
  void testConstructor2() {
    CommonConfigurationProperties props = new CommonConfigurationProperties();
    StandardReactiveWebEnvironment env = new StandardReactiveWebEnvironment();
    SipServerLocatorService sipServerLocatorService =
        new SipServerLocatorService(
            props, new DhruvaExecutorService("Servername", env, new MetricRegistry(), 1, true));

    ProxyParams actualProxyParams =
        new ProxyParams(
            new ControllerConfig(sipServerLocatorService, new ProxyConfigurationProperties()),
            "Request Direction");

    Assert.assertTrue(actualProxyParams.doRecordRoute());
    Assert.assertTrue(actualProxyParams.storedIface instanceof ControllerConfig);
    Assert.assertNull(actualProxyParams.recordRouteInterface);
    Assert.assertNull(actualProxyParams.reInterface);
    Assert.assertEquals(5060, actualProxyParams.getDefaultPort());
    Assert.assertEquals("Request Direction", actualProxyParams.getRequestDirection());
    Assert.assertEquals(180000L, actualProxyParams.getRequestTimeout());
    Assert.assertNull(actualProxyParams.getDefaultProtocol());
    Assert.assertNull(actualProxyParams.getRecordRouteUserParams());
  }

  @Test
  void testConstructor3() {
    CommonConfigurationProperties props = new CommonConfigurationProperties();
    StandardReactiveWebEnvironment env = new StandardReactiveWebEnvironment();
    SipServerLocatorService sipServerLocatorService =
        new SipServerLocatorService(
            props, new DhruvaExecutorService("Servername", env, new MetricRegistry(), 1, true));

    ControllerConfig controllerConfig =
        new ControllerConfig(sipServerLocatorService, new ProxyConfigurationProperties());
    controllerConfig.removeRecordRouteInterface("Direction");
    ProxyParams actualProxyParams =
        new ProxyParams(
            new ProxyParams(
                new ProxyParams(
                    new ProxyParams(
                        new ProxyParams(controllerConfig, "Request Direction"),
                        "Request Direction"),
                    "Request Direction"),
                "Request Direction"),
            "Request Direction");

    Assert.assertFalse(actualProxyParams.doRecordRoute());
    Assert.assertTrue(actualProxyParams.storedIface instanceof ProxyParams);
    Assert.assertNull(actualProxyParams.recordRouteInterface);
    Assert.assertNull(actualProxyParams.reInterface);
    Assert.assertEquals(5060, actualProxyParams.getDefaultPort());
    Assert.assertEquals("Request Direction", actualProxyParams.getRequestDirection());
    Assert.assertEquals(180000L, actualProxyParams.getRequestTimeout());
    Assert.assertNull(actualProxyParams.getDefaultProtocol());
    Assert.assertNull(actualProxyParams.getRecordRouteUserParams());
  }

  @Test
  void testConstructor4() {
    CommonConfigurationProperties props = new CommonConfigurationProperties();
    StandardReactiveWebEnvironment env = new StandardReactiveWebEnvironment();
    SipServerLocatorService sipServerLocatorService =
        new SipServerLocatorService(
            props, new DhruvaExecutorService("Servername", env, new MetricRegistry(), 1, true));

    ProxyParams proxyParams =
        new ProxyParams(
            new ControllerConfig(sipServerLocatorService, new ProxyConfigurationProperties()),
            "Request Direction");
    proxyParams.setProxyToAddress("42 Main St");
    ProxyParams actualProxyParams =
        new ProxyParams(
            new ProxyParams(
                new ProxyParams(
                    new ProxyParams(proxyParams, "Request Direction"), "Request Direction"),
                "Request Direction"),
            "Request Direction");

    Assert.assertTrue(actualProxyParams.doRecordRoute());
    Assert.assertTrue(actualProxyParams.storedIface instanceof ProxyParams);
    Assert.assertNull(actualProxyParams.recordRouteInterface);
    Assert.assertNull(actualProxyParams.reInterface);
    Assert.assertEquals(5060, actualProxyParams.getDefaultPort());
    Assert.assertEquals("42 Main St", actualProxyParams.getProxyToAddress());
    Assert.assertEquals("Request Direction", actualProxyParams.getRequestDirection());
    Assert.assertEquals(180000L, actualProxyParams.getRequestTimeout());
    Assert.assertNull(actualProxyParams.getDefaultProtocol());
    Assert.assertNull(actualProxyParams.getRecordRouteUserParams());
  }

  @Test
  void testConstructor5() {
    CommonConfigurationProperties props = new CommonConfigurationProperties();
    StandardReactiveWebEnvironment env = new StandardReactiveWebEnvironment();
    SipServerLocatorService sipServerLocatorService =
        new SipServerLocatorService(
            props, new DhruvaExecutorService("Servername", env, new MetricRegistry(), 1, true));

    ProxyParams proxyParams =
        new ProxyParams(
            new ControllerConfig(sipServerLocatorService, new ProxyConfigurationProperties()),
            "Request Direction");
    proxyParams.setProxyToPort(8080);
    ProxyParams actualProxyParams =
        new ProxyParams(
            new ProxyParams(
                new ProxyParams(
                    new ProxyParams(proxyParams, "Request Direction"), "Request Direction"),
                "Request Direction"),
            "Request Direction");

    Assert.assertTrue(actualProxyParams.doRecordRoute());
    Assert.assertTrue(actualProxyParams.storedIface instanceof ProxyParams);
    Assert.assertNull(actualProxyParams.recordRouteInterface);
    Assert.assertNull(actualProxyParams.reInterface);
    Assert.assertEquals(5060, actualProxyParams.getDefaultPort());
    Assert.assertEquals("Request Direction", actualProxyParams.getRequestDirection());
    Assert.assertEquals(180000L, actualProxyParams.getRequestTimeout());
    Assert.assertEquals(8080, actualProxyParams.getProxyToPort());
    Assert.assertNull(actualProxyParams.getDefaultProtocol());
    Assert.assertNull(actualProxyParams.getRecordRouteUserParams());
  }

  @Test
  void testConstructor6() {
    CommonConfigurationProperties props = new CommonConfigurationProperties();
    StandardReactiveWebEnvironment env = new StandardReactiveWebEnvironment();
    SipServerLocatorService sipServerLocatorService =
        new SipServerLocatorService(
            props, new DhruvaExecutorService("Servername", env, new MetricRegistry(), 1, true));

    ProxyParams proxyParams =
        new ProxyParams(
            new ControllerConfig(sipServerLocatorService, new ProxyConfigurationProperties()),
            "Request Direction");
    proxyParams.setProxyToProtocol(Transport.NONE);
    ProxyParams actualProxyParams =
        new ProxyParams(
            new ProxyParams(
                new ProxyParams(
                    new ProxyParams(proxyParams, "Request Direction"), "Request Direction"),
                "Request Direction"),
            "Request Direction");

    Assert.assertTrue(actualProxyParams.doRecordRoute());
    Assert.assertTrue(actualProxyParams.storedIface instanceof ProxyParams);
    Assert.assertNull(actualProxyParams.recordRouteInterface);
    Assert.assertNull(actualProxyParams.reInterface);
    Assert.assertEquals(5060, actualProxyParams.getDefaultPort());
    Assert.assertEquals("Request Direction", actualProxyParams.getRequestDirection());
    Assert.assertEquals(180000L, actualProxyParams.getRequestTimeout());
    Assert.assertNull(actualProxyParams.getDefaultProtocol());
    Assert.assertEquals(Transport.NONE, actualProxyParams.getProxyToProtocol());
    Assert.assertNull(actualProxyParams.getRecordRouteUserParams());
  }

  @Test
  void testGetInterface() {
    CommonConfigurationProperties props = new CommonConfigurationProperties();
    StandardReactiveWebEnvironment env = new StandardReactiveWebEnvironment();
    SipServerLocatorService sipServerLocatorService =
        new SipServerLocatorService(
            props, new DhruvaExecutorService("Servername", env, new MetricRegistry(), 1, true));

    Assert.assertNull(
        (new ProxyParams(
                new ProxyParams(
                    new ProxyParams(
                        new ProxyParams(
                            new ControllerConfig(
                                sipServerLocatorService, new ProxyConfigurationProperties()),
                            "Request Direction"),
                        "Request Direction"),
                    "Request Direction"),
                "Request Direction"))
            .getInterface(8080, Transport.NONE));
  }

  @Test
  void testGetInterface2() {
    CommonConfigurationProperties props = new CommonConfigurationProperties();
    StandardReactiveWebEnvironment env = new StandardReactiveWebEnvironment();
    SipServerLocatorService sipServerLocatorService =
        new SipServerLocatorService(
            props, new DhruvaExecutorService("Servername", env, new MetricRegistry(), 1, true));

    ProxyParams proxyParams =
        new ProxyParams(
            new ProxyParams(
                new ProxyParams(
                    new ProxyParams(
                        new ControllerConfig(
                            sipServerLocatorService, new ProxyConfigurationProperties()),
                        "Request Direction"),
                    "Request Direction"),
                "Request Direction"),
            "Request Direction");
    Assert.assertNull(
        proxyParams.getInterface(Transport.NONE, new DhruvaNetwork(new SIPListenPoint())));
  }

  @Test
  void testSetDefaultPort() {
    CommonConfigurationProperties props = new CommonConfigurationProperties();
    StandardReactiveWebEnvironment env = new StandardReactiveWebEnvironment();
    SipServerLocatorService sipServerLocatorService =
        new SipServerLocatorService(
            props, new DhruvaExecutorService("Servername", env, new MetricRegistry(), 1, true));

    ProxyParams proxyParams =
        new ProxyParams(
            new ProxyParams(
                new ProxyParams(
                    new ProxyParams(
                        new ControllerConfig(
                            sipServerLocatorService, new ProxyConfigurationProperties()),
                        "Request Direction"),
                    "Request Direction"),
                "Request Direction"),
            "Request Direction");
    proxyParams.setDefaultPort(8080);
    Assert.assertEquals(8080, proxyParams.getDefaultPort());
  }

  @Test
  void testSetDefaultPort2() {
    CommonConfigurationProperties props = new CommonConfigurationProperties();
    StandardReactiveWebEnvironment env = new StandardReactiveWebEnvironment();
    SipServerLocatorService sipServerLocatorService =
        new SipServerLocatorService(
            props, new DhruvaExecutorService("Servername", env, new MetricRegistry(), 1, true));

    ProxyParams proxyParams =
        new ProxyParams(
            new ProxyParams(
                new ProxyParams(
                    new ProxyParams(
                        new ControllerConfig(
                            sipServerLocatorService, new ProxyConfigurationProperties()),
                        "Request Direction"),
                    "Request Direction"),
                "Request Direction"),
            "Request Direction");
    proxyParams.setDefaultPort(0);
    Assert.assertTrue(proxyParams.doRecordRoute());
    Assert.assertTrue(proxyParams.storedIface instanceof ProxyParams);
    Assert.assertEquals(5060, proxyParams.getDefaultPort());
    Assert.assertEquals("Request Direction", proxyParams.getRequestDirection());
    Assert.assertEquals(180000L, proxyParams.getRequestTimeout());
    Assert.assertEquals(0, proxyParams.getProxyToPort());
    Assert.assertFalse(proxyParams.isLocalProxySet());
  }

  @Test
  void testSetDefaultProtocol() {
    CommonConfigurationProperties props = new CommonConfigurationProperties();
    StandardReactiveWebEnvironment env = new StandardReactiveWebEnvironment();
    SipServerLocatorService sipServerLocatorService =
        new SipServerLocatorService(
            props, new DhruvaExecutorService("Servername", env, new MetricRegistry(), 1, true));

    ProxyParams proxyParams =
        new ProxyParams(
            new ProxyParams(
                new ProxyParams(
                    new ProxyParams(
                        new ControllerConfig(
                            sipServerLocatorService, new ProxyConfigurationProperties()),
                        "Request Direction"),
                    "Request Direction"),
                "Request Direction"),
            "Request Direction");
    proxyParams.setDefaultProtocol(1);
    Assert.assertEquals(Transport.UDP, proxyParams.getDefaultProtocol());
  }

  @Test
  void testSetDefaultProtocol2() {
    CommonConfigurationProperties props = new CommonConfigurationProperties();
    StandardReactiveWebEnvironment env = new StandardReactiveWebEnvironment();
    SipServerLocatorService sipServerLocatorService =
        new SipServerLocatorService(
            props, new DhruvaExecutorService("Servername", env, new MetricRegistry(), 1, true));

    ProxyParams proxyParams =
        new ProxyParams(
            new ProxyParams(
                new ProxyParams(
                    new ProxyParams(
                        new ControllerConfig(
                            sipServerLocatorService, new ProxyConfigurationProperties()),
                        "Request Direction"),
                    "Request Direction"),
                "Request Direction"),
            "Request Direction");
    proxyParams.setDefaultProtocol(2);
    Assert.assertEquals(Transport.TCP, proxyParams.getDefaultProtocol());
  }

  @Test
  void testSetDefaultProtocol3() {
    CommonConfigurationProperties props = new CommonConfigurationProperties();
    StandardReactiveWebEnvironment env = new StandardReactiveWebEnvironment();
    SipServerLocatorService sipServerLocatorService =
        new SipServerLocatorService(
            props, new DhruvaExecutorService("Servername", env, new MetricRegistry(), 1, true));

    ProxyParams proxyParams =
        new ProxyParams(
            new ProxyParams(
                new ProxyParams(
                    new ProxyParams(
                        new ControllerConfig(
                            sipServerLocatorService, new ProxyConfigurationProperties()),
                        "Request Direction"),
                    "Request Direction"),
                "Request Direction"),
            "Request Direction");
    proxyParams.setDefaultProtocol(4);
    Assert.assertEquals(Transport.TLS, proxyParams.getDefaultProtocol());
  }

  @Test
  void testGetViaInterface() {
    CommonConfigurationProperties props = new CommonConfigurationProperties();
    StandardReactiveWebEnvironment env = new StandardReactiveWebEnvironment();
    SipServerLocatorService sipServerLocatorService =
        new SipServerLocatorService(
            props, new DhruvaExecutorService("Servername", env, new MetricRegistry(), 1, true));

    Assert.assertNull(
        (new ProxyParams(
                new ControllerConfig(sipServerLocatorService, new ProxyConfigurationProperties()),
                "Request Direction"))
            .getViaInterface(1, "Direction"));
  }

  @Test
  void testGetViaInterface2() {
    CommonConfigurationProperties props = new CommonConfigurationProperties();
    StandardReactiveWebEnvironment env = new StandardReactiveWebEnvironment();
    SipServerLocatorService sipServerLocatorService =
        new SipServerLocatorService(
            props, new DhruvaExecutorService("Servername", env, new MetricRegistry(), 1, true));

    Assert.assertNull(
        (new ProxyParams(
                new ControllerConfig(sipServerLocatorService, new ProxyConfigurationProperties()),
                "Request Direction"))
            .getViaInterface(Transport.NONE, "Direction"));
  }

  @Test
  void testGetRecordRouteInterface() {
    CommonConfigurationProperties props = new CommonConfigurationProperties();
    StandardReactiveWebEnvironment env = new StandardReactiveWebEnvironment();
    SipServerLocatorService sipServerLocatorService =
        new SipServerLocatorService(
            props, new DhruvaExecutorService("Servername", env, new MetricRegistry(), 1, true));

    ProxyParams proxyParams =
        new ProxyParams(
            new ProxyParams(
                new ProxyParams(
                    new ProxyParams(
                        new ControllerConfig(
                            sipServerLocatorService, new ProxyConfigurationProperties()),
                        "Request Direction"),
                    "Request Direction"),
                "Request Direction"),
            "Request Direction");
    Assert.assertNull(proxyParams.getRecordRouteInterface("Direction"));
    Assert.assertNull(proxyParams.recordRouteInterface);
    Assert.assertNull(((ProxyParams) proxyParams.storedIface).recordRouteInterface);
    Assert.assertNull(
        ((ProxyParams) ((ProxyParams) proxyParams.storedIface).storedIface).recordRouteInterface);
    Assert.assertNull(
        ((ProxyParams)
                ((ProxyParams) ((ProxyParams) proxyParams.storedIface).storedIface).storedIface)
            .recordRouteInterface);
  }
}
