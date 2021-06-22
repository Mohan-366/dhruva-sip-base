package com.cisco.dsb.common.executor;

import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import com.cisco.wx2.util.stripedexecutor.StripedRunnable;
import java.util.Map;
import org.apache.commons.collections4.map.AbstractReferenceMap;
import org.apache.commons.collections4.map.ReferenceMap;

public abstract class BaseHandler implements StripedRunnable {
  private static Map<String, Object> stripes =
      new ReferenceMap<>(
          AbstractReferenceMap.ReferenceStrength.HARD, AbstractReferenceMap.ReferenceStrength.WEAK);
  private Logger logger = DhruvaLoggerFactory.getLogger(BaseHandler.class);

  /**
   * When an object implementing interface <code>Runnable</code> is used to create a thread,
   * starting the thread causes the object's <code>run</code> method to be called in that separately
   * executing thread.
   *
   * <p>The general contract of the method <code>run</code> is that it may take any action
   * whatsoever.
   *
   * @see Thread#run()
   */
  @Override
  public void run() {

    try {
      this.executeRun();
    } catch (Throwable t) {
      logger.error("BaseHandler Exception: " + t.getMessage(), t);
    }
  }

  @Override
  public final Object getStripe() {
    Object stripe = null;
    String stripeId = getStripeId();
    if (stripeId != null) {
      synchronized (stripes) {
        stripe = stripes.get(stripeId);

        if (stripe == null) {
          stripe = new Object();
          stripes.put(stripeId, stripe);
        }
      }
    }

    return stripe;
  }

  // Executes the business logic of the handler.
  public abstract void executeRun() throws Exception;

  // Returns the callId if one exists.
  public abstract String getCallId();

  // Returns the stripe ID. By default, this is the call ID, so all handlers of the same callId get
  // serialized. If stripe
  // ID is null then it's not serialized with anything.
  public String getStripeId() {
    return getCallId();
  }
}
