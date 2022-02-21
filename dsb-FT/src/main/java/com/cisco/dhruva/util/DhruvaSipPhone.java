package com.cisco.dhruva.util;

import java.lang.reflect.Method;
import java.text.ParseException;
import javax.sip.InvalidArgumentException;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;

public class DhruvaSipPhone extends SipPhone {
  public DhruvaSipPhone(SipStack stack, String host, String proto, int port, String me)
      throws ParseException, InvalidArgumentException {
    super(stack, host, proto, port, me);
  }

  public static DhruvaSipPhoneBuilder getBuilder() {
    return new DhruvaSipPhoneBuilder();
  }

  public static class DhruvaSipPhoneBuilder {

    private SipStack stack;
    private String host;
    private String proto;
    private int port;
    private String me;
    private String callIdDomain;
    private String callIdSuffix;
    private Method m;
    private String contactHost;
    private int contactPort;
    private boolean slashInCallId;

    public DhruvaSipPhone build() throws ParseException, InvalidArgumentException {
      return new DhruvaSipPhone(stack, host, proto, port, me);
    }

    public DhruvaSipPhoneBuilder withStack(SipStack stack) {
      this.stack = stack;
      return this;
    }

    public DhruvaSipPhoneBuilder with(String host, String protocol, int port) {
      this.host = host;
      this.proto = protocol;
      this.port = port;
      return this;
    }

    public DhruvaSipPhoneBuilder withID(String me) {
      this.me = me;
      return this;
    }

    public DhruvaSipPhoneBuilder withCallIdDomain(String callIdDomain) {
      this.callIdDomain = callIdDomain;
      return this;
    }

    public DhruvaSipPhoneBuilder withCallIdSuffix(String callIdSuffix) {
      this.callIdSuffix = callIdSuffix;
      return this;
    }

    public DhruvaSipPhoneBuilder withMethod(Method m) {
      this.m = m;
      return this;
    }

    public DhruvaSipPhoneBuilder withContactHost(String contactHost) {
      this.contactHost = contactHost;
      return this;
    }

    public DhruvaSipPhoneBuilder withContactPort(int contactPort) {
      this.contactPort = contactPort;
      return this;
    }

    public DhruvaSipPhoneBuilder withSlash(boolean slashInCallId) {
      this.slashInCallId = slashInCallId;
      return this;
    }
  }
}
