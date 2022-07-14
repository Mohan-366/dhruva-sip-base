package com.cisco.dhruva.normalisation.callTypeNormalization;

import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.util.EndPoint;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.message.SIPRequest;
import java.text.ParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sip.header.Header;
import lombok.CustomLog;
import org.apache.commons.collections4.CollectionUtils;

@CustomLog
public class NormalizeUtil {

  private static final String IPADDRESS_PATTERN =
      "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
  private static HeaderFactoryImpl headerFactory = new HeaderFactoryImpl();

  public static void normalize(
      SIPRequest request, EndPoint endPoint, List<String> headersToReplaceWithRemoteIP) {
    normalize(request, null, endPoint, null, headersToReplaceWithRemoteIP, null, null, null);
  }

  public static void normalize(
      SIPRequest request,
      DhruvaNetwork outGoingNetwork,
      List<String> headersToRemoveWithOwnIP,
      List<String> headersToRemove,
      List<String[]> paramsToRemove,
      List<String[]> paramsToAdd) {
    normalize(
        request,
        outGoingNetwork,
        null,
        headersToRemoveWithOwnIP,
        null,
        headersToRemove,
        paramsToRemove,
        paramsToAdd);
  }

  public static void normalize(SIPRequest request, EndPoint endPoint) {
    normalize(request, null, endPoint, null, null, null, null, null);
  }

  public static void normalize(SIPRequest request, List<String[]> paramsToAdd) {
    normalize(request, null, null, null, null, null, null, paramsToAdd);
  }

  public static void normalize(
      SIPRequest request,
      DhruvaNetwork outgoingNetwork,
      EndPoint endPoint,
      List<String> headersToReplaceWithOwnIP,
      List<String> headersToReplaceWithRemoteIP,
      List<String> headersToRemove,
      List<String[]> paramsToRemove,
      List<String[]> paramsToAdd) {
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

  private static void replaceIPInHeader(
      SIPRequest request, List<String> headerList, String ipAddress) {
    if (ipAddress == null) {
      logger.error(
          "IP address cannot be determined. IP Address normalization cannot be performed.");
      return;
    }
    headerList.stream()
        .forEach(
            headerString -> {
              Header header = request.getHeader(headerString);
              if (header == null) {
                return;
              }
              String headerName = header.getName();
              String headerValue = header.toString().split(headerName + ": ")[1];
              String ipToReplace = getIPToReplace(headerValue);
              if (ipToReplace == null) {
                return;
              }
              headerValue = headerValue.replaceFirst(ipToReplace, ipAddress);
              try {
                request.setHeader(headerFactory.createHeader(headerName, headerValue));
              } catch (ParseException e) {
                logger.error(
                    "Error while replacingIPHeader normalization in {}: {}", headerName, e);
              }
            });
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
}