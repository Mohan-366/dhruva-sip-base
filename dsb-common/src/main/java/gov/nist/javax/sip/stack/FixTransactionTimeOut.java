package gov.nist.javax.sip.stack;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import gov.nist.javax.sip.SipStackImpl;
import java.util.concurrent.ConcurrentHashMap;
import lombok.CustomLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
@CustomLog
public class FixTransactionTimeOut {

  private static ConcurrentHashMap<String, Integer> transactionTimeout = new ConcurrentHashMap<>();

  /*@Around(
      "execution(protected void gov.nist.javax.sip.stack.SIPTransactionImpl.enableTimeoutTimer(int))"
          + "&& target(transaction)")
  public void enableTransactionTimer(ProceedingJoinPoint pjp, SIPTransactionImpl transaction) {
    String name = ((SipStackImpl) transaction.getSIPStack()).getStackName();
    transaction.timeoutTimerTicksLeft =
        transactionTimeout.getOrDefault(
                name, CommonConfigurationProperties.DEFAULT_TRANSACTION_TIMEOUT)
            / transaction.baseTimerInterval;
  }*/

  @Around(
      "execution(public void gov.nist.javax.sip.stack.SIPClientTransactionImpl.startTransactionTimer())"
          + "&& target(clientTransaction)")
  public void startTransactionTimer(
      ProceedingJoinPoint joinPoint, SIPClientTransactionImpl clientTransaction) throws Throwable {
    String name = ((SipStackImpl) clientTransaction.getSIPStack()).getStackName();
    if (!clientTransaction.transactionTimerStarted.get()) {
      clientTransaction.timeoutTimerTicksLeft =
          transactionTimeout.getOrDefault(
                  name, CommonConfigurationProperties.DEFAULT_TRANSACTION_TIMEOUT)
              / clientTransaction.baseTimerInterval;
    }
    joinPoint.proceed();
  }

  public static void setTransactionTimeout(String stackName, int timeout) {
    transactionTimeout.put(stackName, timeout);
  }
}
