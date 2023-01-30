package com.cisco.dhruva.application.filters;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FilterFactory {
  private NetworkB2B networkB2B;
  private NetworkPSTN networkPSTN;
  private NetworkWxC networkWxC;
  private CallTypeDialOutTagOrMidDialogDialOut callTypeDialOutTagOrMidDialogDialOut;
  private CallTypeDialInTagOrMidDialogDialIn callTypeDialInTagOrMidDialogDialIn;

  @Autowired
  public void setNetworkB2B(NetworkB2B networkB2B) {
    this.networkB2B = networkB2B;
  }

  @Autowired
  public void setNetworkPSTN(NetworkPSTN networkPSTN) {
    this.networkPSTN = networkPSTN;
  }

  @Autowired
  public void setNetworkWxC(NetworkWxC networkWxC) {
    this.networkWxC = networkWxC;
  }

  @Autowired
  public void setCallTypeDialOutTagOrMidDialogDialOut(
      CallTypeDialOutTagOrMidDialogDialOut callTypeDialOutTagOrMidDialogDialOut) {
    this.callTypeDialOutTagOrMidDialogDialOut = callTypeDialOutTagOrMidDialogDialOut;
  }

  @Autowired
  public void setCallTypeDialInTagOrMidDialogDialIn(
      CallTypeDialInTagOrMidDialogDialIn callTypeDialInTagOrMidDialogDialIn) {
    this.callTypeDialInTagOrMidDialogDialIn = callTypeDialInTagOrMidDialogDialIn;
  }

  protected FilterNode getFilterNode(FilterId.Id id) {
    switch (id) {
      case ROOT:
        return new RootNode();
      case NETWORK_B2B:
        return networkB2B;
      case NETWORK_PSTN:
        return networkPSTN;
      case NETWORK_WXC:
        return networkWxC;
      case CALLTYPE_DIAL_IN_OR_MID_DIALOG_DIAL_IN:
        return callTypeDialInTagOrMidDialogDialIn;
      case CALLTYPE_DIAL_OUT_OR_MID_DIALOG_DIAL_OUT:
        return callTypeDialOutTagOrMidDialogDialOut;
      default:
        return null;
    }
  }
}
