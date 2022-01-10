package unused.mightneedlater;

public class ThreadLocals {
  private static ThreadLocal<String> threadLocalCallId = ThreadLocal.withInitial(() -> null);

  public static void setCallId(String callId) {
    threadLocalCallId.set(callId);
  }

  public static String getCallId() {
    return threadLocalCallId.get();
  }

  public static void removeCallId() {
    threadLocalCallId.remove();
  }

  private static ThreadLocal<Boolean> callSpecificMessageChannel =
      ThreadLocal.withInitial(() -> Boolean.FALSE);

  public static void setCallSpecificMessageChannel() {
    callSpecificMessageChannel.set(Boolean.TRUE);
  }

  public static boolean isCallSpecificMessageChannelEnabled() {
    return callSpecificMessageChannel.get();
  }

  public static void removeCallSpecificMessageChannel() {
    callSpecificMessageChannel.remove();
  }
}
