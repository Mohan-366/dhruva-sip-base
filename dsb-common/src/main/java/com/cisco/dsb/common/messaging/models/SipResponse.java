package com.cisco.dsb.common.messaging.models;

import javax.sip.ClientTransaction;

public interface SipResponse extends SipEvent {

  ClientTransaction getClientTransaction();
}
