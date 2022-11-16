package gov.nist.javax.sip.stack;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import gov.nist.javax.sip.SIPConstants;
import gov.nist.javax.sip.SipStackImpl;
import java.util.concurrent.ConcurrentHashMap;
import javax.sip.message.Request;
import lombok.CustomLog;
import lombok.NonNull;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
@CustomLog
public class FixTransactionTimeOut {

  private static final ConcurrentHashMap<String, Integer> transactionTimeout =
      new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, Integer> pingTimeout = new ConcurrentHashMap<>();

  @Around(
      "execution(public void gov.nist.javax.sip.stack.SIPClientTransactionImpl.startTransactionTimer())"
          + "&& target(clientTransaction)")
  public void startTransactionTimer(
      ProceedingJoinPoint joinPoint, SIPClientTransactionImpl clientTransaction) throws Throwable {
    if (!clientTransaction.transactionTimerStarted.get()) {
      String name = ((SipStackImpl) clientTransaction.getSIPStack()).getStackName();
      clientTransaction.timeoutTimerTicksLeft = getTimeoutTicks(name, clientTransaction);
    }
    joinPoint.proceed();
  }

  public static void setTransactionTimeout(String stackName, int timeout) {
    transactionTimeout.put(stackName, timeout);
  }

  public static void setPingTimeout(String stackName, int timeout) {
    pingTimeout.put(stackName, timeout);
  }

  private int getTimeoutTicks(
      @NonNull String stackName, @NonNull SIPClientTransaction clientTransaction) {
    if (clientTransaction.getMethod().equals(Request.OPTIONS)) {
      int defaultPingTimeout;
      if (clientTransaction.getTransport().equalsIgnoreCase(SIPConstants.UDP)) {
        defaultPingTimeout = CommonConfigurationProperties.DEFAULT_PING_TIMEOUT_UDP;
      } else {
        defaultPingTimeout = CommonConfigurationProperties.DEFAULT_PING_TIMEOUT_TCP;
      }
      return pingTimeout.getOrDefault(stackName, defaultPingTimeout)
          / clientTransaction.getBaseTimerInterval();
    }
    return transactionTimeout.getOrDefault(
            stackName, CommonConfigurationProperties.DEFAULT_TRANSACTION_TIMEOUT)
        / clientTransaction.getBaseTimerInterval();
  }
}
