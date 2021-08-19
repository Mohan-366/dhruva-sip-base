package com.cisco.dsb.common.servergroups;

import com.cisco.dsb.common.transport.Transport;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public abstract class SG {

  public static final String dsSgFailoverResponseTimeout = "dsSgFailoverResponseTimeout";
  public static final int dsSgFailoverResponseTimeoutDefault = 0;
  public static final int dsSgFailoverResponseTimeoutMin = 0;
  public static final String dsSgGlobalSelectionType = "dsSgGlobalSelectionType";
  public static final String dsSgGlobalSelectionTypeDefault = "request-uri";
  public static final String dsSgGlobalSelectionType_request_uri = "request-uri";
  public static final int index_dsSgGlobalSelectionType_request_uri = 0;
  public static final String dsSgGlobalSelectionType_call_id = "call-id";
  public static final int index_dsSgGlobalSelectionType_call_id = 1;
  public static final String dsSgGlobalSelectionType_highest_q = "highest-q";
  public static final int index_dsSgGlobalSelectionType_highest_q = 2;
  public static final String dsSgGlobalSelectionType_to_uri = "to-uri";
  public static final int index_dsSgGlobalSelectionType_to_uri = 3;
  public static final String dsSgPing = "dsSgPing";
  public static final String dsSgPingDefault = "off";
  public static final String dsSgPing_on = "on";
  public static final int index_dsSgPing_on = 0;
  public static final String dsSgPing_off = "off";
  public static final int index_dsSgPing_off = 1;
  public static final String dsSgSgElementStatus = "dsSgSgElementStatus";
  public static final String dsSgFailoverResponse = "dsSgFailoverResponse";
  public static final String sgFailoverResponseCode = "sgFailoverResponseCode";
  public static final int sgFailoverResponseCodeMin = 500;
  public static final int sgFailoverResponseCodeMax = 599;
  public static final String sgFailoverResponseRowStatus = "sgFailoverResponseRowStatus";
  public static final String dsSgPingOptions = "dsSgPingOptions";
  public static final String sgPingOptionsIP = "sgPingOptionsIP";
  public static final String sgPingOptionsPort = "sgPingOptionsPort";
  public static final int sgPingOptionsPortDefault = 4000;
  public static final int sgPingOptionsPortMin = 0;
  public static final int sgPingOptionsPortMax = 65535;
  public static final String sgPingOptionsTimeout = "sgPingOptionsTimeout";
  public static final int sgPingOptionsTimeoutDefault = 500;
  public static final int sgPingOptionsTimeoutMin = 0;
  public static final String sgPingOptionsUpElementInterval = "sgPingOptionsUpElementInterval";
  public static final int sgPingOptionsUpElementIntervalDefault = 0;
  public static final int sgPingOptionsUpElementIntervalMin = 0;
  public static final String sgPingOptionsDownElementInterval = "sgPingOptionsDownElementInterval";
  public static final int sgPingOptionsDownElementIntervalDefault = 50;
  public static final int sgPingOptionsDownElementIntervalMin = 0;
  public static final String sgPingOptionsMethod = "sgPingOptionsMethod";
  public static final String sgPingOptionsMethodDefault = "PING";
  public static final String sgPingOptionsfailResponseCodes = "502,503";
  public static final Boolean sgPingOptionsEnable = true;
  public static final String sgPingOptionsRowStatus = "sgPingOptionsRowStatus";
  public static final String sgPingOptionsRowStatusDefault = "on";
  public static final String sgPingOptionsRowStatus_on = "on";
  public static final int index_sgPingOptionsRowStatus_on = 0;
  public static final String sgPingOptionsRowStatus_off = "off";
  public static final int index_sgPingOptionsRowStatus_off = 1;
  public static final String dsSgSaveServerGroups = "dsSgSaveServerGroups";
  public static final String sgSaveServerGroupsFilename = "sgSaveServerGroupsFilename";
  public static final String sgSaveServerGroupsSG = "sgSaveServerGroupsSG";
  public static final String sgSaveServerGroupsRowStatus = "sgSaveServerGroupsRowStatus";
  public static final String dsSgSg = "dsSgSg";
  public static final String sgSgName = "sgSgName";
  public static final String sgSgLbType = "sgSgLbType";
  public static final String sgSgLbTypeDefault = "global";
  public static final String sgSgLbType_global = "global";
  public static final int index_sgSgLbType_global = 0;
  public static final String sgSgLbType_highest_q = "highest-q";
  public static final int index_sgSgLbType_highest_q = 1;
  public static final String sgSgLbType_request_uri = "request-uri";
  public static final int index_sgSgLbType_request_uri = 2;
  public static final String sgSgLbType_call_id = "call-id";
  public static final int index_sgSgLbType_call_id = 3;
  public static final String sgSgLbType_to_uri = "to-uri";
  public static final int index_sgSgLbType_to_uri = 4;
  public static final String sgSgLbType_weight = "weight";
  public static final int index_sgSgLbType_weight = 5;
  public static final String sgSgLbType_ms_id = "Ms-Conversation-ID";
  public static final int index_sgSgLbType_ms_id = 6;
  public static final String sgSgRowStatus = "sgSgRowStatus";
  public static final String dsSgSgElement = "dsSgSgElement";
  public static final String sgSgElementSgName = "sgSgElementSgName";
  public static final String sgSgElementSgReference = "sgSgElementSgReference";
  public static final String sgSgElementHost = "sgSgElementHost";
  public static final String sgSgElementPort = "sgSgElementPort";
  public static final int sgSgElementPortDefault = 5060;
  public static final int sgSgElementPortMin = 0;
  public static final int sgSgElementPortMax = 65535;
  public static final String sgSgElementTransport = "sgSgElementTransport";
  public static final String sgSgElementTransportDefault = "UDP";
  public static final String sgSgElementTransport_UDP = "UDP";
  public static final int index_sgSgElementTransport_UDP = Transport.UDP.getValue();
  public static final String sgSgElementTransport_TCP = "TCP";
  public static final int index_sgSgElementTransport_TCP = Transport.TCP.getValue();
  public static final String sgSgElementTransport_TLS = "TLS";
  public static final int index_sgSgElementTransport_TLS = Transport.TLS.getValue();
  public static final String sgSgElementQValue = "sgSgElementQValue";
  public static final float sgSgElementQValueDefault = 1.0f;
  public static final float sgSgElementQValueMin = 0.0f;
  public static final float sgSgElementQValueMax = 1.0f;
  public static final String sgSgElementWeight = "sgSgElementWeight";
  public static final String sgSgElementRowStatus = "sgSgElementRowStatus";
  public static final String dsSgSgElementRetries = "dsSgSgElementRetries";
  public static final String sgSgElementRetriesTransport = "sgSgElementRetriesTransport";
  public static final String sgSgElementRetriesTransportDefault = "UDP";
  public static final String sgSgElementRetriesTransport_UDP = "UDP";
  public static final int index_sgSgElementRetriesTransport_UDP = Transport.UDP.getValue();
  public static final String sgSgElementRetriesTransport_TCP = "TCP";
  public static final int index_sgSgElementRetriesTransport_TCP = Transport.TCP.getValue();
  public static final String sgSgElementRetriesTransport_TLS = "TLS";
  public static final int index_sgSgElementRetriesTransport_TLS = Transport.TLS.getValue();
  public static final String sgSgElementRetries = "sgSgElementRetries";
  public static final int sgSgElementRetriesDefault = 10;
  public static final int sgSgElementRetriesMin = 0;
  public static final int sgSgElementRetriesMax = 65535;
  public static final String sgTrapElementOverloadedClear = "sgTrapElementOverloadedClear";
  public static final String sgElementOverloadedClearIP = "sgElementOverloadedClearIP";
  public static final String sgElementOverloadedClearPort = "sgElementOverloadedClearPort";
  public static final String sgElementOverloadedClearTransport =
      "sgElementOverloadedClearTransport";
  public static final String sgElementOverloadedClearMessage = "sgElementOverloadedClearMessage";
  public static final String sgTrapServerGroupUnreachableClear =
      "sgTrapServerGroupUnreachableClear";
  public static final String sgServerGroupUnreachableClearName =
      "sgServerGroupUnreachableClearName";
  public static final String sgServerGroupUnreachableClearMessage =
      "sgServerGroupUnreachableClearMessage";
  public static final String sgTrapElementUnreachableClear = "sgTrapElementUnreachableClear";
  public static final String sgElementUnreachableClearIP = "sgElementUnreachableClearIP";
  public static final String sgElementUnreachableClearPort = "sgElementUnreachableClearPort";
  public static final String sgElementUnreachableClearTransport =
      "sgElementUnreachableClearTransport";
  public static final String sgElementUnreachableClearMessage = "sgElementUnreachableClearMessage";
  public static final String sgTrapElementUnreachable = "sgTrapElementUnreachable";
  public static final String sgElementUnreachableIP = "sgElementUnreachableIP";
  public static final String sgElementUnreachablePort = "sgElementUnreachablePort";
  public static final String sgElementUnreachableTransport = "sgElementUnreachableTransport";
  public static final String sgElementUnreachableMessage = "sgElementUnreachableMessage";
  public static final String sgTrapElementOverloaded = "sgTrapElementOverloaded";
  public static final String sgElementOverloadedIP = "sgElementOverloadedIP";
  public static final String sgElementOverloadedPort = "sgElementOverloadedPort";
  public static final String sgElementOverloadedTransport = "sgElementOverloadedTransport";
  public static final String sgElementOverloadedMessage = "sgElementOverloadedMessage";
  public static final String sgTrapServerGroupUnreachable = "sgTrapServerGroupUnreachable";
  public static final String sgServerGroupUnreachableName = "sgServerGroupUnreachableName";
  public static final String sgServerGroupUnreachableMessage = "sgServerGroupUnreachableMessage";

  private static final HashSet allStrings = new HashSet(10);

  private static final HashSet versionableParams = new HashSet();

  private static final HashMap validValues_sgPingOptionsRowStatus = new HashMap(2);
  private static final HashMap validValues_sgSgElementTransport = new HashMap(3);
  private static final HashMap validValues_dsSgPing = new HashMap(2);
  private static final HashMap validValues_sgSgElementRetriesTransport = new HashMap(3);
  private static final HashMap validValues_sgSgLbType = new HashMap(5);
  private static final HashMap validValues_dsSgGlobalSelectionType = new HashMap(4);

  private static final HashMap defaults = new HashMap(15);

  private static final HashMap mins = new HashMap(9);

  private static final HashMap maxs = new HashMap(5);

  private static final HashMap validValues = new HashMap();

  static {
    SG.allStrings.add(SG.dsSgFailoverResponseTimeout);
    SG.allStrings.add(SG.dsSgGlobalSelectionType);
    SG.allStrings.add(SG.dsSgPing);
    SG.allStrings.add(SG.dsSgSgElementStatus);
    SG.allStrings.add(SG.dsSgFailoverResponse);
    SG.allStrings.add(SG.sgFailoverResponseCode);
    SG.allStrings.add(SG.sgFailoverResponseRowStatus);
    SG.allStrings.add(SG.dsSgPingOptions);
    SG.allStrings.add(SG.sgPingOptionsIP);
    SG.allStrings.add(SG.sgPingOptionsPort);
    SG.allStrings.add(SG.sgPingOptionsTimeout);
    SG.allStrings.add(SG.sgPingOptionsUpElementInterval);
    SG.allStrings.add(SG.sgPingOptionsDownElementInterval);
    SG.allStrings.add(SG.sgPingOptionsMethod);
    SG.allStrings.add(SG.sgPingOptionsRowStatus);
    SG.allStrings.add(SG.dsSgSaveServerGroups);
    SG.allStrings.add(SG.sgSaveServerGroupsFilename);
    SG.allStrings.add(SG.sgSaveServerGroupsSG);
    SG.allStrings.add(SG.sgSaveServerGroupsRowStatus);
    SG.allStrings.add(SG.dsSgSg);
    SG.allStrings.add(SG.sgSgName);
    SG.allStrings.add(SG.sgSgLbType);
    SG.allStrings.add(SG.sgSgRowStatus);
    SG.allStrings.add(SG.dsSgSgElement);
    SG.allStrings.add(SG.sgSgElementSgName);
    SG.allStrings.add(SG.sgSgElementSgReference);
    SG.allStrings.add(SG.sgSgElementHost);
    SG.allStrings.add(SG.sgSgElementPort);
    SG.allStrings.add(SG.sgSgElementTransport);
    SG.allStrings.add(SG.sgSgElementQValue);
    SG.allStrings.add(SG.sgSgElementRowStatus);
    SG.allStrings.add(SG.dsSgSgElementRetries);
    SG.allStrings.add(SG.sgSgElementRetriesTransport);
    SG.allStrings.add(SG.sgSgElementRetries);

    SG.validValues_sgPingOptionsRowStatus.put(
        SG.sgPingOptionsRowStatus_on, new Integer(SG.index_sgPingOptionsRowStatus_on));
    SG.validValues_sgPingOptionsRowStatus.put(
        SG.sgPingOptionsRowStatus_off, new Integer(SG.index_sgPingOptionsRowStatus_off));
    SG.validValues.put(SG.sgPingOptionsRowStatus, SG.validValues_sgPingOptionsRowStatus);

    SG.validValues_sgSgElementTransport.put(
        SG.sgSgElementTransport_UDP, new Integer(SG.index_sgSgElementTransport_UDP));
    SG.validValues_sgSgElementTransport.put(
        SG.sgSgElementTransport_TCP, new Integer(SG.index_sgSgElementTransport_TCP));
    SG.validValues_sgSgElementTransport.put(
        SG.sgSgElementTransport_TLS, new Integer(SG.index_sgSgElementTransport_TLS));
    SG.validValues.put(SG.sgSgElementTransport, SG.validValues_sgSgElementTransport);

    SG.validValues_dsSgPing.put(SG.dsSgPing_on, new Integer(SG.index_dsSgPing_on));
    SG.validValues_dsSgPing.put(SG.dsSgPing_off, new Integer(SG.index_dsSgPing_off));
    SG.validValues.put(SG.dsSgPing, SG.validValues_dsSgPing);

    SG.validValues_sgSgElementRetriesTransport.put(
        SG.sgSgElementRetriesTransport_UDP, new Integer(SG.index_sgSgElementRetriesTransport_UDP));
    SG.validValues_sgSgElementRetriesTransport.put(
        SG.sgSgElementRetriesTransport_TCP, new Integer(SG.index_sgSgElementRetriesTransport_TCP));
    SG.validValues_sgSgElementRetriesTransport.put(
        SG.sgSgElementRetriesTransport_TLS, new Integer(SG.index_sgSgElementRetriesTransport_TLS));
    SG.validValues.put(SG.sgSgElementRetriesTransport, SG.validValues_sgSgElementRetriesTransport);

    SG.validValues_sgSgLbType.put(SG.sgSgLbType_global, new Integer(SG.index_sgSgLbType_global));
    SG.validValues_sgSgLbType.put(
        SG.sgSgLbType_highest_q, new Integer(SG.index_sgSgLbType_highest_q));
    SG.validValues_sgSgLbType.put(
        SG.sgSgLbType_request_uri, new Integer(SG.index_sgSgLbType_request_uri));
    SG.validValues_sgSgLbType.put(SG.sgSgLbType_call_id, new Integer(SG.index_sgSgLbType_call_id));
    SG.validValues_sgSgLbType.put(SG.sgSgLbType_to_uri, new Integer(SG.index_sgSgLbType_to_uri));
    SG.validValues_sgSgLbType.put(SG.sgSgLbType_weight, new Integer(SG.index_sgSgLbType_weight));
    SG.validValues_sgSgLbType.put(SG.sgSgLbType_ms_id, new Integer(SG.index_sgSgLbType_ms_id));
    SG.validValues.put(SG.sgSgLbType, SG.validValues_sgSgLbType);

    SG.validValues_dsSgGlobalSelectionType.put(
        SG.dsSgGlobalSelectionType_request_uri,
        new Integer(SG.index_dsSgGlobalSelectionType_request_uri));
    SG.validValues_dsSgGlobalSelectionType.put(
        SG.dsSgGlobalSelectionType_call_id, new Integer(SG.index_dsSgGlobalSelectionType_call_id));
    SG.validValues_dsSgGlobalSelectionType.put(
        SG.dsSgGlobalSelectionType_highest_q,
        new Integer(SG.index_dsSgGlobalSelectionType_highest_q));
    SG.validValues_dsSgGlobalSelectionType.put(
        SG.dsSgGlobalSelectionType_to_uri, new Integer(SG.index_dsSgGlobalSelectionType_to_uri));
    SG.validValues.put(SG.dsSgGlobalSelectionType, SG.validValues_dsSgGlobalSelectionType);

    SG.defaults.put(SG.sgSgElementRetries, new Integer(SG.sgSgElementRetriesDefault));
    SG.defaults.put(
        SG.sgSgElementRetriesTransport, new String(SG.sgSgElementRetriesTransportDefault));
    SG.defaults.put(SG.sgSgElementPort, new Integer(SG.sgSgElementPortDefault));
    SG.defaults.put(SG.sgPingOptionsMethod, new String(SG.sgPingOptionsMethodDefault));
    SG.defaults.put(SG.sgPingOptionsTimeout, new Integer(SG.sgPingOptionsTimeoutDefault));
    SG.defaults.put(
        SG.sgPingOptionsDownElementInterval,
        new Integer(SG.sgPingOptionsDownElementIntervalDefault));
    SG.defaults.put(
        SG.dsSgFailoverResponseTimeout, new Integer(SG.dsSgFailoverResponseTimeoutDefault));
    SG.defaults.put(SG.sgPingOptionsPort, new Integer(SG.sgPingOptionsPortDefault));
    SG.defaults.put(SG.sgSgElementQValue, new Float(SG.sgSgElementQValueDefault));
    SG.defaults.put(SG.sgPingOptionsRowStatus, new String(SG.sgPingOptionsRowStatusDefault));
    SG.defaults.put(
        SG.sgPingOptionsUpElementInterval, new Integer(SG.sgPingOptionsUpElementIntervalDefault));
    SG.defaults.put(SG.sgSgElementTransport, new String(SG.sgSgElementTransportDefault));
    SG.defaults.put(SG.dsSgPing, new String(SG.dsSgPingDefault));
    SG.defaults.put(SG.dsSgGlobalSelectionType, new String(SG.dsSgGlobalSelectionTypeDefault));
    SG.defaults.put(SG.sgSgLbType, new String(SG.sgSgLbTypeDefault));

    SG.mins.put(SG.sgSgElementRetries, new Integer(SG.sgSgElementRetriesMin));
    SG.mins.put(SG.dsSgFailoverResponseTimeout, new Integer(SG.dsSgFailoverResponseTimeoutMin));
    SG.mins.put(SG.sgSgElementQValue, new Float(SG.sgSgElementQValueMin));
    SG.mins.put(SG.sgPingOptionsPort, new Integer(SG.sgPingOptionsPortMin));
    SG.mins.put(
        SG.sgPingOptionsUpElementInterval, new Integer(SG.sgPingOptionsUpElementIntervalMin));
    SG.mins.put(SG.sgFailoverResponseCode, new Integer(SG.sgFailoverResponseCodeMin));
    SG.mins.put(SG.sgSgElementPort, new Integer(SG.sgSgElementPortMin));
    SG.mins.put(SG.sgPingOptionsTimeout, new Integer(SG.sgPingOptionsTimeoutMin));
    SG.mins.put(
        SG.sgPingOptionsDownElementInterval, new Integer(SG.sgPingOptionsDownElementIntervalMin));

    SG.maxs.put(SG.sgSgElementRetries, new Integer(SG.sgSgElementRetriesMax));
    SG.maxs.put(SG.sgSgElementQValue, new Float(SG.sgSgElementQValueMax));
    SG.maxs.put(SG.sgPingOptionsPort, new Integer(SG.sgPingOptionsPortMax));
    SG.maxs.put(SG.sgFailoverResponseCode, new Integer(SG.sgFailoverResponseCodeMax));
    SG.maxs.put(SG.sgSgElementPort, new Integer(SG.sgSgElementPortMax));
  }

  public static HashSet getAllStrings() {
    return SG.allStrings;
  }

  public static HashSet getVersionableParams() {
    return SG.versionableParams;
  }

  public static Object getDefault(String paramOrColumnName) {
    return SG.defaults.get(paramOrColumnName);
  }

  public static boolean hasDefault(String paramOrColumnName) {
    return SG.defaults.get(paramOrColumnName) != null;
  }

  public static HashMap getDefaults() {
    return SG.defaults;
  }

  public static Number getMin(String paramOrColumnName) {
    return (Number) SG.mins.get(paramOrColumnName);
  }

  public static boolean hasMin(String paramOrColumnName) {
    return SG.mins.get(paramOrColumnName) != null;
  }

  public static HashMap getMins() {
    return SG.mins;
  }

  public static Number getMax(String paramOrColumnName) {
    return (Number) SG.maxs.get(paramOrColumnName);
  }

  public static boolean hasMax(String paramOrColumnName) {
    return SG.maxs.get(paramOrColumnName) != null;
  }

  public static HashMap getMaxs() {
    return SG.maxs;
  }

  public static Set getValidValues(String paramOrColumnName) {
    Set s = null;
    HashMap map = (HashMap) SG.validValues.get(paramOrColumnName);
    if (map != null) s = map.keySet();
    return s;
  }

  public static boolean hasValidValues(String paramOrColumnName) {
    return SG.validValues.get(paramOrColumnName) != null;
  }

  public static HashMap getValidValues() {
    return SG.validValues;
  }

  public static int getValidValueAsInt(String paramOrColumnName, String value) {
    int i = -1;
    HashMap map = (HashMap) SG.validValues.get(paramOrColumnName);
    if (map != null) {
      Integer integer = (Integer) map.get(value);
      if (integer != null) i = integer.intValue();
    }
    return i;
  }

  public static String getValidValueAsString(String paramOrColumnName, int value) {
    String str = null;
    HashMap map = (HashMap) SG.validValues.get(paramOrColumnName);
    if (map != null) {
      for (Iterator i = map.keySet().iterator(); i.hasNext(); ) {
        String key = (String) i.next();
        Integer integer = (Integer) map.get(key);
        if (integer.intValue() == value) {
          str = key;
          break;
        }
      }
    }
    return str;
  }
}
