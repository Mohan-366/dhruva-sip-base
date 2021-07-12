package com.cisco.dsb.common.messaging.models;

import javax.sip.address.Address;

public abstract class SipEventImpl implements SipEvent {
  protected final String callId;
  private long cSeq;

  public SipEventImpl(String callId, long cSeq) {
    this.callId = callId;
    this.cSeq = cSeq;
  }

  @Override
  public long getCseq() {
    return cSeq;
  }

  @Override
  public Address sipPartyAddress() throws Exception {
    return null;
  }
}
