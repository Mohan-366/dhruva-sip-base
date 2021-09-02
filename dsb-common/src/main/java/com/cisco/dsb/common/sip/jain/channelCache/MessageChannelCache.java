package com.cisco.dsb.common.sip.jain.channelCache;

import gov.nist.javax.sip.stack.ConnectionOrientedMessageChannel;
import java.util.Collection;

/**
 * MessageChannelCache allows message processors to expose their underlying cache of message
 * channels in a standard way.
 *
 * <p>Consumed by KeepAliveTimerTask.
 */
public interface MessageChannelCache {
  Collection<ConnectionOrientedMessageChannel> getOutgoingMessageChannels();

  Collection<ConnectionOrientedMessageChannel> getIncomingMessageChannels();

  String getStackName();
}
