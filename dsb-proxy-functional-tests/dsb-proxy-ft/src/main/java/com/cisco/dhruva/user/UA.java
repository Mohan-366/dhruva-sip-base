package com.cisco.dhruva.user;

import com.cisco.dhruva.util.TestMessage;
import java.util.List;

public interface UA {

  public void addTestMessage(TestMessage testMessage);

  public List<TestMessage> getTestMessages();
}
