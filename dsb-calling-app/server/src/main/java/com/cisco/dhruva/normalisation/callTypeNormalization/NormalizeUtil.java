package com.cisco.dhruva.normalisation.callTypeNormalization;

import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyCookieImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.CustomLog;
import org.apache.commons.collections4.CollectionUtils;

@CustomLog
public class NormalizeUtil {

  private static final String IPADDRESS_PATTERN =
      "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
  private static HeaderFactoryImpl headerFactory = new HeaderFactoryImpl();

  public static void normalize(
      SIPRequest request, EndPoint endPoint, List<HeaderToNormalize> headersToReplaceWithRemoteIP) {
    normalize(request, null, endPoint, null, headersToReplaceWithRemoteIP, null, null, null);
  }

  public static void normalize(
      SIPRequest request,
      DhruvaNetwork outGoingNetwork,
      List<HeaderToNormalize> headersToRemoveWithOwnIP,
      List<String> headersToRemove) {
    normalize(
        request,
        outGoingNetwork,
        null,
        headersToRemoveWithOwnIP,
        null,
        headersToRemove,
        null,
        null);
  }

  public static void normalize(SIPRequest request, EndPoint endPoint) {
    normalize(request, null, endPoint, null, null, null, null, null);
  }

  public static void normalize(
      SIPRequest request, List<String[]> paramsToAdd, List<String[]> paramsToRemove) {
    normalize(request, null, null, null, null, null, paramsToAdd, paramsToRemove);
  }

  public static void normalize(
      SIPRequest request,
      DhruvaNetwork outgoingNetwork,
      EndPoint endPoint,
      List<HeaderToNormalize> headersToReplaceWithOwnIP,
      List<HeaderToNormalize> headersToReplaceWithRemoteIP,
      List<String> headersToRemove,
      List<String[]> paramsToAdd,
      List<String[]> paramsToRemove) {
    String remoteIPAddress = null;
    int remotePort;
    String ownIPAddress = null;
    SipUri rUri = ((SipUri) request.getRequestURI());
    // Add all normalizations here
    try {
      if (endPoint != null) {
        remoteIPAddress = endPoint.getHost();
        remotePort = endPoint.getPort();
        if (remoteIPAddress != null) {
          rUri.setHost(remoteIPAddress);
          rUri.setPort(remotePort);
        }
      }

      // remove Headers
      if (CollectionUtils.isNotEmpty(headersToRemove)) {
        headersToRemove.stream().forEach(request::removeHeader);
      }

      // params to remove
      if (CollectionUtils.isNotEmpty(paramsToRemove)) {
        removeHeaderParams(request, paramsToRemove);
      }

      // params to add
      if (CollectionUtils.isNotEmpty(paramsToAdd)) {
        addHeaderParams(request, paramsToAdd);
      }

      // Replace IP in Headers with own IP
      if (headersToReplaceWithOwnIP != null) {
        if (outgoingNetwork != null) {
          ownIPAddress = outgoingNetwork.getListenPoint().getHostIPAddress();
          replaceIPInHeader(request, headersToReplaceWithOwnIP, ownIPAddress);
        } else {
          logger.error("Outgoing network is null. Cannot set own IP");
        }
      }

      // Replace IP in Headers with own IP
      if (headersToReplaceWithRemoteIP != null) {
        replaceIPInHeader(request, headersToReplaceWithRemoteIP, remoteIPAddress);
      }

    } catch (ParseException | RuntimeException e) {
      logger.error("Unable to perform normalization.", e);
    }
  }

  public static void normalizeResponse(
      ProxySIPResponse proxySIPResponse,
      List<HeaderToNormalize> headersToReplaceWithOwnIP,
      List<HeaderToNormalize> headersToReplaceWithRemoteIP,
      List<String> headersToRemove) {
    DhruvaNetwork responseOutgoingNetwork =
        ((ProxyCookieImpl) proxySIPResponse.getCookie()).getRequestIncomingNetwork();
    String ownIPAddress = responseOutgoingNetwork.getListenPoint().getHostIPAddress();
    String remoteIP = proxySIPResponse.getResponse().getTopmostViaHeader().getHost();
    SIPResponse response = proxySIPResponse.getResponse();
    if (CollectionUtils.isNotEmpty(headersToReplaceWithOwnIP)) {
      replaceIPInHeader(response, headersToReplaceWithOwnIP, ownIPAddress);
    }
    if (CollectionUtils.isNotEmpty(headersToReplaceWithRemoteIP)) {
      replaceIPInHeader(response, headersToReplaceWithRemoteIP, remoteIP);
    }
    if (CollectionUtils.isNotEmpty(headersToRemove)) {
      headersToRemove.stream().forEach(response::removeHeader);
    }
  }

  public static Consumer<SIPResponse> doStrayResponseNormalization() {
    return sipResponse -> {
      String network = (String) sipResponse.getApplicationData();
      Optional<DhruvaNetwork> responseOutgoingNetwork = DhruvaNetwork.getNetwork(network);
      String remoteIPAddress = sipResponse.getTopmostVia().getHost();
      try {
        String ownIPAddress = responseOutgoingNetwork.get().getListenPoint().getHostIPAddress();

        ((SipUri) sipResponse.getTo().getAddress().getURI()).setHost(ownIPAddress);
        ((SipUri) sipResponse.getFrom().getAddress().getURI()).setHost(remoteIPAddress);
      } catch (ParseException e) {
        logger.error("Cannot normalize stray response {}", sipResponse, e);
      }
    };
  }

  private static void replaceIPInHeader(
      SIPMessage message, List<HeaderToNormalize> headerList, String ipAddress) {
    if (ipAddress == null) {
      logger.error(
          "IP address cannot be determined. IP Address normalization cannot be performed.");
      return;
    }
    headerList.stream()
        .forEach(
            headerForIPReplacement -> {
              if (!headerForIPReplacement.updateAllHeaderOccurrences) {
                SIPHeader header = (SIPHeader) message.getHeader(headerForIPReplacement.header);
                try {
                  if (header == null) {
                    logger.debug(
                        "Header {} not present in message. Skippig Normalization.",
                        headerForIPReplacement.header);
                    return;
                  }
                  header = getHeaderWithReplacedIP(header, ipAddress);
                  if (header != null) {
                    message.setHeader(header);
                  } else {
                    logger.error(
                        "No IP found to replace in header: {}", headerForIPReplacement.header);
                  }
                } catch (ParseException e) {
                  logger.error(
                      "Error while replacingIPHeader normalization in {}: {}",
                      headerForIPReplacement.header,
                      e);
                }
              } else {
                List<SIPHeader> newHeaderList = new ArrayList<>();
                ListIterator<SIPHeader> headers = message.getHeaders(headerForIPReplacement.header);
                while (headers.hasNext()) {
                  SIPHeader header = headers.next();
                  try {
                    header = getHeaderWithReplacedIP(header, ipAddress);
                    if (header != null) {
                      newHeaderList.add(header);
                    }
                  } catch (ParseException e) {
                    logger.error(
                        "Error while replacingIPHeader normalization in {}: {}",
                        headerForIPReplacement.header,
                        e);
                  }
                }
                message.removeHeader(headerForIPReplacement.header);
                message.setHeaders(newHeaderList);
              }
            });
  }

  private static SIPHeader getHeaderWithReplacedIP(SIPHeader sipHeader, String ipAddress)
      throws ParseException {
    String headerName = sipHeader.getName();
    String headerValue = sipHeader.toString().split(headerName + ": ")[1];
    String ipToReplace = getIPToReplace(headerValue);
    if (ipToReplace != null) {
      headerValue = headerValue.replace(ipToReplace, ipAddress);
      return ((SIPHeader) headerFactory.createHeader(headerName, headerValue));
    } else {
      return null;
    }
  }

  // Currently removing params only from requestUri, To and From Header
  private static void removeHeaderParams(SIPRequest request, List<String[]> paramsToRemove) {
    paramsToRemove.stream()
        .forEach(
            headerInfo -> {
              if (headerInfo.length != 2) {
                logger.error("Error adding normalization removeHeaderParams for {}", headerInfo);
                return;
              }
              String headerName = headerInfo[0];
              String param = headerInfo[1];
              SipUri sipUri = null;
              if (headerName.equals("requestUri")) {
                sipUri = (SipUri) request.getRequestURI();
              } else if (headerName.equals("To")) {
                request.getTo().removeParameter(param);
                sipUri = (SipUri) request.getTo().getAddress().getURI();
              } else if (headerName.equals("From")) {
                request.getFrom().removeParameter(param);
                sipUri = (SipUri) request.getFrom().getAddress().getURI();
              } else {
                logger.error(
                    "removeHeaderParams norm not added for {}. Currently available only for To, From and RUri",
                    headerName);
              }
              if (sipUri != null) {
                sipUri.removeParameter(param);
              }
            });
  }

  // Currently adding params only from requestUri, To and From Header
  private static void addHeaderParams(SIPRequest request, List<String[]> headersToAdd) {
    headersToAdd.stream()
        .forEach(
            headerInfo -> {
              if (headerInfo.length != 3) {
                logger.error("Error adding normalization for addHeaderParams for {}", headerInfo);
                return;
              }
              String headerName = headerInfo[0];
              String paramName = headerInfo[1];
              String paramValue = headerInfo[2];
              SipUri sipUri = null;
              try {
                if (headerName.equals("requestUri")) {
                  sipUri = (SipUri) request.getRequestURI();
                  if (sipUri != null) {
                    sipUri.setParameter(paramName, paramValue);
                  }
                } else if (headerName.equals("To")) {
                  request.getTo().setParameter(paramName, paramValue);
                } else if (headerName.equals("From")) {
                  request.getFrom().setParameter(paramName, paramValue);
                } else {
                  logger.error(
                      "addHeaderParams norm not added for {}. Currently available only for To, From and RUri",
                      headerName);
                }
              } catch (ParseException e) {
                logger.error("Error adding normalization for addHeaderParams for {}", headerInfo);
              }
            });
  }

  private static String getIPToReplace(String headerString) {
    Pattern pattern = Pattern.compile(IPADDRESS_PATTERN);
    Matcher matcher = pattern.matcher(headerString);
    if (matcher.find()) {
      return matcher.group();
    } else {
      return null;
    }
  }

  public static class HeaderToNormalize {

    String header;
    Boolean updateAllHeaderOccurrences;

    HeaderToNormalize(String header, Boolean updateAllHeaderOccurrences) {
      this.header = header;
      this.updateAllHeaderOccurrences = updateAllHeaderOccurrences;
    }
  }
}
