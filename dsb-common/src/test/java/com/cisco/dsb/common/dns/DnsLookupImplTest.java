package com.cisco.dsb.common.dns;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.AdditionalAnswers.answer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cisco.dsb.common.dns.dto.DNSARecord;
import com.cisco.dsb.common.dns.dto.DNSSRVRecord;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;
import org.xbill.DNS.lookup.LookupSession;

@Test
public class DnsLookupImplTest {
  DnsLookup resolver;

  LookupFactory lookupFactory;
  Resolver xbillResolver;
  Resolver mockResolver;

  public static final Name DUMMY_NAME = Name.fromConstantString("to.be.replaced.");
  private static ARecord LOOPBACK_A =
      new ARecord(DUMMY_NAME, DClass.IN, 3600, InetAddress.getLoopbackAddress());

  @Rule public ExpectedException thrown = ExpectedException.none();

  @BeforeTest
  public void setUp() {
    lookupFactory = mock(LookupFactory.class);

    SrvRecordCache srvCache = new SrvRecordCache(1000, 50000);
    ARecordCache aCache = new ARecordCache(1000, 50000);

    resolver = new DnsLookupImpl(srvCache, aCache, lookupFactory);

    xbillResolver = mock(Resolver.class);
    mockResolver = mock(Resolver.class);
  }

  @AfterTest
  public void tearDown() {
    Lookup.refreshDefault();
  }

  @Test
  public void shouldReturnResultsFromLookup() throws Exception {
    String fqdn = "thefqdn6.";
    String[] resultNodes = new String[] {"node1.domain.", "node2.domain."};

    setupResponseForQuery(fqdn, fqdn, resultNodes);

    CompletableFuture<List<DNSSRVRecord>> f = resolver.lookupSRV(fqdn);
    List<DNSSRVRecord> actual = f.get();

    Set<String> nodeNames =
        actual.stream().map(DNSSRVRecord::getTarget).collect(Collectors.toSet());

    assertThat(nodeNames, containsInAnyOrder(resultNodes));
  }

  @Test
  public void shouldIndicateCauseFromXBillIfLookupFails() throws Exception {
    thrown.expect(DnsException.class);
    thrown.expectMessage("response does not match query");

    String fqdn = "thefqdn5.";
    setupResponseForQuery(fqdn, "somethingelse.", "node1.domain.", "node2.domain.");

    resolver.lookupSRV(fqdn);
  }

  @Test
  public void shouldIndicateNameIfLookupFails() throws Exception {
    thrown.expect(DnsException.class);
    thrown.expectMessage("thefqdn.");

    String fqdn = "thefqdn4.";
    setupResponseForQuery(fqdn, "somethingelse.", "node1.domain.", "node2.domain.");

    resolver.lookupSRV(fqdn);
  }

  @Test(expectedExceptions = {ExecutionException.class})
  public void shouldReturnEmptyForHostNotFound() throws Exception {
    String fqdn = "thefqdn3.";

    when(lookupFactory.createLookup(fqdn, Type.SRV)).thenReturn(testLookup(fqdn));
    when(xbillResolver.send(any(Message.class))).thenReturn(messageWithRCode(fqdn, Rcode.NXDOMAIN));

    CompletableFuture<List<DNSSRVRecord>> f = resolver.lookupSRV(fqdn);
    List<DNSSRVRecord> actual = f.get();
    // assertThat(f.get().isEmpty(), is(true));
  }

  @Test(expectedExceptions = {ExecutionException.class})
  public void shouldReturnEmptyForServerFailure() throws Exception {
    String fqdn = "thefqdn1.";

    when(lookupFactory.createLookup(fqdn, Type.SRV)).thenReturn(testLookup(fqdn));
    when(xbillResolver.send(any(Message.class))).thenReturn(messageWithRCode(fqdn, Rcode.SERVFAIL));

    CompletableFuture<List<DNSSRVRecord>> f = resolver.lookupSRV(fqdn);
    List<DNSSRVRecord> actual = f.get();
    // assertThat(f.get().isEmpty(), is(true));
  }

  @Test(expectedExceptions = {ExecutionException.class})
  public void shouldReturnEmptyForServerError() throws Exception {
    String fqdn = "thefqdn2.";

    when(lookupFactory.createLookup(fqdn, Type.SRV)).thenReturn(testLookup(fqdn));
    when(xbillResolver.send(any(Message.class))).thenReturn(messageWithRCode(fqdn, Rcode.FORMERR));

    CompletableFuture<List<DNSSRVRecord>> f = resolver.lookupSRV(fqdn);
    List<DNSSRVRecord> actual = f.get();
    // assertThat(f.get().isEmpty(), is(true));
  }

  @Test(description = "test aysnc lookup")
  public void testAsyncLookup() throws ExecutionException, InterruptedException {
    String fqdn = "cisco.webex.com";
    wireUpMockResolver(mockResolver, query -> answer(query, name -> LOOPBACK_A));
    Cache mockCache = mock(Cache.class);
    when(mockCache.getDClass()).thenReturn(DClass.IN);

    LookupSession lookupSession =
        LookupSession.builder().resolver(mockResolver).cache(mockCache).build();
    when(lookupFactory.createLookupAsync(fqdn)).thenReturn(lookupSession);

    List<DNSARecord> dnsaRecordList = resolver.lookupAAsync(fqdn);
    Assert.assertNotNull(dnsaRecordList);
    Assert.assertFalse(dnsaRecordList.isEmpty());
    Assert.assertEquals(
        dnsaRecordList.get(0).getAddress(), InetAddress.getLoopbackAddress().getHostAddress());
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

  private void setupResponseForQuery(String queryFqdn, String responseFqdn, String... results)
      throws IOException {
    when(lookupFactory.createLookup(queryFqdn, Type.SRV)).thenReturn(testLookup(queryFqdn));
    when(xbillResolver.send(any(Message.class)))
        .thenReturn(messageWithNodes(responseFqdn, results));
  }

  private Lookup testLookup(String thefqdn) throws TextParseException {
    Lookup result = new Lookup(thefqdn, Type.SRV);

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

  private void wireUpMockResolver(Resolver mockResolver, Function<Message, Message> handler) {
    when(mockResolver.sendAsync(any(Message.class), any(Executor.class)))
        .thenAnswer(
            invocation -> {
              Message query = invocation.getArgument(0);
              return CompletableFuture.completedFuture(handler.apply(query));
            });
  }

  public static Message answer(Message query, Function<Name, Record> recordMaker) {
    Message answer = new Message(query.getHeader().getID());
    answer.addRecord(query.getQuestion(), Section.QUESTION);
    Name questionName = query.getQuestion().getName();
    Record response = recordMaker.apply(questionName);
    if (response == null) {
      answer.getHeader().setRcode(Rcode.NXDOMAIN);
    } else {
      if (DUMMY_NAME.equals(response.getName())) {
        response = response.withName(query.getQuestion().getName());
      }
      answer.addRecord(response, Section.ANSWER);
    }
    return answer;
  }
}
