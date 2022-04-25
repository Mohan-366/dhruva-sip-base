package com.cisco.dsb.common.dns;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cisco.dsb.common.service.MetricService;
import java.io.IOException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

public class DnsMetricsReporterTest {

  private DnsLookup resolver;

  @Mock MetricService metricService;
  @InjectMocks DnsMetricsReporter dnsMetricsReporter;

  LookupFactory lookupFactory;
  Resolver xbillResolver;

  @BeforeMethod
  public void setUp() {
    lookupFactory = mock(LookupFactory.class);
    xbillResolver = mock(Resolver.class);
    MockitoAnnotations.initMocks(this);

    resolver =
        DnsResolvers.newBuilder().lookupFactory(lookupFactory).metered(dnsMetricsReporter).build();
  }

  @Test
  public void testSrvQueryWithMetrics() throws Exception {
    String query = "thewebex101.";
    doNothing().when(metricService).sendDNSMetric(anyString(), anyString(), anyLong(), anyString());
    setupResponseForSrvQuery(query, query, "node1.domain.", "node2.domain.");

    resolver.lookupSRV(query); // RCode = 0 (successful)
    ArgumentCaptor<String> argumentCaptor1 = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> argumentCaptor2 = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Long> argumentCaptor3 = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<String> argumentCaptor4 = ArgumentCaptor.forClass(String.class);
    verify(metricService)
        .sendDNSMetric(
            argumentCaptor1.capture(),
            argumentCaptor2.capture(),
            argumentCaptor3.capture(),
            argumentCaptor4.capture());

    String actualQuery = argumentCaptor1.getValue();
    Assert.assertEquals(query, actualQuery);

    String actualQueryType = argumentCaptor2.getValue();
    Assert.assertEquals("SRV", actualQueryType);

    Long processingDelay = argumentCaptor3.getValue();
    Assert.assertTrue(processingDelay >= 0L);

    Assert.assertNull(argumentCaptor4.getValue());
  }

  @Test
  public void testSrvLookupThrowsDnsExceptionWithMetrics() {
    String query = "thefqdn1.";
    doNothing().when(metricService).sendDNSMetric(anyString(), anyString(), anyLong(), anyString());

    DnsErrorCode errorCode = DnsErrorCode.ERROR_DNS_INVALID_QUERY;
    DnsException ex = new DnsException(Type.SRV, query, errorCode);
    when(lookupFactory.createLookup(query, Type.SRV)).thenThrow(ex);

    resolver.lookupSRV(query);

    ArgumentCaptor<String> argumentCaptor1 = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> argumentCaptor2 = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Long> argumentCaptor3 = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<String> argumentCaptor4 = ArgumentCaptor.forClass(String.class);
    verify(metricService)
        .sendDNSMetric(
            argumentCaptor1.capture(),
            argumentCaptor2.capture(),
            argumentCaptor3.capture(),
            argumentCaptor4.capture());

    String actualQuery = argumentCaptor1.getValue();
    Assert.assertEquals(query, actualQuery);

    String actualQueryType = argumentCaptor2.getValue();
    Assert.assertEquals("SRV", actualQueryType);

    Long processingDelay = argumentCaptor3.getValue();
    Assert.assertTrue(processingDelay >= 0L);

    String errorMsg = argumentCaptor4.getValue();
    Assert.assertEquals(errorCode.name(), errorMsg);
  }

  @Test
  public void testSrvLookupThrowsNestedExceptionWithMetrics() throws Exception {
    String query = "thefqdn2.";
    doNothing().when(metricService).sendDNSMetric(anyString(), anyString(), anyLong(), anyString());

    when(lookupFactory.createLookup(query, Type.SRV)).thenReturn(testLookupSrv(query));
    when(xbillResolver.send(any(Message.class)))
        .thenReturn(
            messageWithRCode(
                query,
                Rcode.SERVFAIL)); // RCode = 2 (try again) -> DnsErrorCode.ERROR_DNS_QUERY_TIMEDOUT

    resolver.lookupSRV(query);

    ArgumentCaptor<String> argumentCaptor1 = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> argumentCaptor2 = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Long> argumentCaptor3 = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<String> argumentCaptor4 = ArgumentCaptor.forClass(String.class);
    verify(metricService)
        .sendDNSMetric(
            argumentCaptor1.capture(),
            argumentCaptor2.capture(),
            argumentCaptor3.capture(),
            argumentCaptor4.capture());

    String actualQuery = argumentCaptor1.getValue();
    Assert.assertEquals(query, actualQuery);

    String actualQueryType = argumentCaptor2.getValue();
    Assert.assertEquals("SRV", actualQueryType);

    Long processingDelay = argumentCaptor3.getValue();
    Assert.assertTrue(processingDelay >= 0L);

    String errorMsg = argumentCaptor4.getValue();
    Assert.assertEquals(DnsErrorCode.ERROR_DNS_QUERY_TIMEDOUT.name(), errorMsg);
  }

  @Test
  public void testALookupThrowsNestedExceptionWithMetrics() throws Exception {
    String query = "thefqdn2.";
    doNothing().when(metricService).sendDNSMetric(anyString(), anyString(), anyLong(), anyString());

    when(lookupFactory.createLookup(query, Type.A)).thenReturn(testLookupA(query));
    when(xbillResolver.send(any(Message.class)))
        .thenReturn(
            messageWithRCode(
                query,
                Rcode.SERVFAIL)); // RCode = 2 (try again) -> DnsErrorCode.ERROR_DNS_QUERY_TIMEDOUT

    resolver.lookupA(query);

    ArgumentCaptor<String> argumentCaptor1 = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> argumentCaptor2 = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Long> argumentCaptor3 = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<String> argumentCaptor4 = ArgumentCaptor.forClass(String.class);
    verify(metricService)
        .sendDNSMetric(
            argumentCaptor1.capture(),
            argumentCaptor2.capture(),
            argumentCaptor3.capture(),
            argumentCaptor4.capture());

    String actualQuery = argumentCaptor1.getValue();
    Assert.assertEquals(query, actualQuery);

    String actualQueryType = argumentCaptor2.getValue();
    Assert.assertEquals("A", actualQueryType);

    Long processingDelay = argumentCaptor3.getValue();
    Assert.assertTrue(processingDelay >= 0L);

    String errorMsg = argumentCaptor4.getValue();
    Assert.assertEquals(DnsErrorCode.ERROR_DNS_QUERY_TIMEDOUT.name(), errorMsg);
  }

  private void setupResponseForSrvQuery(String queryFqdn, String responseFqdn, String... results)
      throws IOException {
    when(lookupFactory.createLookup(queryFqdn, Type.SRV)).thenReturn(testLookupSrv(queryFqdn));
    when(xbillResolver.send(any(Message.class)))
        .thenReturn(messageWithNodes(responseFqdn, results));
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
}
