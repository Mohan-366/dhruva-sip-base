package com.cisco.dhruva.common.context;

import com.cisco.dhruva.util.DeepCopy;
import java.io.NotSerializableException;
import java.util.*;

public class ExecutionContext extends HashMap<String, Object> implements Cloneable {

  private static final int INITIAL_SIZE = 64;

  private final String ctxId;

  public ExecutionContext() {

    super(INITIAL_SIZE);

    this.timestamp = System.currentTimeMillis();
    this.ctxId = this.timestamp + String.valueOf(super.hashCode());
  }

  private final long timestamp;

  @Override
  public ExecutionContext clone() {
    return (ExecutionContext) super.clone();
  }

  public String getString(String key) {
    return (String) get(key);
  }

  /** We use this information to pass it to the proxy */
  private final Map<String, String> extraHeaders = new HashMap<>();

  public boolean getBoolean(String key) {
    return getBoolean(key, false);
  }

  public boolean getBoolean(String key, boolean defaultResponse) {
    Boolean b = (Boolean) get(key);
    if (b != null) {
      return b.booleanValue();
    }
    return defaultResponse;
  }

  public void set(String key) {
    put(key, Boolean.TRUE);
  }

  public void set(String key, Object value) {
    if (value != null) put(key, value);
    else remove(key);
  }

  public ExecutionContext copy() {
    ExecutionContext copy = new ExecutionContext();
    // do field by filed assignment

    Iterator<String> it = keySet().iterator();
    String key = it.next();
    while (key != null) {
      Object orig = get(key);
      try {
        Object copyValue = DeepCopy.copy(orig);
        if (copyValue != null) {
          copy.set(key, copyValue);
        } else {
          copy.set(key, orig);
        }
      } catch (NotSerializableException e) {
        copy.set(key, orig);
      }
      if (it.hasNext()) {
        key = it.next();
      } else {
        key = null;
      }
    }
    return copy;
  }

  /**
   * Gets the throwable that will be use in the Error endpoint.
   *
   * @return a set throwable
   */
  public Throwable getError() {
    return (Throwable) get("_error");
  }

  /** @param th */
  public void setError(Throwable th) {
    put("_error", th);
  }

  /**
   * Adds extra headers to use for this call context.
   *
   * @param name the name of the header.
   * @param value the value of the header.
   */
  public synchronized void addExtraHeader(String name, String value) {
    if (!this.extraHeaders.containsKey(name)) {
      this.extraHeaders.put(name, value);
    }
  }

  /**
   * Returns the extra headers.
   *
   * @return the extra headers.
   */
  public Map<String, String> getExtraHeaders() {
    return Collections.unmodifiableMap(this.extraHeaders);
  }
}
