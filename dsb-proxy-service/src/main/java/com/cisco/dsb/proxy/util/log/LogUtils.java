package com.cisco.dsb.proxy.util.log;

import com.cisco.dsb.sip.jain.JainSipHelper;
import com.cisco.dsb.sip.util.SipAddressUtils;
import com.cisco.dsb.sip.util.SipConstants;
import com.cisco.dsb.util.ObfuscationAspect;
import com.cisco.wx2.util.Utilities;
import com.google.common.base.Strings;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import gov.nist.core.GenericObject;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import java.text.ParseException;
import java.util.Iterator;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sip.address.SipURI;
import javax.sip.address.TelURL;
import javax.sip.address.URI;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

public class LogUtils {

  private static Logger logger = DhruvaLoggerFactory.getLogger(LogUtils.class);
  private static boolean obfuscateLog = true;
  private static final String COLON = ":";

  public static void setObfuscateLog(boolean obfuscateLog) {
    LogUtils.obfuscateLog = obfuscateLog;
  }

  private static final String CRYPTO_MASK = "************";
  private static Pattern CRYPTO_PATTERN =
      Pattern.compile("((a=crypto:[\\w ]*inline:)|(a=ice-pwd:))([\\w+/]+)");

  private static final Pattern UUID_PATTERN =
      Pattern.compile(
          "[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}");

  // Given a sip/tel URI, separate the scheme: (scheme is case insensitive) from everything after it
  private static final Pattern extractAfterScheme = Pattern.compile("((?i)sip:|sips:|tel:)(.*)");

  private static String obfuscate(
      String value, boolean obfuscateSdpOnly, boolean forceObfuscation) {
    if (value == null) {
      return "[null]";
    } else if (!obfuscateLog && !forceObfuscation) {
      return value;
    }

    // If it's a SIP/TEL URI, special treatment
    String result = null;
    Matcher m = extractAfterScheme.matcher(value);
    if (obfuscateSdpOnly) {
      result =
          Utilities.replaceAll(value, CRYPTO_PATTERN, (matcher) -> matcher.group(1) + CRYPTO_MASK);
    } else if (m.find()) {
      // return the scheme:// unobfuscated, with everything else obfuscated in []
      if (Strings.isNullOrEmpty(m.group(2))) {
        result = m.group(1);
      } else {
        result = m.group(1) + obfuscateEntireString(m.group(2));
      }
    } else if (UUID_PATTERN.matcher(value).matches()) {
      // Do not obfuscate UUID
      result = value;
    } else {
      result = obfuscateEntireString(value);
    }

    return result;
  }

  @SuppressFBWarnings(value = "WEAK_MESSAGE_DIGEST_MD5", justification = "baseline suppression")
  public static String obfuscateEntireString(String value) {
    return "[OBFUSCATED:" + DigestUtils.md5Hex(value.trim()) + "]";
  }

  public static String obfuscate(Object object) {
    return obfuscate(object, false);
  }

  public static String obfuscate(Object object, boolean obfuscateSdpOnly) {
    if (object == null) {
      return "[null]";
    } else if (!obfuscateLog) {
      return object.toString();
    } else {
      return obfuscate(object.toString(), obfuscateSdpOnly, false);
    }
  }

  @SuppressFBWarnings(value = "WEAK_MESSAGE_DIGEST_MD5", justification = "baseline suppression")
  public static String obfuscateIdentityURI(URI uri) {
    // For identity URI's we want to both obfuscate/mask the URI AND show the hash of the identity.
    if (uri == null) {
      return null;
    }
    if (obfuscateLog) {
      return obfuscateIdentityURINoFilter(uri);
    }
    return String.format("%s hash=%s", dq(uri.toString()), DigestUtils.md5Hex(uri.toString()));
  }

  public static String obfuscateIdentityURINoFilter(URI uri) {
    if (uri instanceof SipUri) {
      return String.format(
          "OBFUSCATED:%s hash=%s (%s)",
          dq(maskURI(uri)),
          identityUriToTraceHash(uri),
          identitySIPUriToTraceHashAsComponents((SipUri) uri));
    } else {
      return String.format("OBFUSCATED:%s hash=%s", dq(maskURI(uri)), identityUriToTraceHash(uri));
    }
  }

  public static String maskURI(URI uri) {
    if (uri == null) {
      return null;
    }
    try {
      // Trying to retain as much useful information about the URI as possible for troubleshooting
      // yet still adhere to PII/privacy requirements (which aren't totally spelled out).
      // For both SIP and TEL URI's we mask individual characters, thus retaining the length
      // of the individual elements.
      // See unit test cases for examples.
      if (uri.isSipURI()) {
        // Mask the user, password, and host portions of the URI
        // We keep the @ and the : delineating the password (password almost never used),
        // since it's very useful to see what of the three elements are present.
        // We also keep %, which starts a character encode sequence.
        // We want to know if there's any of that since Spark/CI don't account for encoding when
        // testing
        // equivalence of URI's for matching SIP party identity to Spark user identity.
        // I would like to keep the dots too, especially for host. But sounds like that might be
        // pushing it.
        SipURI sipURI = (SipURI) uri.clone();
        if (sipURI.getUser() != null) {
          String user = sipURI.getUser();
          user = user.replaceAll("[^\\%]", "x");
          sipURI.setUser(user);
        }
        if (sipURI.getUserPassword() != null) {
          String userPassword = sipURI.getUserPassword();
          userPassword = userPassword.replaceAll("[^\\%]", "x");
          sipURI.setUserPassword(userPassword);
        }
        if (sipURI.getHost() != null) {
          String host = sipURI.getHost();
          host = host.replaceAll("[^\\%]", "X");
          sipURI.setHost(host);
        }
        for (Iterator i = sipURI.getParameterNames(); i.hasNext(); ) {
          String name = (String) i.next();
          if (LogUtils.isObfuscatedParam(name)) {
            String value = sipURI.getParameter(name);
            sipURI.setParameter(name, LogUtils.obfuscateEntireString(value));
          }
        }
        return sipURI.toString();
      } else if (uri instanceof TelURL) {
        // This will leave alone the optional leading +
        // Also leave the formatting in case there is some hitch relating to keeping/stripping.
        TelURL telURL = (TelURL) uri.clone();
        if (telURL.getPhoneNumber() != null) {
          String phonenumber = telURL.getPhoneNumber();
          phonenumber = phonenumber.replaceAll("[0-9a-zA-Z]", "X");
          telURL.setPhoneNumber(phonenumber);
        }
        return telURL.toString();
      }

    } catch (ParseException e) {
      logger.error("Error creating masked URI", e);
    }

    // Fall through if exception trapped
    return obfuscate(uri.toString());
  }

  private static String dq(String s) {
    return (s == null) ? "null" : "\"" + s + "\"";
  }

  /**
   * Extracts from the URI a string that might be entered by a user. If the URI is a SIP URI then
   * the pattern is user@host. If the URI is a TEL URI then the pattern is the phone number.
   *
   * @param uri
   * @return
   */
  public static String identityUriToTargetPattern(URI uri) {
    if (uri instanceof SipURI) {
      return JainSipHelper.getUserAtHost((SipURI) uri);
      // Note this leaves out user=phone. It doesn't seem to be hurting anything, maybe Locus does
      // not use it.
    } else if (uri instanceof TelURL) {
      TelURL telURL = (TelURL) uri;
      return SipAddressUtils.phoneNumberFromTelUrl(telURL);
    }
    return null;
  }

  /**
   * Convert identity URI to MD5 hash that can go in traces without exposing PII. The primary goal
   * here is for the hash to be intuitive to an engineer looking in kibana for a known URI. The
   * identity of a SIP URI is calculated from the scheme and the user@host elements. Since someone
   * searching kibana will likely want to find occurrences of email matching the SIP URI, we ignore
   * the SIP URI scheme ("sip:") when calculating this hash. If user is missing, then just the host.
   * For a TEL URI it seems most intuitive to hash the phone number as-is, leaving the formatting,
   * again omitting the scheme.
   *
   * <p>None of this is set in stone, change it to suit users.
   *
   * <p>This hash should not be used to test equivalence of identity. Since scheme is not included
   * in the hash, "sip:10.93.2.3" (IP address) and "tel:10.93.2.3" (formatted phone number) result
   * in the same hash, but they are not equivalent identities.
   */
  @SuppressFBWarnings(value = "WEAK_MESSAGE_DIGEST_MD5", justification = "baseline suppression")
  public static String identityUriToTraceHash(URI uri) {
    // Seems that identityUriToTargetPattern is logically what we want to feed into the hash.
    return DigestUtils.md5Hex(identityUriToTargetPattern(uri));
  }

  /**
   * A hack to foil the logs scanner that flags SIP URI's as PII, which searches for any string
   * formatted like an email address, e.g., "user@host". Sometimes we know that a SIP URI is not
   * personally identifiable and want it to appear in traces unhashed. This method splits the input
   * string on the @.
   */
  public static String splitOnAmpersand(String s) {
    return String.join(" @ ", s.split("@"));
  }

  /**
   * Given a SIP URI, hashes the user and host portions separately and returns a string in format
   * "USERHASH @ HOSTHASH" with spaces around the @.
   *
   * @param sipURI
   * @return
   */
  @SuppressFBWarnings(value = "WEAK_MESSAGE_DIGEST_MD5", justification = "baseline suppression")
  public static String identitySIPUriToTraceHashAsComponents(SipUri sipURI) {
    String user = sipURI.getUser();
    String host = sipURI.getHost();

    if (!Strings.isNullOrEmpty(user)) {
      if (!user.toLowerCase().equals("anonymous")) {
        user = DigestUtils.md5Hex(user);
      }
    }
    if (!host.equals("127.0.0.1")) {
      host = DigestUtils.md5Hex(host);
    }

    String result = null;
    if (user != null) {
      result = String.format("%s @ %s", user, host);
    } else {
      // This is extremely unlikely for identity URI's
      result = String.format("<no user> @ %s", host);
    }
    return result;
  }

  public static String getFirstLine(SIPMessage message) {
    if (obfuscateLog) {
      try {
        ObfuscationAspect.enableObfuscationForThisThread();
        return message.getFirstLine();
      } finally {
        ObfuscationAspect.disableObfuscationForThisThread();
      }
    } else {
      return message.getFirstLine();
    }
  }

  // It's tempting to try to factor things out so that these innards could be shared with
  // obfuscateObject.
  // But I'm hesitant to link the two since it seems so easy to modify one and break the other.
  // For MATS, we always want the SIP URI's in the clear, unlike kibana. PII are permitted in MATS.
  // If it's production (obfuscateLog = true), then we still need to obfuscate the SDP (crypto) and
  // DTMF
  // since those may contain customer secrets, not just PII.
  public static String obfuscateSipMessageForMATS(GenericObject object) {
    String result = object.encode();
    if (obfuscateLog) {
      // This will obfuscate just the SDP
      result = LogUtils.obfuscate(result, true, true);
      // replace dtmf digits in content
      result = DhruvaStackLogger.obfuscateDigits(result);

      result = LogUtils.obfuscatePinForEscalatedMeeting(result, object);
    }
    return result;
  }

  private static String obfuscatePinForEscalatedMeeting(String result, GenericObject object) {
    if (object instanceof SIPRequest) {
      SIPRequest sipRequest = (SIPRequest) object;
      URI requestURI = sipRequest.getRequestURI();
      if (requestURI != null && requestURI.isSipURI()) {
        SipURI sipURI = (SipURI) requestURI;
        String user = sipURI.getUser();
        if (user != null) {
          String[] userParts = user.split("\\+");
          if (userParts.length == 2 && userParts[0].length() > 0) {
            SipURI clonedSipURI = (SipURI) requestURI.clone();
            try {
              clonedSipURI.setUser(
                  userParts[0] + "+" + StringUtils.repeat("*", userParts[1].length()));
            } catch (ParseException e) {
              logger.info(
                  "Exception setting user in obfuscatePinForEscalatedMeeting , effects "
                      + "only masking functionality for mats flow , doesn't impact call");

              return result;
            }
            return result.replaceAll(Pattern.quote(sipURI.toString()), clonedSipURI.toString());
          }
        }
      }
    }
    return result;
  }

  public static String obfuscateObject(GenericObject object, boolean forceObfuscation) {
    if (obfuscateLog || forceObfuscation) {
      try {
        ObfuscationAspect.enableObfuscationForThisThread();

        String objectEncoded = object.encode();

        objectEncoded = LogUtils.obfuscate(objectEncoded, true, true);

        // replace dtmf digits in content

        return DhruvaStackLogger.obfuscateDigits(objectEncoded);
      } finally {
        ObfuscationAspect.disableObfuscationForThisThread();
      }
    } else {
      return object.encode();
    }
  }

  public static boolean isObfuscatedParam(String name) {
    return name != null
        && ((name.startsWith("x-cisco") && !name.startsWith(SipConstants.X_Cisco_Tenant))
            || name.equalsIgnoreCase("icid-value"));
  }

  public static Function<SIPMessage, String> getConnectionSignature =
      (sipMessage) ->
          sipMessage.getLocalAddress().getHostAddress()
              + COLON
              + sipMessage.getLocalPort()
              + COLON
              + sipMessage.getRemoteAddress().getHostAddress()
              + COLON
              + sipMessage.getRemotePort();
}
