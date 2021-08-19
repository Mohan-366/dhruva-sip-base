package com.cisco.dsb.common.ua;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sip.header.Header;
import javax.sip.message.Message;
import org.apache.commons.lang3.builder.EqualsBuilder;

/**
 * Handles all processing associated with the Session-ID header found in SIP messages.
 *
 * <p>Stores and processes the SIP Session-ID header contents. Latest specification is
 * https://datatracker.ietf.org/doc/rfc7989 Draft
 * https://tools.ietf.org/html/draft-ietf-insipid-session-id-14 Replaces
 * https://tools.ietf.org/html/rfc7329
 *
 * <p>This header can be sent in incoming requests by any upstream SIP network element and is
 * included in the SIP messages for all outbound calls. Allows other code to create, compare, test,
 * and update <code>SessionId</code> objects. Also includes methods to aid in building a Session-ID
 * header in an outbound SIP message.
 *
 * <p>The Session-ID header consists of two UUIDs, "local" and "remote". Each UUID represents one
 * end of the session.
 *
 * <p>By convention, the local and remote halves of the SipCall.SessionId member are:
 *
 * <p>local = Locus' session ID (UUID) AKA correlation ID in Spark-land remote = SIP client's UUID
 *
 * <p>The reason why the local is always Locus and not a Spark client is explained below (the "MCU"
 * model).
 *
 * <p>Since you always put your UUID as the "local" in the header and the other guy's as the
 * "remote" in SIP messages that you send, there are lots of helper functions below relating to
 * flipping an incoming Session-Id so it's ordered from L2Sip's perspective.
 *
 * <p>The UUID's are formatted as 32-char strings of lower-case hex digits. From RFC:<br>
 * sess-uuid = 32(DIGIT / %x61-66) ;32 chars of [0-9a-f]
 *
 * <p>The "nil" value "000000000000000000000000" means the UUID is unknown.
 *
 * <p>The format of the header in draft-ietf-insipid-session-id-14 is:<br>
 * &nbsp;&nbsp;"Session-ID: <<32-digit local session ID>;remote=<<32-digit remote session ID>"<br>
 * That draft standard defines some rules for interworking with the earlier standard, RFC 7329.
 * Those older rules are not supported, may never be. The format of the header in RFC 7329 is:<br>
 * &nbsp;&nbsp;"Session-ID: <<32-digit session ID>"
 *
 * <p>Spark uses the Multipoint Control Unit (MCU) model as defined in the RFC 7989, which is
 * consistent with the concept of the locus (roster) being the meeting hub, even for 1-1 calls. So
 * Locus is always one end of the session and the SIP client the other. Spark services call these
 * IDs Correlation ID, but that follows the SIP session ID model. For each call leg, the Spark
 * client has a Correlation ID and the locus has its own correlation ID. * In a 3+-party meeting,
 * all of the clients will be associated with a common locus, and thus a common locus
 * session/correlation ID. * In a 1-1 call, the locus session/correlation ID is still in the middle.
 *
 * <p>L2sip interacts with the SIP client on behalf of the locus. L2sip has code to handle both the
 * SIP Session-ID when interacting with the SIP side and the Spark/Locus correlation ID.
 *
 * <p>L2sip functions as an "intermediary" as defined by the RFC section 7. This influences L2sip
 * behavior when the SIP client does not send Session-ID or sends Session-ID having a nil remote
 * ("000000000000000000000000"). In this case, L2sip concocts a remote session/correlation ID to
 * give to Locus. This gives Spark's internal metrics some unique ID to latch onto. L2sip must track
 * (in the SipCall object) when it does this since L2sip is not allowed to send that session ID back
 * to the SIP side according to the RFC.
 *
 * <p>Note that the code should not capture Session-Id when receiving CANCEL (CancelHandler.java).
 * The reasons are explained in separate places in the RFC.
 *
 * <p>TODO SESSIONID Do not accept remote UUID in failure response or ACK to an error response From
 * RFC:
 *
 * <p>o If an endpoint or intermediary sends a successful (2xx) or redirection (3xx) response to the
 * request containing the new UUID value, the endpoint or intermediary MUST accept the peer's UUID
 * and include this new UUID as the "remote" parameter for any subsequent messages unless the UUID
 * from a subsequent transaction has already been accepted. The one exception is a CANCEL request,
 * as outlined below.
 *
 * <p>o If the endpoint or intermediary sends a failure (4xx, 5xx, or 6xx) response, it MUST NOT
 * accept the new UUID value and any subsequent messages MUST contain the previously stored UUID
 * value in the "remote" parameter for any subsequent message. Note that the failure response itself
 * will contain the new UUID value from the request in the "remote" parameter.
 *
 * <p>o When an endpoint or intermediary receives an ACK for a successful (2xx) or redirection (3xx)
 * response with a new UUID value, it MUST accept the peer's new UUID value and include this new
 * UUID as the "remote" parameter for any subsequent messages. If the ACK is for a failure (4xx,
 * 5xx, or 6xx) response, the new value MUST NOT be used.
 *
 * <p>(the wording of the first two is weird since they say "sends". Checked with Paul G and they
 * both refer to the behavior of the receiving client)
 *
 * <p>TODO SESSIONID If send CANCEL, Session-Id header must be same as (re)INVITE that it cancels.
 * This means we can't unconditionally accept a new remote UUID in provisional response. Also, if
 * locus migrated as a result of provisional response (results in new Locus session ID), we still
 * have to remember our old local session ID. From RFC (I believe we are currently not doing this):
 * The Session-ID header field value included in a CANCEL request MUST be identical to the
 * Session-ID header field value included in the corresponding request being cancelled.
 *
 * <p>TODO SESSIONID Do not store the remote UUID when we receive CANCEL From RFC As stated in
 * Sections 6 and 7, the Session-ID header field value included in a CANCEL request MUST be
 * identical to the Session-ID header field value included in the corresponding INVITE request. Upon
 * receiving a CANCEL request, an endpoint or intermediary would normally send a 487 Request
 * Terminated response (see Section 15.1.2 of [RFC3261]) which, by the rules outlined above, would
 * result in the endpoint or intermediary not storing any UUID value contained in the CANCEL
 * request. Section 3.8 of [RFC6141] specifies conditions where a CANCEL request can result in a 2xx
 * response. Because a CANCEL request is not passed end-to-end and will always contain the UUID from
 * the original INVITE request, retaining a new UUID value received in a CANCEL request may result
 * in inconsistency with the Session-ID value stored on the endpoints and intermediaries involved in
 * the session. To avoid this situation, an endpoint or intermediary MUST NOT accept the new UUID
 * value received in a CANCEL request and any subsequent messages MUST contain the previously stored
 * UUID value in the "remote" parameter". Note that the response to the CANCEL request will contain
 * the UUID value from the CANCEL request in the "remote" parameter.
 *
 * <p>TODO SESSIONID Handle 3xx response, do not use prev SIP/remote UUID in new INVITE From RFC: If
 * an endpoint receives a 3xx message, a REFER that directs the endpoint to a different peer, or an
 * INVITE request with Replaces that also potentially results in communicating with a new peer, the
 * endpoint MUST complete any message exchanges with its current peer using the existing session
 * identifier, but it MUST NOT use the current peer's UUID value when sending the first message to
 * what it believes may be a new peer endpoint (even if the exchange results in communicating with
 * the same physical or logical entity). The endpoint MUST retain its own UUID value, however, as
 * described above.
 */
public class SessionId {
  // private static Logger logger = L2SipLogger.getLogger(SessionId.class);

  public static final String HEADER_NAME = "Session-ID";
  public static final String EMPTY_SESSION_ID = "00000000000000000000000000000000";
  private static final Pattern SESSION_ID_PATTERN = Pattern.compile("[0-9a-f]{32}");

  @JsonProperty protected final String localSessionId;
  @JsonProperty protected final String remoteSessionId;

  // For use when deserializing from JSON
  @JsonCreator
  private SessionId() {
    localSessionId = null;
    remoteSessionId = null;
  }

  private SessionId(String localSessionId, String remoteSessionId) {
    if (localSessionId == null) {
      throw new IllegalArgumentException("localSessionId null");
    }
    if (remoteSessionId == null) {
      throw new IllegalArgumentException("localSessionId null");
    }

    // TODO SESSIONID Eventually turn these into assertions. But first want to be somewhat sure
    // we are clean, since this is not a big enough problem to drop a call.
    if (!validateUUID(localSessionId)) {
      // logger.warn("Invalid SessionId, local={}", localSessionId);
    }
    if (!validateUUID(remoteSessionId)) {
      // logger.warn("Invalid SessionId, remote={}", remoteSessionId);
    }

    this.localSessionId = localSessionId;
    this.remoteSessionId = remoteSessionId;
  }

  public static SessionId createWithRandomLocalUUID() {
    return new SessionId(createSessionUUID(), EMPTY_SESSION_ID);
  }

  public static SessionId createNilNil() {
    return new SessionId(EMPTY_SESSION_ID, EMPTY_SESSION_ID);
  }

  public static SessionId createWithLocalUUID(String local) {
    // TODO SESSIONID validate format. Typically this will come from Locus.
    return new SessionId(local, EMPTY_SESSION_ID);
  }

  public static SessionId createWithRemoteUUID(String remote) {
    return new SessionId(EMPTY_SESSION_ID, remote);
  }

  public static boolean validateUUID(String uuid) {
    if (uuid == null) return false;
    return SESSION_ID_PATTERN.matcher(uuid).matches();
  }
  /**
   * Tests whether the value matches the "nil" string defined in rfc7989
   *
   * @return <code>true</code> if the uuid string matches the defined nil string, <code>false</code>
   *     otherwise
   */
  public static boolean isNilUuid(String uuid) {
    return uuid.matches(EMPTY_SESSION_ID);
  }

  /**
   * @param local - Local UUID (in sessionID format) to use
   * @param copyRemoteFromSessionId - Session ID from which to grab the remote UUID. If null, use
   *     remote=nil.
   * @return SessionId - Never null
   */
  public static SessionId replaceLocalUUIDOnSessionId(
      String local, SessionId copyRemoteFromSessionId) {
    SessionId sessionId = null;
    if (copyRemoteFromSessionId == null) {
      sessionId = createWithLocalUUID(local); // remote will be nil UUID
    } else {
      sessionId = new SessionId(local, copyRemoteFromSessionId.getRemoteSessionId());
    }
    return sessionId;
  }

  /**
   * Extract Session-Id from SIP event (request), flip so it's from l2sip's perspective.
   *
   * @param sipMessage the SIP message
   * @return the object
   */
  public static SessionId extractFromSipEventAndFlip(Message sipMessage) {
    SessionId sessionId = extractFromSipEvent(sipMessage);
    if (sessionId != null) {
      sessionId = sessionId.flip();
    }
    return sessionId;
  }

  /**
   * Extract Session-Id from SIP message.
   *
   * @param sipMessage the SIP message
   * @return the object
   */
  public static SessionId extractFromSipEvent(Message sipMessage) {

    if (sipMessage == null) {
      throw new IllegalArgumentException("Invalid null value for SIP message");
    }
    return extractFromHeader(sipMessage.getHeader(HEADER_NAME));
  }

  public static SessionId extractFromHeader(Header sipSessionIdHeader) {
    if (sipSessionIdHeader == null) {
      return null;
    }

    // Get rid of the header name then split value into local and remote components.
    String[] sipSessionIdHeaderComponents = sipSessionIdHeader.toString().split(":");
    if (sipSessionIdHeaderComponents.length != 2) {
      // Malformed Session-ID header, return
      // logger.warn("Received malformed Session-ID header={}. Ignore header",
      // sipSessionIdHeader.toString());
      return null;
    }
    String sipSessionIdHeaderValue = sipSessionIdHeaderComponents[1].trim();
    // logger.info("Message contains Session-ID header value=" + sipSessionIdHeaderValue);
    String[] sipSessionIds = sipSessionIdHeaderValue.split(";remote=");
    if (sipSessionIds.length != 2) {
      // Malformed Session-ID header value, return
      // logger.warn("Received malformed Session-ID header={}. Ignore header",
      // sipSessionIdHeader.toString());
      return null;
    }

    // From RFC:
    // If an endpoint receives a SIP response with a non-nil "local-uuid"
    // that is not 32 octets long, this response comes from a misbehaving
    // implementation, and its Session-ID header field MUST be discarded.
    String local = sipSessionIds[0].trim();
    String remote = sipSessionIds[1].trim();
    if (!validateUUID(local) || !validateUUID(remote)) {
      // logger.warn("Received malformed Session-ID header={}. Ignore header.",
      // sipSessionIdHeader.toString());
      return null;
    }

    SessionId sessionId = new SessionId(local, remote);

    return sessionId;
  }

  /**
   * Update this object's remote session ID based on the Session-ID header received from the remote
   * end
   *
   * @param receivedSessionId the <code>SessionId</code> object that represents the Session-ID
   *     header in the received message
   */
  public SessionId updateRemoteFromReceivedSessionId(SessionId receivedSessionId) {
    if (receivedSessionId == null) {
      // Didn't receive a Session-ID header, so nothing to do
      return this;
    }
    // Previous editions of this method used to discard the receivedSessionId if its remote UUID
    // (which should have come from l2sip) does not match our current local UUID.
    // I'm not sure that's a valid requirement in all cases. For example, if there's a Locus
    // migration
    // then our local UUID will change. Depending on timing of SIP exchange between l2sip and client
    // we may receive a message with our old locus session ID in it right after the migration
    // occurs.
    // So just accept what the other guys says their UUID is. That seems to be the spirit of the
    // RFC.
    return new SessionId(this.localSessionId, receivedSessionId.localSessionId);
  }

  /**
   * Reverse the order of the local and remote values. Typically use this when receiving an incoming
   * message to convert into Spark/L2sip point-of-view.
   */
  public SessionId flip() {
    String local = this.remoteSessionId;
    String remote = this.localSessionId;
    return new SessionId(local, remote);
  }

  /**
   * Create a UUID that conforms to the rules in draft-ietf-insipid-session-id-14
   *
   * @return the UUID
   */
  public static String createSessionUUID() {
    return UUID.randomUUID().toString().replace("-", "").toLowerCase();
  }

  /**
   * Gets the value we should send in the SIP header
   *
   * @return the header value
   */
  public String getHeaderValue() {
    StringBuilder headerVal = new StringBuilder();
    headerVal.append(localSessionId);
    headerVal.append(";remote=");
    headerVal.append(remoteSessionId);
    return headerVal.toString();
  }

  public String getLocalSessionId() {
    return localSessionId;
  }

  public String getRemoteSessionId() {
    return remoteSessionId;
  }

  @Override
  public String toString() {
    return "local=[" + localSessionId + "] remote=[" + remoteSessionId + "]";
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || other.getClass() != this.getClass()) {
      return false;
    }

    SessionId rhs = (SessionId) other;
    return new EqualsBuilder()
        .append(this.localSessionId, rhs.localSessionId)
        .append(this.remoteSessionId, rhs.remoteSessionId)
        .isEquals();
  }

  @Override
  public int hashCode() {

    return java.util.Objects.hash(localSessionId, remoteSessionId);
  }
}
