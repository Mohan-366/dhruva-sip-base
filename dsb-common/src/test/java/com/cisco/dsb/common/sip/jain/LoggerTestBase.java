package com.cisco.dsb.common.sip.jain;

import static org.mockito.Mockito.*;

import gov.nist.core.ServerLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.message.SIPMessage;
import java.util.Calendar;
import org.mockito.ArgumentCaptor;
import org.testng.Assert;

public abstract class LoggerTestBase {

  private final ServerLogger serverLogger;

  public LoggerTestBase(ServerLogger serverLogger) {
    this.serverLogger = serverLogger;
  }

  @SuppressWarnings("deprecation")
  protected void runLoggingTest(SIPMessage sipMessage, boolean expectContent) {
    SipStackImpl sipStack = mock(SipStackImpl.class);
    StackLogger stackLogger = mock(StackLogger.class);
    when(sipStack.getStackLogger()).thenReturn(stackLogger);
    serverLogger.setSipStack(sipStack);

    serverLogger.logMessage(
        sipMessage,
        "fromAddress",
        "toAddress",
        "status",
        false,
        Calendar.getInstance().getTimeInMillis());

    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);

    if (expectContent || !(serverLogger instanceof DsbHeaderLogger)) {
      verify(stackLogger).logInfo(argumentCaptor.capture());

      Assert.assertEquals(argumentCaptor.getAllValues().size(), 1);
      Assert.assertEquals(argumentCaptor.getAllValues().get(0).contains("<![CDATA"), expectContent);
    } else {
      verify(stackLogger).logInfo(argumentCaptor.capture());
      Assert.assertEquals(argumentCaptor.getAllValues().size(), 1);
    }
  }
}
