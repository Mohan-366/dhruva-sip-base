package com.cisco.dsb.sip.stack.dns;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cisco.dsb.common.dns.*;
import com.cisco.dsb.common.dns.dto.DNSARecord;
import com.cisco.dsb.common.dns.dto.DNSSRVRecord;
import com.cisco.dsb.sip.dto.DNSInjectAction;
import com.cisco.dsb.sip.dto.Hop;
import com.cisco.dsb.sip.dto.InjectedDNSARecord;
import com.cisco.dsb.sip.dto.InjectedDNSSRVRecord;
import com.cisco.dsb.sip.enums.DNSRecordSource;
import com.cisco.dsb.sip.enums.LocateSIPServerTransportType;
import com.cisco.dsb.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import com.cisco.wx2.util.JsonUtil;
import com.cisco.wx2.util.Token;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class SipServerLocatorTest {
  protected static final Logger logger = DhruvaLoggerFactory.getLogger(SipServerLocatorTest.class);

  // Configure immediate eviction of {SRV, A} records in cache. Per Guava's CacheBuilder
  // documentation, if maximum
  // size of cache is 0 then elements will be evicted immediately after being loaded into the cache.
  private ARecordCache aCache = new ARecordCache(0, 500);
  private SrvRecordCache srvCache = new SrvRecordCache(0, 500);
  DnsInjectionService dnsInjectionService = DnsInjectionService.memoryBackedCache();

  @Test
  public void testAugmentRealDNSRecords(Method m) throws ExecutionException, InterruptedException {
    MockDnsLookup mockDnsLookup = new MockDnsLookup();
    SipServerLocator locator = new SipServerLocator(dnsInjectionService, mockDnsLookup);

    logger.info("testAugmentRealDNSRecords");

    // Inject a new SRV record that will fall at the top of the list of SRV rows for dhruva.com.
    // Also insert an A record that goes with it.
    // Make sure we get the corresponding IP address back at the top of the hop list.
    // Then remove the injected entries and verify all is back to normal.

    final String name = "DHRUVA.com"; // Caps to check case-insensitive matching on domain names
    final LocateSIPServerTransportType eTransport = LocateSIPServerTransportType.TLS;
    final String injectSRVName = "_sips._tcp." + name + Token.Dot;
    final int injectPort5065 = 5065;
    final String userIdInject = UUID.randomUUID().toString().toLowerCase();
    final String injectHostname = m.getName() + Token.Dot + "bogus.com";
    final String injectDnsHostname = injectHostname + Token.Dot; // join operator?
    final String injectAddress = "1.2.3.4";

    // These are in public DNS for dhruva.com.
    // _sips._tcp.dhruva.com.	1800	IN	SRV	10 10 25061 dhruva-dhruvaintb-02.ciscospark.com.
    // _sips._tcp.dhruva.com.	1800	IN	SRV	10 10 25061 dhruva-dhruvaintb-01.ciscospark.com.
    // dhruva-dhruvaintb-01.ciscospark.com. 5 IN	A	54.165.47.221
    // dhruva-dhruvaintb-02.ciscospark.com. 5 IN	A	54.165.47.221
    final String host1 = "dhruva-dhruvaintb-01.ciscospark.com";
    final String host2 = "dhruva-dhruvaintb-02.ciscospark.com";
    int port = 25061;
    final String hostIP = "54.165.47.221";

    mockDnsLookup.addSRVRecord(
        "_sips._tcp.dhruva.com",
        new DNSSRVRecord("_sips._tcp.dhruva.com", 1800L, 1000, 10, 25061, host1 + "."));
    mockDnsLookup.addSRVRecord(
        "_sips._tcp.dhruva.com",
        new DNSSRVRecord("_sips._tcp.dhruva.com", 1800L, 1000, 10, 25061, host2 + "."));
    mockDnsLookup.addARecord(
        host1 + ".", new DNSARecord("dhruva-dhruvaintb-01.ciscospark.com.", 5, hostIP));
    mockDnsLookup.addARecord(
        host2 + ".", new DNSARecord("dhruva-dhruvaintb-02.ciscospark.com.", 5, hostIP));

    Set<Hop> realHops =
        new HashSet<>(
            Arrays.asList(
                new Hop(host1, hostIP, Transport.TLS, port, 1000, DNSRecordSource.DNS),
                new Hop(host2, hostIP, Transport.TLS, port, 1000, DNSRecordSource.DNS)));
    try {
      // priority=1 specified here should cause this hostname to fall first in the hop list.
      dnsInjectionService.injectSRV(
          userIdInject,
          Arrays.asList(
              new InjectedDNSSRVRecord(
                  injectSRVName,
                  3601,
                  100,
                  1000,
                  injectPort5065,
                  injectDnsHostname,
                  DNSInjectAction.PREPEND)));
      dnsInjectionService.injectA(
          userIdInject,
          Arrays.asList(
              new InjectedDNSARecord(
                  injectDnsHostname, 3600, injectAddress, DNSInjectAction.REPLACE)));
      LocateSIPServersResponse response =
          locator.resolve(name.toLowerCase(), eTransport, null, userIdInject);
      logger.info("response = \n{}\n", JsonUtil.toJsonPretty(response));
      Assert.assertEquals(response.getHops().size(), 3);
      Assert.assertEquals(response.getDnsSRVRecords().size(), 3);
      Assert.assertEquals(response.getDnsARecords().size(), 3);
      Assert.assertEquals(
          response.getHops().get(0),
          new Hop(
              injectHostname,
              injectAddress,
              Transport.TLS,
              injectPort5065,
              100,
              DNSRecordSource.INJECTED));
      Assert.assertTrue(realHops.contains(response.getHops().get(1)));
      Assert.assertTrue(realHops.contains(response.getHops().get(2)));
      dnsInjectionService.clear(userIdInject);
      response = locator.resolve(name.toLowerCase(), eTransport, null, userIdInject);
      logger.info("response = \n{}\n", JsonUtil.toJsonPretty(response));
      Assert.assertEquals(response.getHops().size(), 2);
      Assert.assertEquals(response.getDnsSRVRecords().size(), 2);
      Assert.assertEquals(response.getDnsARecords().size(), 2);
    } catch (Throwable exception) {
      logger.error("An exception occurred in " + m.getName() + ": exception=", exception);
      throw exception;
    } finally {
      try {
        dnsInjectionService.clear(userIdInject);
      } catch (Throwable finallyException) {
        logger.error(
            "Exception trapped in finally block " + m.getName() + ": exception=", finallyException);
      }
    }
  }

  public void testSipServerLocatorLookupTypes(Method m) throws Exception {

    DnsLookup dnsLookup;
    dnsLookup = mock(DnsLookupImpl.class);
    CompletableFuture<List<DNSSRVRecord>> srvFuture = new CompletableFuture<>();
    srvFuture.complete(Collections.emptyList());
    when(dnsLookup.lookupSRV(anyString())).thenReturn(srvFuture);
    CompletableFuture<List<DNSARecord>> aFuture = new CompletableFuture<>();
    when(dnsLookup.lookupA(anyString())).thenReturn(aFuture);
    SipServerLocator locator = new SipServerLocator(dnsInjectionService, dnsLookup);

    logger.info("testSipServerLocatorLookupTypes");

    // Verify paths through the locator logic depending on type of search and whether SRV or A
    // records are found.
    // incorporate userIdInject into names makes easier to grep traces
    final String userIdInject = UUID.randomUUID().toString().toLowerCase();
    final String name =
        "bogus-"
            + UUID.randomUUID().toString()
            + "-dhruva-test.com"; // domain name for both sip and sips.
    final String nameTCP = "tcp." + name; // domain name for just sip
    // These two allow srv lookup on name for both sip and sips
    final String injectSRVTLSName = "_sips._tcp." + name + ".";
    final String injectSRVTCPName = "_sip._tcp." + name + ".";
    // This allows srv lookup of nameTCP only for sip, not sips
    final String injectSRVTCPNameOnly = "_sip._tcp." + nameTCP + ".";
    final String srvHostname = "fromsrv." + name; // Hostname to which the SRV should resolve.
    final String injectSRVTarget =
        srvHostname + "."; // Same as above with period, formatted for SRV record
    final String injectSRVTargetAddress = "1.2.3.4"; // Address to which the name above will resolve
    final String injectAddress =
        "2.3.4.5"; // Address to which "name" will resolve if looked up directly as host.

    try {
      // If resolver looks up name in SRV, these are the SRV and A records it will traverse.
      // If no SRV is matched, but name is looked up as hostname, this is the record we should find.
      // The address is different from above, which should allow us to deduce which path was
      // followed.
      dnsInjectionService.injectSRV(
          userIdInject,
          Arrays.asList(
              new InjectedDNSSRVRecord(
                  injectSRVTLSName,
                  3601,
                  100,
                  1000,
                  5065,
                  injectSRVTarget,
                  DNSInjectAction.REPLACE),
              new InjectedDNSSRVRecord(
                  injectSRVTCPName,
                  3601,
                  100,
                  1000,
                  5060,
                  injectSRVTarget,
                  DNSInjectAction.REPLACE),
              new InjectedDNSSRVRecord(
                  injectSRVTCPNameOnly,
                  3601,
                  100,
                  1000,
                  5060,
                  injectSRVTarget,
                  DNSInjectAction.REPLACE)));
      dnsInjectionService.injectA(
          userIdInject,
          Arrays.asList(
              new InjectedDNSARecord(
                  injectSRVTarget, 3600, injectSRVTargetAddress, DNSInjectAction.REPLACE),
              new InjectedDNSARecord(name + ".", 3600, injectAddress, DNSInjectAction.REPLACE)));

      // Test resolving as concrete transport, TLS or TCP.
      {
        logger.info(
            "test: transport=TLS, no port specified -> RFC 3263 logic: try SRV, if no hits resort to hostname.");
        LocateSIPServersResponse response =
            locator.resolve(name, LocateSIPServerTransportType.TLS, null, userIdInject);
        logger.info("response = \n{}\n", JsonUtil.toJsonPretty(response));
        Assert.assertEquals(response.getHops().size(), 1, "number of hops mismatch");
        Assert.assertEquals(
            response.getHops().get(0),
            new Hop(
                srvHostname,
                injectSRVTargetAddress,
                Transport.TLS,
                5065,
                100,
                DNSRecordSource.INJECTED));
      }
      {
        // Same as above, just query for TCP
        logger.info(
            "test: transport=TCP, no port specified -> RFC 3263 logic: try SRV, if no hits resort to hostname.");
        LocateSIPServersResponse response =
            locator.resolve(name, LocateSIPServerTransportType.TCP, null, userIdInject);
        logger.info("response = \n{}\n", JsonUtil.toJsonPretty(response));
        Assert.assertEquals(response.getHops().size(), 1, "number of hops mismatch");
        Assert.assertEquals(
            response.getHops().get(0),
            new Hop(
                srvHostname,
                injectSRVTargetAddress,
                Transport.TCP,
                5060,
                100,
                DNSRecordSource.INJECTED));
      }
      {
        // If we request a ANY lookup on the same hostname that is referenced by the SRV record,
        // the SRV lookup should fail and it should fallback to trying hostname, so we'll still
        // succeed.
        // Since we pass port=null, it should calculate the default port for the protocol
        // (TLS->5061)
        logger.info("test: ANY lookup where SRV fails but hostname lookup succeeds");
        LocateSIPServersResponse response =
            locator.resolve(srvHostname, LocateSIPServerTransportType.TLS, null, userIdInject);
        logger.info("response = \n{}\n", JsonUtil.toJsonPretty(response));
        Assert.assertEquals(response.getHops().size(), 1, "number of hops mismatch");
        Assert.assertEquals(
            response.getHops().get(0),
            new Hop(
                srvHostname,
                injectSRVTargetAddress,
                Transport.TLS,
                5061,
                0,
                DNSRecordSource.INJECTED));
      }

      // Test resolving TLS_AND_TCP
      {
        logger.info("test: transportLookupType=TLS_AND_TCP pass SRV domain name, hit on TLS SRV");
        // This should hit on the SRV TLS lookup and return those hops.
        LocateSIPServersResponse response =
            locator.resolve(name, LocateSIPServerTransportType.TLS_AND_TCP, null, userIdInject);
        logger.info("response = \n{}\n", JsonUtil.toJsonPretty(response));
        Assert.assertEquals(response.getHops().size(), 1, "number of hops mismatch");
        Assert.assertEquals(
            response.getHops().get(0),
            new Hop(
                srvHostname,
                injectSRVTargetAddress,
                Transport.TLS,
                5065,
                100,
                DNSRecordSource.INJECTED));
      }
      {
        logger.info(
            "test: transportLookupType=TLS_AND_TCP pass TCP-only SRV domain name, hit on TCP SRV");
        // This should fail the SRV TLS lookup, then hit on the SRV TCP lookup and return those
        // hops.
        LocateSIPServersResponse response =
            locator.resolve(nameTCP, LocateSIPServerTransportType.TLS_AND_TCP, null, userIdInject);
        logger.info("response = \n{}\n", JsonUtil.toJsonPretty(response));
        Assert.assertEquals(response.getHops().size(), 1, "number of hops mismatch");
        Assert.assertEquals(
            response.getHops().get(0),
            new Hop(
                srvHostname,
                injectSRVTargetAddress,
                Transport.TCP,
                5060,
                100,
                DNSRecordSource.INJECTED));
      }
      {
        logger.info(
            "test: transportLookupType=TLS_AND_TCP pass hostname referenced by the SRV record");
        // This should fail the SRV TLS lookup, fail the SRV TCP lookup, hit on the hostname lookup
        // and return the hops as TCP.
        LocateSIPServersResponse response =
            locator.resolve(
                srvHostname, LocateSIPServerTransportType.TLS_AND_TCP, null, userIdInject);
        logger.info("response = \n{}\n", JsonUtil.toJsonPretty(response));
        Assert.assertEquals(response.getHops().size(), 1, "number of hops mismatch");
        Assert.assertEquals(
            response.getHops().get(0),
            new Hop(
                srvHostname,
                injectSRVTargetAddress,
                Transport.TLS,
                5061,
                0,
                DNSRecordSource.INJECTED));
      }

      {
        logger.info(
            "test: transportLookupType=TLS_AND_TCP pass SRV domain name and TLS port, hit injectAddress");
        // This should attempt only hostname lookup since port is specified.
        // Should hit on the the hostname record created for this scenario.
        // Because port is NOT 5060, returns hops as TLS
        LocateSIPServersResponse response =
            locator.resolve(name, LocateSIPServerTransportType.TLS_AND_TCP, 5075, userIdInject);
        logger.info("response = \n{}\n", JsonUtil.toJsonPretty(response));
        Assert.assertEquals(response.getHops().size(), 1, "number of hops mismatch");
        Assert.assertEquals(
            response.getHops().get(0),
            new Hop(name, injectAddress, Transport.TLS, 5075, 0, DNSRecordSource.INJECTED));
      }
      {
        logger.info(
            "test: transportLookupType=TLS_AND_TCP pass SRV domain name and TCP port, hit injectAddress");
        // Same as test above except we pass port=5060.
        // This should attempt only hostname lookup since port is specified.
        // Should hit on the the hostname record created for this scenario.
        // Because port is 5060, returns hops as TCP
        LocateSIPServersResponse response =
            locator.resolve(name, LocateSIPServerTransportType.TLS_AND_TCP, 5060, userIdInject);
        logger.info("response = \n{}\n", JsonUtil.toJsonPretty(response));
        Assert.assertEquals(response.getHops().size(), 1, "number of hops mismatch");
        Assert.assertEquals(
            response.getHops().get(0),
            new Hop(name, injectAddress, Transport.TCP, 5060, 0, DNSRecordSource.INJECTED));
      }

      // Test name is IPv4 address
      {
        logger.info(
            "test: name is IP, transport=TCP, no port specified, derive default port from transport.");
        LocateSIPServersResponse response =
            locator.resolve("10.9.8.7", LocateSIPServerTransportType.TCP, null, userIdInject);
        logger.info("response = \n{}\n", JsonUtil.toJsonPretty(response));
        Assert.assertEquals(response.getHops().size(), 1, "number of hops mismatch");
        Assert.assertEquals(
            response.getHops().get(0),
            new Hop(null, "10.9.8.7", Transport.TCP, 5060, 0, DNSRecordSource.DNS));
      }
      {
        logger.info(
            "test: name is IP, transport=TLS, no port specified, derive default port from transport.");
        LocateSIPServersResponse response =
            locator.resolve("10.9.8.7", LocateSIPServerTransportType.TLS, null, userIdInject);
        logger.info("response = \n{}\n", JsonUtil.toJsonPretty(response));
        Assert.assertEquals(response.getHops().size(), 1, "number of hops mismatch");
        Assert.assertEquals(
            response.getHops().get(0),
            new Hop(null, "10.9.8.7", Transport.TLS, 5061, 0, DNSRecordSource.DNS));
      }
      {
        logger.info("test: name is IP, transport=TLS_AND_TCP, no port specified, assumes TCP.");
        LocateSIPServersResponse response =
            locator.resolve(
                "10.9.8.7", LocateSIPServerTransportType.TLS_AND_TCP, null, userIdInject);
        logger.info("response = \n{}\n", JsonUtil.toJsonPretty(response));
        Assert.assertEquals(response.getHops().size(), 1, "number of hops mismatch");
        Assert.assertEquals(
            response.getHops().get(0),
            new Hop(null, "10.9.8.7", Transport.TCP, 5060, 0, DNSRecordSource.DNS));
      }
      {
        logger.info("test: name is IP, transport=TLS, port, get back exactly what was passed.");
        LocateSIPServersResponse response =
            locator.resolve(
                "10.9.8.7",
                LocateSIPServerTransportType.TLS,
                5060, // standard TCP port, not TLS, passed thru
                userIdInject);
        logger.info("response = \n{}\n", JsonUtil.toJsonPretty(response));
        Assert.assertEquals(response.getHops().size(), 1, "number of hops mismatch");
        Assert.assertEquals(
            response.getHops().get(0),
            new Hop(null, "10.9.8.7", Transport.TLS, 5060, 0, DNSRecordSource.DNS));
      }
      {
        logger.info("test: name is IP, transport=TLS_AND_TCP, port=5060, infer transport=TCP.");
        LocateSIPServersResponse response =
            locator.resolve(
                "10.9.8.7", LocateSIPServerTransportType.TLS_AND_TCP, 5060, userIdInject);
        logger.info("response = \n{}\n", JsonUtil.toJsonPretty(response));
        Assert.assertEquals(response.getHops().size(), 1, "number of hops mismatch");
        Assert.assertEquals(
            response.getHops().get(0),
            new Hop(null, "10.9.8.7", Transport.TCP, 5060, 0, DNSRecordSource.DNS));
      }
      {
        logger.info("test: name is IP, transport=TLS_AND_TCP, port=5061, infer transport=TLS.");
        LocateSIPServersResponse response =
            locator.resolve(
                "10.9.8.7", LocateSIPServerTransportType.TLS_AND_TCP, 5061, userIdInject);
        logger.info("response = \n{}\n", JsonUtil.toJsonPretty(response));
        Assert.assertEquals(response.getHops().size(), 1, "number of hops mismatch");
        Assert.assertEquals(
            response.getHops().get(0),
            new Hop(null, "10.9.8.7", Transport.TLS, 5061, 0, DNSRecordSource.DNS));
      }
      {
        logger.info(
            "test: name is IP, transport=TLS_AND_TCP, port=5062 (non-standard), infer transport=TLS");
        LocateSIPServersResponse response =
            locator.resolve(
                "10.9.8.7", LocateSIPServerTransportType.TLS_AND_TCP, 5062, userIdInject);
        logger.info("response = \n{}\n", JsonUtil.toJsonPretty(response));
        Assert.assertEquals(response.getHops().size(), 1, "number of hops mismatch");
        Assert.assertEquals(
            response.getHops().get(0),
            new Hop(null, "10.9.8.7", Transport.TLS, 5062, 0, DNSRecordSource.DNS));
      }

    } catch (Throwable exception) {
      logger.error("An exception occurred in " + m.getName() + ": exception=", exception);
      throw exception;
    } finally {
      try {
        dnsInjectionService.clear(userIdInject);
      } catch (Throwable finallyException) {
        logger.error(
            "Exception trapped in finally block " + m.getName() + ": exception=", finallyException);
      }
    }
  }

  private static class MockDnsLookup implements DnsLookup {
    Map<String, List<DNSSRVRecord>> srvRecords = new HashMap<>();
    Map<String, List<DNSARecord>> aRecords = new HashMap<>();

    public void addSRVRecord(String lookup, DNSSRVRecord record) {
      List<DNSSRVRecord> records = srvRecords.getOrDefault(lookup, new ArrayList<>());
      records.add(record);
      srvRecords.put(lookup, records);
    }

    public void addARecord(String lookup, DNSARecord record) {
      List<DNSARecord> records = aRecords.getOrDefault(lookup, new ArrayList<>());
      records.add(record);
      aRecords.put(lookup, records);
    }

    @Override
    public CompletableFuture<List<DNSSRVRecord>> lookupSRV(String lookup) {
      CompletableFuture<List<DNSSRVRecord>> records = new CompletableFuture<>();

      records.complete(srvRecords.getOrDefault(lookup, new ArrayList<>()));
      return records;
    }

    @Override
    public CompletableFuture<List<DNSARecord>> lookupA(String lookup) {
      CompletableFuture<List<DNSARecord>> records = new CompletableFuture<>();
      records.complete(aRecords.getOrDefault(lookup, new ArrayList<>()));
      return records;
    }
  }
}
