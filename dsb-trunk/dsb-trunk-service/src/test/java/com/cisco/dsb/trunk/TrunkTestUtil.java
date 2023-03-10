package com.cisco.dsb.trunk;

import com.cisco.dsb.common.circuitbreaker.DsbCircuitBreaker;
import com.cisco.dsb.common.config.RoutePolicy;
import com.cisco.dsb.common.loadbalancer.LBType;
import com.cisco.dsb.common.servergroup.DnsServerGroupUtil;
import com.cisco.dsb.common.servergroup.SGType;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.sip.dto.HopImpl;
import com.cisco.dsb.common.sip.enums.DNSRecordSource;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.trunk.trunks.AbstractTrunk;
import com.cisco.dsb.trunk.trunks.Egress;
import com.github.javafaker.Faker;
import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.header.ContactList;
import java.security.SecureRandom;
import java.text.ParseException;
import java.util.*;
import javax.sip.InvalidArgumentException;

public class TrunkTestUtil {
  private DnsServerGroupUtil dnsServerGroupUtil;
  private SecureRandom random = new SecureRandom();
  private Faker faker = Faker.instance();

  public TrunkTestUtil(DnsServerGroupUtil dnsServerGroupUtil) {
    this.dnsServerGroupUtil = dnsServerGroupUtil;
  }

  public List<ServerGroupElement> getServerGroupElements(int count, boolean sameQ) {
    List<ServerGroupElement> sgeList = new ArrayList<>();
    List<Transport> transports = Arrays.asList(Transport.TCP, Transport.UDP, Transport.TLS);
    int[] qValues;
    if (sameQ) qValues = new int[] {10};
    else qValues = new int[] {10, 20, 30};

    int[] weights = {60, 30, 10};
    for (int i = 0; i < count; i++) {
      String ipAddress =
          random.nextInt(255)
              + "."
              + random.nextInt(255)
              + "."
              + random.nextInt(255)
              + "."
              + random.nextInt(255);
      int port = random.nextInt(1000);
      Transport transport = transports.get(random.nextInt(transports.size()));
      int qvalue = qValues[random.nextInt(qValues.length)];
      int weight = weights[i % weights.length];

      ServerGroupElement element =
          ServerGroupElement.builder()
              .setIpAddress(ipAddress)
              .setPort(port)
              .setTransport(transport)
              .setPriority(qvalue)
              .setWeight(weight)
              .build();
      sgeList.add(element);
    }
    return sgeList;
  }

  public void initTrunk(
      List<ServerGroup> serverGroups,
      AbstractTrunk abstractTrunk,
      DsbCircuitBreaker dsbCircuitBreaker) {
    Egress egrees = new Egress();
    Map<String, ServerGroup> serverGroupMap = egrees.getServerGroupMap();
    RoutePolicy rp =
        RoutePolicy.builder()
            .setName("trunk1")
            .setFailoverResponseCodes(Arrays.asList(500, 502, 503))
            .build();
    egrees.setLbType(LBType.WEIGHT);
    egrees.setRoutePolicyFromConfig(rp);
    serverGroups.forEach(sg -> serverGroupMap.put(sg.getHostName(), sg));
    abstractTrunk.setEgress(egrees);
    abstractTrunk.setDnsServerGroupUtil(dnsServerGroupUtil);
    abstractTrunk.setDsbCircuitBreaker(dsbCircuitBreaker);
  }

  public List<HopImpl> getHops(int count, ServerGroup sg1, boolean srv) {
    List<HopImpl> hops = new ArrayList<>();

    for (int i = 1; i <= count; i++) {
      int rand = random.nextInt(255) + 1;
      HopImpl hop =
          new HopImpl(
              sg1.getHostName(),
              faker.internet().ipV4Address(),
              sg1.getTransport(),
              srv ? rand * 30 : sg1.getPort(),
              10,
              100,
              DNSRecordSource.INJECTED);
      hops.add(hop);
    }
    return hops;
  }

  public ContactList getContactList(int count, String type, String hostname)
      throws ParseException, InvalidArgumentException {
    ContactList contactList = new ContactList();
    float[] qValues = {0.9f, 0.8f, 0.4f};
    for (int i = 1; i <= count; i++) {
      Contact contact = new Contact();
      AddressImpl address = new AddressImpl();
      SipUri uri = new SipUri();
      uri.setUser(faker.name().firstName());
      switch (type.toLowerCase(Locale.ROOT)) {
        case "static":
          uri.setHost(faker.internet().ipV4Address());
          break;
        case "a":
        default:
          uri.setHost(hostname != null ? hostname : faker.internet().domainName());
          break;
      }
      uri.setPort(faker.number().numberBetween(5060, 5070));
      address.setURI(uri);
      contact.setAddress(address);
      contact.setQValue(qValues[random.nextInt(qValues.length)]);
      contactList.add(contact);
    }

    return contactList;
  }

  public List<ServerGroup> getNSServerGroups(RoutePolicy sgRoutePolicy) {
    ServerGroup sg1 =
        ServerGroup.builder()
            .setName("ns1")
            .setHostName("ns1.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(1)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .build();

    ServerGroup sg2 =
        ServerGroup.builder()
            .setName("ns2")
            .setHostName("ns2.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .build();

    return Arrays.asList(sg1, sg2);
  }
}
