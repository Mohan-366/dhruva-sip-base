package com.cisco.dsb.util.log;

import static gov.nist.javax.sip.address.AddressImpl.NAME_ADDR;
import static gov.nist.javax.sip.address.AddressImpl.WILD_CARD;

import gov.nist.core.NameValueList;
import gov.nist.core.Separators;
import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.address.TelephoneNumber;
import gov.nist.javax.sip.header.ExtensionHeaderImpl;
import javax.sip.address.URI;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class ObfuscationAspect {
  private static Logger logger = DhruvaLoggerFactory.getLogger(ObfuscationAspect.class);

  private static ThreadLocal<Boolean> obfuscate = ThreadLocal.withInitial(() -> Boolean.FALSE);

  public static void enableObfuscationForThisThread() {
    obfuscate.set(Boolean.TRUE);
  }

  public static void disableObfuscationForThisThread() {
    obfuscate.remove();
  }

  public static boolean isObfuscationEnabledForThisThread() {
    return obfuscate.get();
  }

  /** Obfuscates Addresses (optional display name + SIP URI) within SIP messages. */
  @Around(
      "execution(public StringBuilder gov.nist.javax.sip.address.AddressImpl.encode(StringBuilder)) && args(buffer) && target(addr)")
  public Object obfuscate(ProceedingJoinPoint pjp, AddressImpl addr, StringBuilder buffer)
      throws Throwable {
    if (obfuscate.get()) {
      int addressType = addr.getAddressType();
      String displayName = addr.getDisplayName();
      URI address = addr.getURI();

      if (addressType == WILD_CARD) {
        buffer.append('*');
      } else {
        if (displayName != null) {
          buffer
              .append(Separators.DOUBLE_QUOTE)
              .append(LogUtils.obfuscateEntireString(displayName))
              .append(Separators.DOUBLE_QUOTE)
              .append(Separators.SP);
        }
        if (address != null) {
          if (addressType == NAME_ADDR || displayName != null) {
            buffer.append(Separators.LESS_THAN);
          }
          appendObfuscated(address, buffer);
          if (addressType == NAME_ADDR || displayName != null) {
            buffer.append(Separators.GREATER_THAN);
          }
        }
      }
      return buffer;
    } else {
      return pjp.proceed();
    }
  }

  /** Obfuscates SIP URIs within SIP messages. */
  @Around(
      "execution(public StringBuilder gov.nist.javax.sip.address.SipUri.encode(StringBuilder)) && args(buffer) && target(uri)")
  public Object obfuscate(ProceedingJoinPoint pjp, SipUri uri, StringBuilder buffer)
      throws Throwable {
    if (obfuscate.get()) {
      return appendObfuscated(uri, buffer);
    } else {
      return pjp.proceed();
    }
  }

  /** This is a helper to obfuscate a URI within an aspect. */
  private StringBuilder appendObfuscated(URI uri, StringBuilder buffer) {
    // Since the obfuscation code depends on SipUri.encode(), disable intercepting the call to avoid
    // infinite recursion.
    disableObfuscationForThisThread();
    String obfuscatedUri = LogUtils.obfuscateIdentityURINoFilter(uri);
    enableObfuscationForThisThread();
    return buffer.append("[").append(obfuscatedUri).append("]");
  }

  /** Obfuscates any telephone numbers. This is not expected to be used. */
  @Around(
      "execution(public StringBuilder gov.nist.javax.sip.address.TelephoneNumber.encode(StringBuilder)) && args(buffer) && target(number)")
  public Object obfuscate(ProceedingJoinPoint pjp, TelephoneNumber number, StringBuilder buffer)
      throws Throwable {
    if (obfuscate.get()) {
      boolean isGlobal = number.isGlobal();
      String phoneNumber = number.getPhoneNumber();
      NameValueList parameters = number.getParameters();

      if (isGlobal) {
        buffer.append('+');
      }
      buffer.append(LogUtils.obfuscateEntireString(phoneNumber));
      if (!parameters.isEmpty()) {
        buffer.append(Separators.SEMICOLON);
        parameters.encode(buffer);
      }
      return buffer;
    } else {
      return pjp.proceed();
    }
  }

  /** The purpose of this aspect is to obfuscate unrecognized headers. */
  @Around(
      "execution(public StringBuilder gov.nist.javax.sip.header.ExtensionHeaderImpl.encodeBody(StringBuilder)) && args(buffer) && target(header)")
  public Object obfuscate(ProceedingJoinPoint pjp, ExtensionHeaderImpl header, StringBuilder buffer)
      throws Throwable {
    if (obfuscate.get()) {
      disableObfuscationForThisThread();
      buffer.append(DhruvaStackLogger.obfuscateHeader(header.encodeBody()));
      enableObfuscationForThisThread();
      return buffer;
    } else {
      return pjp.proceed();
    }
  }

  /**
   * For some reason {@link ExtensionHeaderImpl#encode()} doesn't use the default {@link
   * gov.nist.javax.sip.header.SIPHeader} implementation and call {@link
   * ExtensionHeaderImpl#encodeBody(StringBuilder)}, even though it has the same behavior. This
   * changes that so calling encode() directly will cause the above aspect {@link
   * ObfuscationAspect#obfuscate(ProceedingJoinPoint, ExtensionHeaderImpl, StringBuilder)} like
   * expected.
   */
  @Around(
      "execution(public String gov.nist.javax.sip.header.ExtensionHeaderImpl.encode()) && target(header)")
  public Object encode(ProceedingJoinPoint pjp, ExtensionHeaderImpl header) throws Throwable {
    if (obfuscate.get()) {
      return header.encode(new StringBuilder()).toString();
    } else {
      return pjp.proceed();
    }
  }
}
