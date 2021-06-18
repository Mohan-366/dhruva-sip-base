package com.cisco.dsb.sip.stack.dns;

import com.cisco.dsb.common.dns.DnsException;
import com.cisco.dsb.common.dns.DnsInjectionService;
import com.cisco.dsb.common.dns.DnsLookup;
import com.cisco.dsb.common.dns.dto.DNSARecord;
import com.cisco.dsb.common.dns.dto.DNSSRVRecord;
import com.cisco.dsb.sip.dto.*;
import com.cisco.dsb.sip.enums.DNSRecordSource;
import com.cisco.dsb.sip.enums.LocateSIPServerTransportType;
import com.cisco.dsb.sip.stack.SipTransportType;
import com.cisco.dsb.sip.stack.dto.BindingInfo;
import com.cisco.dsb.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.sip.stack.util.IPValidator;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.sip.address.SipURI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SipServerLocator implements SipResolver {
  private static final Logger logger = DhruvaLoggerFactory.getLogger(SipServerLocator.class);
  private final DnsLookup dnsLookup;
  public static boolean m_useDsUnreachableTable = false;

  private DnsInjectionService dnsInjectionService;

  private static final int DEFAULT_PORT = SipTransportType.T_UDP.getDefaultPort();
  private static final int TLS_DEFAULT_PORT = SipTransportType.T_TLS.getDefaultPort();

  @Autowired
  public SipServerLocator(DnsInjectionService dnsInjectionService, DnsLookup dnsLookup) {
    this.dnsInjectionService = dnsInjectionService;
    this.dnsLookup = dnsLookup;
  }

  public LocateSIPServersResponse resolve(
      String name, LocateSIPServerTransportType transport, @Nullable Integer port)
      throws ExecutionException, InterruptedException {
    return resolve(name, transport, port, null);
  }

  /**
   * List possible possible network hops to send SIP message to specified host and transport.
   *
   * @param name SIP domain or hostname to resolve using DNS (RFC 3263). May be IP address.
   * @param transportLookupType Specifies which transports should be included in SRV search. * TCP -
   *     SRV lookup for TCP only. All hops returned are for TCP. * TLS - SRV lookup for TLS only.
   *     All hops returned are for TLS. * TLS_AND_TCP - Attempt SRV lookup for TLS. If no hits, SRV
   *     lookup for TCP. In all three cases, port affects whether SRV lookup is attempted, see
   *     below.
   * @param port Port number (optional). Follow RFC 3263 logic: If port specified, lookup name as
   *     hostname only. If port = null, attempt SRV lookup to get the hostname:port pairs. If no SRV
   *     hits, resort to lookup name as hostname.
   * @param userIdInject
   * @return Object containing the list of possible next network hops in the order in which
   *     connection should be attempted, plus the DNS entries matched to derive those hops. If name
   *     is an IP address, the list contains one Hop for that host (basically the parameters passed
   *     in). If not, and no DNS matches for name are found, the lists can be empty.
   *     <p>Contract (for now) is that the hops returned will always be for the same transport.
   *     <p>See unit tests for examples of combinations of the input arguments.
   */
  public LocateSIPServersResponse resolve(
      String name,
      LocateSIPServerTransportType transportLookupType,
      @Nullable Integer port,
      @Nullable String userIdInject)
      throws ExecutionException, InterruptedException {
    LocateSIPServersResponse response = new LocateSIPServersResponse();
    LogProxy responseLog = new LogProxy(response.getLog());

    final String traceUserIdInject =
        (userIdInject == null) ? "" : String.format(" userIdInject=%s", userIdInject);
    logger.info(
        "Resolve name=\"{}\" transportLookupType={} port={}{}",
        name,
        transportLookupType,
        port,
        traceUserIdInject);
    responseLog.append(
        "Resolve name=\"%s\" transportLookupType=%s port=%s%s",
        name, transportLookupType, port, traceUserIdInject);

    if (transportLookupType == null) {
      responseLog.append("ERROR Invalid transportLookupType=null");
      return response;
    }
    if (port != null && !(0 <= port && port <= 65535)) {
      responseLog.append("ERROR Invalid port=%s", port);
      return response;
    }

    if (IPValidator.hostIsIPAddr(name)) {
      responseLog.append("Host=%s is IP address. No DNS lookup required.", name);
      // First make sure we have a concrete transport, TLS or TCP.
      Optional<Transport> optTransport = transportLookupType.toSipTransport();
      if (!optTransport.isPresent()) {
        if (port == null) {
          // Nothing to go by. Assume TCP. There's nothing hard-core about this, change as needed.
          optTransport = Optional.of(Transport.TCP);
        } else {
          optTransport = Optional.of(inferTransportFromPort(port));
        }
      }
      // Once we know transport, can ensure we have a port
      int portScrubbed = ensurePort(port, optTransport.get());

      ArrayList<Hop> hopList = new ArrayList<Hop>();
      hopList.add(new Hop(null, name, optTransport.get(), portScrubbed, 0, DNSRecordSource.DNS));
      response.setHops(hopList);
      response.setType(LocateSIPServersResponse.Type.IP);
      return response;
    }

    List<InjectedDNSSRVRecord> injectSRV = dnsInjectionService.getInjectedSRV(userIdInject);
    List<InjectedDNSARecord> injectA = dnsInjectionService.getInjectedA(userIdInject);

    Optional<Transport> t = transportLookupType.toSipTransport();
    Transport sipTransport = t.orElse(Transport.TLS);

    if (transportLookupType == LocateSIPServerTransportType.TCP
        || transportLookupType == LocateSIPServerTransportType.UDP
        || transportLookupType == LocateSIPServerTransportType.TLS) {
      resolveRFC3263(response, responseLog, name, port, sipTransport, injectSRV, injectA);
    } else if (transportLookupType == LocateSIPServerTransportType.TLS_AND_TCP) {
      resolveTLSAndTCP(response, responseLog, name, port, injectSRV, injectA);
    } else if (transportLookupType == LocateSIPServerTransportType.TCP_AND_TLS) {
      resolveTCPandTLS(response, responseLog, name, port, injectSRV, injectA);
    } else {
      throw new IllegalArgumentException(transportLookupType.toString());
    }
    return response;
  }

  @Override
  public void setSizeExceedsMTU(boolean sizeExceedsMTU) {}

  @Override
  public void setSupportedTransports(byte supported_transports) {}

  // This implements the lookup logic described in RFC 3263, at least the parts of it that we
  // support.
  // Some pieces not in place yet are:
  // AAAA lookup - IPv6 support for Spark and dhruva is not high on priority list at the moment
  // (2015-08)
  // NAPTR lookup - Very few customers (if any) use this. It could be useful for cloud calling
  //                for a customer to tell us whether to use TLS or TCP SIP.
  // If port is not specified:
  //    Attempt to lookup name as a domain name in SRV for the specified transport.
  //    If any SRV records found, resolve the hosts found to IP's and return hops.
  //    If no SRV records found, attempt lookup name as hostname. Hops will have port matching the
  // transport default.
  // If port specified:
  //    Lookup name as hostname (no SRV lookup). Hops returned will have the specified port.
  private void resolveRFC3263(
      LocateSIPServersResponse response,
      LogProxy responseLog,
      String name,
      Integer port,
      Transport transport,
      List<InjectedDNSSRVRecord> injectSRV,
      List<InjectedDNSARecord> injectA)
      throws ExecutionException, InterruptedException {
    int defaultPort = 5060;

    if (transport == Transport.TCP || transport == Transport.UDP) defaultPort = DEFAULT_PORT;
    if (transport == Transport.TLS) defaultPort = TLS_DEFAULT_PORT;

    final int portScrubbed = (port == null) ? defaultPort : port;

    if (port == null) {
      resolveSRV(response, responseLog, name, transport, injectSRV, injectA);
      if (response.getDnsARecords().isEmpty()) {
        resolveHostname(response, responseLog, name, portScrubbed, transport, injectA);
      }
    } else {
      responseLog.append("Port=%s was specified. Do not attempt SRV lookup.", port);
      resolveHostname(response, responseLog, name, portScrubbed, transport, injectA);
    }
  }

  // This is a quick-n-dirty implementation to get cloud calling working to cisco.com.
  // If port unspecified (we would expect this in most cases):
  //   Lookup name in SRV TLS. If any hits, return them. else...
  //   Lookup name in SRV TCP. If any hits, return them. else...
  //   Lookup name as hostname. Return any hits (may be none) with transport=tcp.
  //     There's nothing hard driving the choice of TCP vs TLS here. Whatever works best in
  // practice.
  // If port specified:  (this can be only a hostname lookup)
  //   Lookup name as hostname. Return hops having transport that is the default for the port.
  // Note that we can return hops for only one transport, TLS or TCP.
  //
  // TODO The way it should work (future) is we retrieve all the hops for TLS and all hops for TCP
  // and concatenate the lists. That's much more work to implement because of the way
  // the selection of transport (sipStack) is woven into the INVITE/OPTIONS send/retry flows.
  public void resolveTLSAndTCP(
      LocateSIPServersResponse response,
      LogProxy responseLog,
      String name,
      @Nullable Integer port,
      List<InjectedDNSSRVRecord> injectSRV,
      List<InjectedDNSARecord> injectA)
      throws ExecutionException, InterruptedException {

    if (port == null) {
      // First lookup name as SRV TLS
      resolveSRV(response, responseLog, name, Transport.TLS, injectSRV, injectA);
      // If no hits, try lookup as SRV TCP
      if (response.getDnsARecords().size() <= 0) {
        resolveSRV(response, responseLog, name, Transport.TCP, injectSRV, injectA);
      }
      // If still no hits, try lookup as hostname. Any hops found are returned as TCP.
      if (response.getDnsARecords().size() <= 0) {
        final int portScrubbed = ensurePort(null, Transport.TLS);
        resolveHostname(response, responseLog, name, portScrubbed, Transport.TLS, injectA);
      }

      if (response.getDnsARecords().size() <= 0) {
        final int portScrubbed = ensurePort(null, Transport.TCP);
        resolveHostname(response, responseLog, name, portScrubbed, Transport.TCP, injectA);
      }

    } else {
      // Work backwards from port to deduce the transport. 5060->TCP, anything else assume TLS.
      final Transport transport = inferTransportFromPort(port);
      resolveHostname(response, responseLog, name, port, transport, injectA);
    }
  }

  public void resolveTCPandTLS(
      LocateSIPServersResponse response,
      LogProxy responseLog,
      String name,
      @Nullable Integer port,
      List<InjectedDNSSRVRecord> injectSRV,
      List<InjectedDNSARecord> injectA)
      throws ExecutionException, InterruptedException {

    if (port == null) {
      // First lookup name as SRV TCP
      resolveSRV(response, responseLog, name, Transport.TCP, injectSRV, injectA);
      // If no hits, try lookup as SRV TLS
      if (response.getDnsARecords().size() <= 0) {
        resolveSRV(response, responseLog, name, Transport.TLS, injectSRV, injectA);
      }
      // If still no hits, try lookup as hostname. Any hops found are returned as TCP.
      if (response.getDnsARecords().size() <= 0) {
        final int portScrubbed = ensurePort(null, Transport.TCP);
        resolveHostname(response, responseLog, name, portScrubbed, Transport.TCP, injectA);
      }

      if (response.getDnsARecords().size() <= 0) {
        final int portScrubbed = ensurePort(null, Transport.TLS);
        resolveHostname(response, responseLog, name, portScrubbed, Transport.TLS, injectA);
      }
    } else {
      // Work backwards from port to deduce the transport. 5060->TCP, anything else assume TLS.
      final Transport transport = inferTransportFromPort(port);
      resolveHostname(response, responseLog, name, port, transport, injectA);
    }
  }

  // Lookup name/transport in SRV. If any SRV records are found, lookup the hostname records using
  // the targets in the SRV records and construct an ordered list of Hops.
  // Append results to response and responseLog.
  private void resolveSRV(
      LocateSIPServersResponse response,
      LogProxy responseLog,
      String name,
      Transport transport,
      List<InjectedDNSSRVRecord> injectSRV,
      List<InjectedDNSARecord> injectA)
      throws ExecutionException, InterruptedException {

    // SRV records are ordered per RFC 3263. Must preserve the ordering.
    // Also want to remove duplicate hops. LinkedHashSet does both.
    // If two hops differ only in source (DNS | INJECTED) they are not considered dups.
    // This allows us to send to a specific IP address earlier in the chain when needed.
    try {
      Set<Hop> hops = new LinkedHashSet<>();
      List<MatchedDNSSRVRecord> srvRecords =
          resolveSRV(name, transport.toString(), injectSRV, responseLog);
      response.setDnsSRVRecords(srvRecords);
      for (MatchedDNSSRVRecord r : srvRecords) {
        final DNSSRVRecord srvRecord = r.getRecord();
        List<MatchedDNSARecord> aRecordsFromHost =
            lookupHostname(srvRecord.getTarget(), injectA, responseLog);
        // allow dups. Should be rare, but let's see them.
        response.getDnsARecords().addAll(aRecordsFromHost);
        // IP address comes from A record, port comes from the SRV record
        List<Hop> srvHops =
            aRecordsFromHost.stream()
                .map(
                    rA -> {
                      // If the SRV or host record were injected, the hop is tagged as injected too.
                      DNSRecordSource source =
                          (r.getSource() == DNSRecordSource.INJECTED
                                  || rA.getSource() == DNSRecordSource.INJECTED)
                              ? DNSRecordSource.INJECTED
                              : DNSRecordSource.DNS;
                      return new Hop(
                          removeTrailingPeriod(rA.getRecord().getName()),
                          rA.getRecord().getAddress(),
                          transport,
                          srvRecord.getPort(),
                          srvRecord.getPriority(),
                          source);
                    })
                .collect(Collectors.toList());

        hops.addAll(srvHops);
      }
      if (!hops.isEmpty()) {
        response.setType(LocateSIPServersResponse.Type.SRV);
      }
      response.getHops().addAll(hops);

      responseLog.append(
          "Found %s SRV records for name=%s transport=%s",
          response.getDnsSRVRecords().size(), name, transport.toString());
    } catch (DnsException ex) {
      response.setDnsException(ex);
    } catch (Exception e) {
      if (e.getCause() instanceof DnsException) {
        response.setDnsException((DnsException) e.getCause());
      }
      logger.warn("exception while resolving dns {}", e.getCause().getMessage());
    }
  }

  // Lookup name as hostname in DNS A (AAAA not implemented yet).
  // Append results to response and responseLog.
  // The hops are derived from the hostname records found and port.
  private void resolveHostname(
      LocateSIPServersResponse response,
      LogProxy responseLog,
      String name,
      int port,
      Transport transport,
      List<InjectedDNSARecord> injectA)
      throws ExecutionException, InterruptedException {
    try {
      responseLog.append("Lookup hostname=%s.", name);
      response.setDnsARecords(lookupHostname(name + ".", injectA, responseLog));
      responseLog.append("Found %s hostname records.", response.getDnsARecords().size());
      if (!response.getDnsARecords().isEmpty()) {
        response.setType(LocateSIPServersResponse.Type.HOSTNAME);
      }
      response.setHops(
          response.getDnsARecords().stream()
              .map(
                  r ->
                      new Hop(
                          removeTrailingPeriod(r.getRecord().getName()),
                          r.getRecord().getAddress(),
                          transport,
                          port,
                          0,
                          r.getSource()))
              .distinct()
              .collect(Collectors.toList()));
    } catch (DnsException ex) {
      response.setDnsException(ex);
    } catch (Exception e) {
      if (e.getCause() instanceof DnsException) {
        response.setDnsException((DnsException) e.getCause());
      }
      logger.warn("exception while resolving dns {}", e.getCause().getMessage());
    }
  }

  private List<MatchedDNSARecord> lookupHostname(
      String name, List<InjectedDNSARecord> injectARecords, LogProxy responseLog)
      throws ExecutionException, InterruptedException {
    // Return any "real" or injected A records matching name.
    // TODO AAAA records.
    /////// responseLog.append("resolveHostname name=\"%s\"", name); // Do not store in in-memory
    // list

    List<MatchedDNSARecord> injectedARecords =
        injectARecords.stream()
            .map(
                r -> {
                  responseLog.append("Test injected A %s.", r);
                  return r;
                })
            .filter(r -> addTrailingPeriod(name).equalsIgnoreCase(addTrailingPeriod(r.getName())))
            .map(
                r -> {
                  responseLog.append("Matched injected A %s.", r);
                  return new MatchedDNSARecord(r, DNSRecordSource.INJECTED);
                })
            .collect(Collectors.toList());

    if (injectedARecords.stream()
        .anyMatch(
            r -> ((InjectedDNSARecord) r.getRecord()).getInjectAction() == DNSInjectAction.BLOCK)) {
      return Lists.newArrayList();
    }

    List<MatchedDNSARecord> replaceRecords =
        injectedARecords.stream()
            .filter(
                r ->
                    ((InjectedDNSARecord) r.getRecord()).getInjectAction()
                        == DNSInjectAction.REPLACE)
            .collect(Collectors.toList());

    if (!replaceRecords.isEmpty()) {
      return replaceRecords;
    }

    List<MatchedDNSARecord> matchedRecords =
        injectedARecords.stream()
            .filter(
                r ->
                    ((InjectedDNSARecord) r.getRecord()).getInjectAction()
                        == DNSInjectAction.PREPEND)
            .collect(Collectors.toList());

    // If no injected A records, perform real DNS lookup up A records.

    CompletableFuture<List<DNSARecord>> aRecords = dnsLookup.lookupA(name);

    aRecords.get().stream()
        .map(
            r -> {
              ////// responseLog.append("Found A %s.", r); // Do not store in in-memory list
              return new MatchedDNSARecord(r, DNSRecordSource.DNS);
            })
        .forEach(matchedRecords::add);

    return matchedRecords;
  }

  private List<MatchedDNSSRVRecord> resolveSRV(
      String name,
      String transport,
      List<InjectedDNSSRVRecord> injectSRVRecords,
      LogProxy responseLog)
      throws ExecutionException, InterruptedException {
    // Return any "real" or injected SRV records matching name.

    final String srvName =
        transport.equalsIgnoreCase("tls")
            ? String.format("_sips._tcp.%s", name)
            : String.format("_sip._%s.%s", transport, name);

    responseLog.append("Lookup SRV=%s", srvName);

    List<MatchedDNSSRVRecord> matchedInjectedRecords =
        injectSRVRecords.stream()
            .map(
                r -> {
                  responseLog.append("TEST injected SRV %s.", r);
                  return r;
                })
            // Select only injected records that match the srv name
            .filter(
                r -> addTrailingPeriod(srvName).equalsIgnoreCase(addTrailingPeriod(r.getName())))
            // Append to the results
            .map(
                r -> {
                  responseLog.append("Found injected SRV %s->%s.", srvName, r);
                  return new MatchedDNSSRVRecord(r, DNSRecordSource.INJECTED);
                })
            .collect(Collectors.toList());

    if (matchedInjectedRecords.stream()
        .anyMatch(
            r ->
                ((InjectedDNSSRVRecord) r.getRecord()).getInjectAction()
                    == DNSInjectAction.BLOCK)) {
      return Lists.newArrayList();
    }

    List<MatchedDNSSRVRecord> replaceRecords =
        matchedInjectedRecords.stream()
            .filter(
                r ->
                    ((InjectedDNSSRVRecord) r.getRecord()).getInjectAction()
                        == DNSInjectAction.REPLACE)
            .collect(Collectors.toList());

    if (!replaceRecords.isEmpty()) {
      return sortSRVRecords(replaceRecords);
    }

    List<MatchedDNSSRVRecord> matchedRecords =
        matchedInjectedRecords.stream()
            .filter(
                r ->
                    ((InjectedDNSSRVRecord) r.getRecord()).getInjectAction()
                        == DNSInjectAction.PREPEND)
            .collect(Collectors.toList());

    CompletableFuture<List<DNSSRVRecord>> srvRecords = dnsLookup.lookupSRV(srvName);
    srvRecords.get().stream()
        .map(
            r -> {
              ////// responseLog.append("Found SRV %s->%s.", srvName, r);  // Do not store in
              // in-memory list
              return new MatchedDNSSRVRecord(r, DNSRecordSource.DNS);
            })
        .forEach(matchedRecords::add);

    matchedRecords = sortSRVRecords(matchedRecords);

    return matchedRecords;
  }

  private List<MatchedDNSSRVRecord> sortSRVRecords(List<MatchedDNSSRVRecord> records) {
    // optimization for common case
    if (records.size() <= 1) {
      return records;
    }
    /* RFC 2782, for Priority...
    A client MUST attempt to
    contact the target host with the lowest-numbered priority it can
    reach; target hosts with the same priority SHOULD be tried in an
    order defined by the weight field.
    */
    // Collect records into bins by key=priority. TreeMap sorts by the key.
    Map<Integer, List<MatchedDNSSRVRecord>> mapPriority = new TreeMap<>();
    records.stream()
        .forEach(
            r -> {
              final int priority = r.getRecord().getPriority();
              List<MatchedDNSSRVRecord> list = new ArrayList<MatchedDNSSRVRecord>();
              if (!mapPriority.containsKey(priority)) {
                mapPriority.put(priority, list);
              } else {
                list = mapPriority.get(priority);
              }
              list.add(r);
            });

    List<MatchedDNSSRVRecord> result = new ArrayList<MatchedDNSSRVRecord>();
    for (Map.Entry<Integer, List<MatchedDNSSRVRecord>> entry : mapPriority.entrySet()) {
      List<MatchedDNSSRVRecord> sortedBucket = sortByWeight(entry.getValue());
      result.addAll(sortedBucket);
    }
    return result;
  }

  @SuppressFBWarnings(value = "PREDICTABLE_RANDOM", justification = "baseline suppression")
  private List<MatchedDNSSRVRecord> sortByWeight(List<MatchedDNSSRVRecord> records) {
    /* RFC 3263 outlines a recursive algorithm to order records having the same priority.
    Recursive algorithms can hard to test because of the boundary conditions.
    So we went with something that's simpler but in practice should be OK.
    Maybe they are mathematically equivalent. Haven't bother to prove it one way or the other :)
    */

    // Optimization
    if (records.size() <= 1) {
      return records;
    }

    // TreeMap orders by key
    Map<Double, MatchedDNSSRVRecord> ordered = new TreeMap<>();
    for (MatchedDNSSRVRecord record : records) {
      // avoid 0 as a weight as it will produce identical keys and the
      // loop below never exits
      int weight = record.getRecord().getWeight() + 1;

      // negative of weight puts highest weight at the front of the list
      double key = -1 * Math.random() * weight;
      while (ordered.containsKey(key)) {
        key = -1 * Math.random() * weight;
      }
      ordered.put(key, record);
    }

    return new ArrayList<>(ordered.values());
  }

  private Transport inferTransportFromPort(int port) {
    return (port == 5060) ? Transport.TCP : Transport.TLS;
  }

  private int ensurePort(Integer port, Transport transport) {
    int defaultPort = 5060;

    if (transport == Transport.TCP || transport == Transport.UDP) defaultPort = DEFAULT_PORT;
    if (transport == Transport.TLS) defaultPort = TLS_DEFAULT_PORT;

    return (port == null) ? defaultPort : port;
  }

  public static String addTrailingPeriod(String s) {
    // In DNS, the period indicates the name is fully qualified.
    // This is what we typically find for SRV and A records, so clean up the parameter before
    // inserting.
    if (s == null) {
      return null;
    }
    return s.endsWith(".") ? s : s + ".";
  }

  public static String removeTrailingPeriod(String s) {
    // In DNS, the period indicates the name is fully qualified.
    // This is what we typically find for SRV and A records, so clean up the parameter before
    // inserting.
    return s != null && s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
  }

  // The response contains a "log", list of strings recording key steps or diagnostic comments.
  // This avoids spamming kibana with multiple trace entries for a given run through the locator.
  // Client code can dump the response object into one trace entry, and all the information is
  // there.
  // This class is a shim to shorten the statements here that write to the log.
  private static class LogProxy {
    private List<String> log;

    public LogProxy(List<String> log) {
      this.log = log;
    }

    public void append(String format, Object... args) {
      String s = new Formatter().format(format, args).toString();
      this.log.add(s);
    }
  }

  public boolean shouldSearch(SipURI sipURL) {
    boolean ret_value;
    String host = sipURL.getMAddrParam();

    if (host == null) {
      host = sipURL.getHost();
    }

    ret_value =
        !(IPValidator.hostIsIPAddr(host)
            && (sipURL.isSecure() || sipURL.getTransportParam() != null)
            && sipURL.getPort() >= 0);

    return ret_value;
  }

  /**
   * Determine if, according to the SIP spec, more than one service end point should be tried.
   *
   * @param hostName host part of URI
   * @param port port in the URI
   * @param transport transport param of the URI
   * @return <code>true</code> if according to the RFC, more than one service end point should be
   *     tried otherwise returns <code>false</code>
   */
  public boolean shouldSearch(String hostName, int port, Transport transport) {
    return !(IPValidator.hostIsIPAddr(hostName)
        && (port != BindingInfo.REMOTE_PORT_UNSPECIFIED)
        && (transport != BindingInfo.BINDING_TRANSPORT_UNSPECIFIED));
  }

  @Override
  public boolean isSupported(Transport transport) {
    return true;
  }
}
