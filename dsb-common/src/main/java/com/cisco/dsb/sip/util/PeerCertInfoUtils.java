package com.cisco.dsb.sip.util;

import com.google.common.base.Strings;
import gov.nist.javax.sip.header.SIPHeader;
import io.netty.util.internal.StringUtil;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PeerCertInfoUtils {

  private static final String COMMON_NAME = "cn";
  private static final String SAN = "san";
  private static final String SAN_RFC822_NAME = "sanrfc822name";
  private static final String SAN_URI = "sanuniformresourceidentifier";
  private static final String SAN_DNS_NAME = "sandnsname";

  public static List<String> getPeerCertInfo(List<SIPHeader> headerList) {
    return getPeerCertInfo(headerList, -1);
  }

  /**
   * Parse all the SANs from X-Cisco-Peer-Cert-Info headers into a list of org domains. If the
   * argument is null or empty, returns an empty list.
   */
  public static List<String> getPeerCertInfo(List<SIPHeader> headerList, int maxDomains) {
    List<String> orgDomainsFromPeerCertInfoHeader = new ArrayList<>();

    if (headerList != null && !headerList.isEmpty()) {
      /*
      There can be multiple X-Cisco-Peer-Cert-Info headers from Cloud Proxy
      if VCS cannot fit all cert SANs into a single header.
      Iterate through each X-Cisco-Peer-Cert-Info header in the header list
      and add all of its SANs into the org name list.
      */
      for (SIPHeader header : headerList) {
        try {
          // This gets all the SANs as a single String out of the current X-Cisco-Peer-Cert-Info
          // header.
          String headerBody = header.getHeaderValue();

          if (StringUtil.isNullOrEmpty(headerBody)) {
            // TODO: logger.info("Empty x-cisco-peer-cert-info header body");
            continue;
          }

          // Parse the SIPHeader into individual SANs. For example, a single element could be:
          // <blabla=ecccptest.bts.pub.webex.com>
          String[] sanArray = headerBody.split(SipTokens.Comma);

          // If the # of SANs in this header will cause us to look up more domains than what's
          // allowed, truncate the SAN array.
          if (maxDomains > -1
              && sanArray.length > maxDomains - orgDomainsFromPeerCertInfoHeader.size()) {
            String[] omittedSans =
                Arrays.copyOfRange(
                    sanArray,
                    maxDomains - orgDomainsFromPeerCertInfoHeader.size(),
                    sanArray.length);
            // TODO: logger.info("The following SANs are being omitted: {}",
            // Arrays.toString(omittedSans));
            sanArray =
                Arrays.copyOf(sanArray, maxDomains - orgDomainsFromPeerCertInfoHeader.size());
          }

          // Iterate through each SAN in the X-Cisco-Peer-Cert-Info header.
          for (String san : sanArray) {
            // Remove spaces, "<", and ">" from the SAN, taking "
            // <sandNSName=ecccptest.bts.pub.webex.com>"
            // and leaving us with "sandNSName=ecccptest.bts.pub.webex.com"
            san = san.trim();
            if (san.startsWith("<")) {
              san = san.substring(1);
            }
            if (san.endsWith(">")) {
              san = san.substring(0, san.length() - 1);
            }
            // Parse the SAN name and value into an array. If, for some reason,
            // there is an "=" in the actual SAN name, do not parse that part.
            String[] sanNameValue = san.split("=", 2);

            if (sanNameValue.length != 2) {
              // TODO: logger.info("San format is missing a name or value. san: {}", san);
              continue;
            }

            String sanName = sanNameValue[0];
            String sanValue = sanNameValue[1];
            if (!Strings.isNullOrEmpty(sanValue)) {
              switch (sanName.toLowerCase()) {
                case COMMON_NAME:
                case SAN_DNS_NAME:
                  orgDomainsFromPeerCertInfoHeader.add(sanValue);
                  break;
                case SAN_URI:
                  try {
                    String host = new java.net.URI(sanValue).getHost();
                    // TODO:  logger.info("host: {}", host);
                    if (!Strings.isNullOrEmpty(host)) {
                      orgDomainsFromPeerCertInfoHeader.add(host);
                    }
                  } catch (URISyntaxException e) {
                    // TODO: logger.info("Parsing error in SAN type: {}, Value: {}", SAN_URI,
                    // sanValue, e);
                  }
                  break;
                case SAN_RFC822_NAME:
                  int atIndex = sanValue.lastIndexOf(SipTokens.At_Sign);
                  String domain = sanValue.substring(atIndex + 1);
                  if (!Strings.isNullOrEmpty(domain)) {
                    orgDomainsFromPeerCertInfoHeader.add(domain);
                  }
                  break;
                default:
                  // TODO: logger.info("X-Cisco-Peer-Cert-Info header contains a SAN type that
                  // cannot be processed: {}", sanName);
                  break;
              }
            }
          }
        } catch (Exception ex) {
          // TODO: logger.info("Error getting peer cert info from header: {}. ", header, ex);
        }
      }
    }
    return orgDomainsFromPeerCertInfoHeader;
  }
}
