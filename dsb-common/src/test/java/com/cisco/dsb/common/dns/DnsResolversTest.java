package com.cisco.dsb.common.dns;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.isA;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import com.cisco.dsb.common.dns.dto.DNSARecord;
import com.cisco.dsb.common.dns.dto.DNSSRVRecord;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

public class DnsResolversTest {
  private DnsLookup resolver;
  @Rule public ExpectedException thrown = ExpectedException.none();

  LookupFactory lookupFactory;
  Resolver xbillResolver;

  @BeforeTest
  public void setUp() {
    lookupFactory = mock(LookupFactory.class);

    xbillResolver = mock(Resolver.class);

    MockitoAnnotations.initMocks(this);

    resolver = DnsResolvers.newBuilder().lookupFactory(lookupFactory).build();
  }

  @Test
  public void checkResultsValidSrvQuery() throws Exception {
    try {

      String queryFqdn = "thefqdn10.";

      setupResponseForSrvQuery(queryFqdn, queryFqdn, "node1.domain.", "node2.domain.");

      CompletableFuture<List<DNSSRVRecord>> future = resolver.lookupSRV(queryFqdn);
      List<DNSSRVRecord> records = future.get();
      Assert.assertFalse(records.isEmpty());
    } catch (DnsException e) {
      Assert.fail();
    } catch (InterruptedException | ExecutionException e) {
      Assert.fail();
      e.printStackTrace();
    }
  }

  @Test(expectedExceptions = {ExecutionException.class})
  public void checkResultsInValidSrvQuery() throws Exception {
    thrown.expectCause(isA(DnsException.class));

    String fqdn = "thefqdn11.";

    when(lookupFactory.createLookup(fqdn, Type.SRV)).thenReturn(testLookupSrv(fqdn));
    when(xbillResolver.send(any(Message.class))).thenReturn(messageWithRCode(fqdn, Rcode.NXDOMAIN));

    CompletableFuture<List<DNSSRVRecord>> f = resolver.lookupSRV(fqdn);
    List<DNSSRVRecord> dnssrvRecords = f.get();
  }

  @Test(expectedExceptions = {ExecutionException.class})
  public void shouldFailForBadHostNames() throws Exception {
    thrown.expectCause(isA(DnsException.class));

    String fqdn = "thefqdn12.";

    when(lookupFactory.createLookup(fqdn, Type.SRV)).thenReturn(testLookupSrv(fqdn));
    when(xbillResolver.send(any(Message.class))).thenReturn(messageWithRCode(fqdn, Rcode.NXDOMAIN));

    CompletableFuture<List<DNSSRVRecord>> f = resolver.lookupSRV("_sips._spamhost.tcp.com");
    List<DNSSRVRecord> dnssrvRecords = f.get();
  }

  @Test
  public void checkResultsValidAQuery() throws Exception {
    try {
      String host = "thehost.";
      setupResponseForAQuery(host, host, "node1.domain.", "node2.domain.");
      CompletableFuture<List<DNSARecord>> future = resolver.lookupA(host);
      List<DNSARecord> records = future.get();
      Assert.assertFalse(records.isEmpty());
    } catch (DnsException e) {
      Assert.fail();
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void checkResultsInValidAQuery() throws Exception {
    try {

      String host = "thehost13.";
      when(lookupFactory.createLookup(host, Type.A)).thenReturn(testLookupA(host));
      when(xbillResolver.send(any(Message.class)))
          .thenReturn(messageWithRCode(host, Rcode.NXDOMAIN));
      CompletableFuture<List<DNSARecord>> future = resolver.lookupA(host);
      List<DNSARecord> records = future.get();
      Assert.assertFalse(records.isEmpty());
    } catch (DnsException e) {
      Assert.fail();
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void shouldReturnResultsUsingSpecifiedServers() throws Exception {
    final String server = new SimpleResolver().getAddress().getHostName();
    String fqdn = "thewebex14.";
    ;
    final DnsLookup resolver =
        DnsResolvers.newBuilder()
            .lookupFactory(lookupFactory)
            .servers(Arrays.asList(server))
            .build();
    setupResponseForSrvQuery(fqdn, fqdn, "node1.domain.", "node2.domain.");
    CompletableFuture<List<DNSSRVRecord>> f = resolver.lookupSRV(fqdn);
    assertThat(f.get().isEmpty(), is(false));
  }

  private void setupResponseForSrvQuery(String queryFqdn, String responseFqdn, String... results)
      throws IOException {
    when(lookupFactory.createLookup(queryFqdn, Type.SRV)).thenReturn(testLookupSrv(queryFqdn));
    when(xbillResolver.send(any(Message.class)))
        .thenReturn(messageWithNodes(responseFqdn, results));
  }

  private void setupResponseForAQuery(String queryFqdn, String responseFqdn, String... results)
      throws IOException {
    when(lookupFactory.createLookup(queryFqdn, Type.A)).thenReturn(testLookupA(queryFqdn));
    when(xbillResolver.send(any(Message.class)))
        .thenReturn(messageWithANodes(responseFqdn, results));
  }

  private Message messageWithRCode(String query, int rcode) throws TextParseException {
    Name queryName = Name.fromString(query);
    Record question = Record.newRecord(queryName, Type.SRV, DClass.IN);
    Message queryMessage = Message.newQuery(question);
    Message result = new Message();
    result.setHeader(queryMessage.getHeader());
    result.addRecord(question, Section.QUESTION);

    result.getHeader().setRcode(rcode);

    return result;
  }

  private Lookup testLookupSrv(String thefqdn) throws TextParseException {
    Lookup result = new Lookup(thefqdn, Type.SRV);

    result.setResolver(xbillResolver);

    return result;
  }

  private Lookup testLookupA(String thefqdn) throws TextParseException {
    Lookup result = new Lookup(thefqdn, Type.A);

    result.setResolver(xbillResolver);

    return result;
  }

  private Message messageWithNodes(String query, String[] names) throws TextParseException {
    Name queryName = Name.fromString(query);
    Record question = Record.newRecord(queryName, Type.SRV, DClass.IN);
    Message queryMessage = Message.newQuery(question);
    Message result = new Message();
    result.setHeader(queryMessage.getHeader());
    result.addRecord(question, Section.QUESTION);

    for (String name1 : names) {
      result.addRecord(
          new SRVRecord(queryName, DClass.IN, 1, 1, 1, 8080, Name.fromString(name1)),
          Section.ANSWER);
    }

    return result;
  }

  private Message messageWithANodes(String query, String[] names)
      throws TextParseException, UnknownHostException {
    Name queryName = Name.fromString(query);
    Record question = Record.newRecord(queryName, Type.A, DClass.IN);
    Message queryMessage = Message.newQuery(question);
    Message result = new Message();
    result.setHeader(queryMessage.getHeader());
    result.addRecord(question, Section.QUESTION);
    InetAddress addr = InetAddress.getByName("127.0.0.1");
    for (String name1 : names) {
      result.addRecord(new ARecord(queryName, DClass.IN, 1, addr), Section.ANSWER);
    }

    return result;
  }
}
