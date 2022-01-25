package gov.nist.javax.sip.stack;

import gov.nist.javax.sip.message.SIPResponse;
import javax.sip.message.Request;
import lombok.CustomLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
@CustomLog
public class FixDialogSetLastResponseAspect {

  @Around(
      "execution(public void gov.nist.javax.sip.stack.SIPDialog.setLastResponse(gov.nist.javax.sip.stack.SIPTransaction , gov.nist.javax.sip.message"
          + ".SIPResponse)) && args(sipTransaction, sipResponse) &&"
          + " target(sipDialog)")
  public void dialogSetLastResponse(
      ProceedingJoinPoint pjp,
      SIPTransaction sipTransaction,
      SIPResponse sipResponse,
      SIPDialog sipDialog)
      throws Throwable {

    long lastInviteResponseCSeqNumberBeforeSet = sipDialog.lastInviteResponseCSeqNumber;
    int lastInviteResponseCodeBeforeSet = sipDialog.lastInviteResponseCode;
    String cSeqMethod = sipResponse.getCSeqHeader().getMethod();
    long responseCSeqNumber = sipResponse.getCSeq().getSeqNumber();
    int statusCode = sipResponse.getStatusCode();

    pjp.proceed();

    /**
     * This is to override the logic in here
     * https://github.com/RestComm/jain-sip/blob/e9772e5e00dbd6cdcc233484988a0ac522d9d012/src/gov/nist/javax/sip/stack/SIPDialog.java#L3378-L3381
     *
     * <p>update lastInviteResponseCSeqNumber and lastInviteResponseCode only when sipTransaction is
     * server transaction.
     *
     * <p>In here, if the transaction is not null and is of not of type server transaction, we are
     * going to reset `lastInviteResponseCSeqNumber` and `lastInviteResponseCode` to the previous
     * value.
     */
    if (Request.INVITE.equals(cSeqMethod)
        && sipTransaction != null
        && !sipTransaction.isServerTransaction()
        && (lastInviteResponseCSeqNumberBeforeSet != sipDialog.lastInviteResponseCSeqNumber
            || lastInviteResponseCodeBeforeSet != sipDialog.lastInviteResponseCode)) {
      logger.info(
          " {} - updating lastInviteResponseCSeqNumber and lastInviteResponseCode in dialog to the state before the method was invoked. "
              + "\n CSeq: {}, Method: {}, statusCode: {} "
              + "\n Before SetLastResponse(...) - InviteResponseSequenceNumber  = {} , LastInviteResponseCode = {} "
              + "\n After SetLastResponse(...)  - InviteResponseSequenceNumber  = {} , LastInviteResponseCode = {} ",
          sipDialog.getCallId().getCallId(),
          responseCSeqNumber,
          cSeqMethod,
          statusCode,
          lastInviteResponseCSeqNumberBeforeSet,
          lastInviteResponseCodeBeforeSet,
          sipDialog.lastInviteResponseCSeqNumber,
          sipDialog.lastInviteResponseCode);
      sipDialog.lastInviteResponseCSeqNumber = lastInviteResponseCSeqNumberBeforeSet;
      sipDialog.lastInviteResponseCode = lastInviteResponseCodeBeforeSet;
    }
  }
}
