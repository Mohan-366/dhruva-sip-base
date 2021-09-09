package com.cisco.dsb.proxy.sip.tls;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.SSLException;
import lombok.CustomLog;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;

@CustomLog
public class HostNameValidationHelper {

  protected static final DefaultHostnameVerifier hostnameVerifier = new DefaultHostnameVerifier();
  public static final String VALID_WEBEX_SAN = "sip.webex.com";

  public static boolean match(List<String> hostnames, X509Certificate certificate) {
    if (hostnames == null || hostnames.isEmpty()) {
      return false;
    }

    if (certificate == null) {
      return false;
    }

    boolean result = hostnames.stream().anyMatch(hostname -> match(hostname, certificate));

    if (result) {
      logger.debug(
          "Match found when comparing hostnames {} to certificate {}",
          hostnames,
          toString(certificate));
    } else {
      logger.debug(
          "No match found when comparing hostnames {} to certificate {}",
          hostnames,
          toString(certificate));
    }

    return result;
  }

  private static boolean match(String hostname, X509Certificate certificate) {
    if (hostname == null || certificate == null) {
      return false;
    }

    try {
      hostnameVerifier.verify(hostname, certificate);
      return true;
    } catch (SSLException e) {
      try {
        Collection<List<?>> san = certificate.getSubjectAlternativeNames();
        if (hostname.toLowerCase().endsWith(".webex.com") && san != null) {
          logger.warn(
              "Hostname ends in .webex.com. Going to check SANs for the presence of sip.webex.com");
          return san.stream()
              .anyMatch(
                  x ->
                      x.get(1).toString().toLowerCase().equals(VALID_WEBEX_SAN)
                          && x.get(0)
                              .toString()
                              .equals("2") // Also check that the SAN is of type dNSName
                  );
        }
      } catch (CertificateParsingException ex) {
        logger.error("Failed to parse certificate: {}", toString(certificate), ex);
      }
      return false;
    }
  }

  public static String toString(X509Certificate certificate) {
    CertificateInfo certificateInfo = CertificateInfo.createFromX509Certificate(certificate);

    return certificateInfo.toString();
  }
}
