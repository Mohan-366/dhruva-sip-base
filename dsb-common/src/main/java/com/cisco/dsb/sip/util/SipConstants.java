package com.cisco.dsb.sip.util;

import com.google.common.net.InternetDomainName;
import gov.nist.core.NameValue;

/**
 * Constants used in SIP messages
 *
 * <p>NOTE: ----- Following constants which sounded specific to l2sip are not included here:
 *
 * <p>- L2SIP_IT_HURON_DOMAIN - L2SIP_INTEGRATION_TEST_DOMAINS - SPARK_SIP_DOMAINS -
 * NON_CMR_WEBEX_DOMAINS - FORCE_PROXY_PARAMETER - HEADER_DROP_THIS_MSG - CANARY_OPTS_PARAMETER -
 * REASSIGNED_IS_CANARY - FORWARDER_EXECUTOR_NAME - FORWARDER_EXECUTOR_SIP - HURON_CERT_NAMES -
 * WEBEX_CAUSE_806 - WEBEX_CAUSE_821 - WEBEX_REASON_DUPLICATECALL - SESSION_INTERVAL_TOO_SMALL -
 * HURON_LOOPBACK_FLAG - HURON_SOURCE_ROUTING_FLAG - Testname_Header_Name
 *
 * <p>- ReasonProtocol
 *
 * <p>Static inner class -> RFC3261, Token moved to separate file 'TokenExt'
 */
public class SipConstants {

  public static final String CALLTYPE_PARAM = "call-type";
  public static final String CallId_Parameter = "call-id";
  public static final String CALL_INFO_HEADER_NAME = "Call-Info";

  // IPV4 Loopback Address
  public static final String Ipv4_Loopback = "127.0.0.1";
  // The LoopBack* variables are used to detect SIP loopback. Deprecated versions.
  // 2020-10-05 Webex/CP still looking for this (WEBEX-132186 and INC0038308)
  public static final String Old_Loopback_Parameter = CALLTYPE_PARAM;
  public static final String Old_Loopback_Parameter_Value = "squared";
  public static final String Old_Loopback_Parameter_Test_Value = "squared-test";

  // The LoopBack* variables are used to detect SIP loopback
  public static final String Loopback_Parameter = "x-cisco-svc-type";
  public static final String Loopback_Parameter_Value = "spark";
  public static final String Loopback_Parameter_Test_Value = "spark-test";

  public static final NameValue Old_Loopback =
      new NameValue(Old_Loopback_Parameter, Old_Loopback_Parameter_Value);
  public static final NameValue Loopback =
      new NameValue(Loopback_Parameter, Loopback_Parameter_Value);
  public static final String Loopback_Parameter_SquaredUC = "squared-uc-only";

  // sip.webex.com is just a temporary placeholder until we find out what CP's actual subject
  // name/SANs are.
  public static final String Cloud_Proxy_SAN = "sip.webex.com";

  /** Note: that all headers sent from Cloud Proxy will begin with "x-cisco" * */

  /**
   * List of SAN's obtained from the MTLS cert terminated by Cloud Proxy. This header is always sent
   * in OCP environments over port 5062.
   */
  public static final String X_Cisco_Peer_Cert_Info = "x-cisco-peer-cert-info";

  /**
   * Cloud Proxy will send "x-cisco-crid" in SIP Request URI when they send the
   * "client.call.initiated" event to Call Analyzer. If we see this, we should: (1) Not send
   * "client.call.initiated" event (2) Use the x-cisco-crid value as the correlation id
   */
  public static final String X_Cisco_Crid = "x-cisco-crid";

  public static final String X_Cisco_Ivrid = "x-cisco-ivrid";

  public static final String X_Cisco_Call_Type = "x-cisco-call-type";

  public static final String X_Cisco_Assert_User = "x-cisco-assert-user";

  public static final String X_Cisco_Conf_Create = "x-cisco-conf-create";

  public static final String X_Cisco_Conf_Id = "x-cisco-conf-id";

  public static final String Cisco_Media_Playback = "cisco-media-playback";
  public static final String X_Cisco_Media_Playback = "x-cisco-media-playback";

  public static final String X_Cisco_Number = "x-cisco-number";

  public static final String X_Cisco_Tenant = "x-cisco-tenant";

  public static final String X_Cisco_Identity = "X-Cisco-Identity";

  public static final String X_Cisco_Srtp_Fallback = "x-cisco-srtp-fallback";

  /** Added by Cloud Proxy, contains GeoIP and GeoDNS data */
  public static final String X_Cisco_Geo_Location_info = "X-Cisco-Geo-Location-Info";

  public static final String Client_Region_Code = "clientRegionCode";
  public static final String Client_Country_Code = "clientCountryCode";
  public static final String Sip_Cloud_Ingress_DC = "sipCloudIngressDC";
  public static final String Geo_Dns_Dialled = "geoDNSDialled";

  public static final String X_Cisco_Locale = "x-cisco-locale";

  public static final String X_Cisco_Remotecc_URI = "urn:x-cisco-remotecc:callinfo";

  public static final String Locus_Header_Name = "Locus";
  public static final String LocusType_Header_Name = "Locus-Type";

  public static final String WebexMeetingNumber_Header_Name = "WebexMeetingNumber";
  public static final String WebexConferenceId_Header_Name = "WebexConferenceId";

  public static final String Content_Type_Application = "application";
  public static final String Content_Type_Message = "message";

  public static final String Rpid_Header_Name = "Remote-Party-ID";
  public static final String Rpid_Header_Name_With_Colon = "Remote-Party-ID:";

  public static final String Paid_Header_Name = "P-Asserted-Identity";

  public static final String Ppi_Header_Name = "P-Preferred-Identity";

  public static final String ISFOCUS_PARAMETER = "isfocus";

  public static final String XML = "xml";

  public static final String Kpml_Event_Type = "kpml";

  public static final String Timer_Option_Tag = "timer";

  public static final String UAC = "uac";
  public static final String UAS = "uas";

  public static final String REST_FLAG = "+rest";

  public static final String LYNC_CALL = "lync";

  public static final String HUNT_PILOT_URI = "huntpiloturi";

  public static final String SEPARATE_TLS_CONNECTION_PER_CALL = "Separate-TLS-Connection-Per-Call";

  public static final NameValue PHONE_USER_PARAMETER = new NameValue("user", "phone");

  public static final InternetDomainName WEBEX_TOP_DOMAIN = InternetDomainName.from("webex.com");

  public static final String NOMAD_ALLOC_ID = "NOMAD_ALLOC_ID"; //  name of environment variable

  // old ciscospark branded domains
  public static final InternetDomainName SPARK_CLOUD_DOMAIN =
      InternetDomainName.from("ciscospark.com");
  public static final InternetDomainName WBX2_SPARK_CLOUD_DOMAIN =
      InternetDomainName.from("wbx2.com");
  public static final InternetDomainName SPARK_CLOUD_URI_USER_DOMAIN =
      InternetDomainName.from("call.ciscospark.com");
  public static final InternetDomainName SPARK_CLOUD_URI_ROOM_DOMAIN =
      InternetDomainName.from("room.ciscospark.com");
  public static final InternetDomainName SPARK_MEETING_DOMAIN =
      InternetDomainName.from("meet.ciscospark.com");

  // integration environment wbx2.com cloud calling domains
  public static final InternetDomainName LEGACY_WBX2_CLOUD_URI_USER_DOMAIN =
      InternetDomainName.from("call.wbx2.com");
  public static final InternetDomainName LEGACY_WBX2_CLOUD_URI_ROOM_DOMAIN =
      InternetDomainName.from("room.wbx2.com");

  public static final InternetDomainName WBX2_CLOUD_URI_USER_DOMAIN =
      InternetDomainName.from("calls.wbx2.com");
  public static final InternetDomainName WBX2_CLOUD_URI_ROOM_DOMAIN =
      InternetDomainName.from("rooms.wbx2.com");

  // new webex teams branded domains
  public static final InternetDomainName WEBEX_CLOUD_URI_USER_DOMAIN =
      InternetDomainName.from("calls.webex.com");
  public static final InternetDomainName WEBEX_CLOUD_URI_ROOM_DOMAIN =
      InternetDomainName.from("rooms.webex.com");
  public static final InternetDomainName WEBEX_MEETUP_DOMAIN =
      InternetDomainName.from("meetup.webex.com");
  public static final InternetDomainName WEBEX_SIP_DOMAIN =
      InternetDomainName.from("sip.webex.com");
  public static final InternetDomainName WEBEX_CALLSERVICE_DOMAIN =
      InternetDomainName.from("callservice.webex.com");

  public enum ContentSubType {
    Sdp("sdp"),
    Media_Control_Xml("media_control+" + XML),
    Cisco_Conf_Config_Xml("cisco-conf-config+" + XML),
    Cisco_Media_Playback_Xml(Cisco_Media_Playback + "+" + XML),
    SipFrag("sipfrag"),
    Kpml_Request("kpml-request+" + XML),
    Kpml_Response("kpml-response+" + XML);

    private final String value;

    ContentSubType(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }
  }
}
