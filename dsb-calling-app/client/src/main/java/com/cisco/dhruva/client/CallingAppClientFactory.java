package com.cisco.dhruva.client;

import com.cisco.wx2.client.ClientFactory;
import com.cisco.wx2.client.health.MonitorableClientFactory;
import com.cisco.wx2.client.health.ServiceHealthPinger;
import java.net.URI;

public class CallingAppClientFactory extends ClientFactory implements MonitorableClientFactory {
  @Override
  public ServiceHealthPinger getServiceHealthPinger() {
    return newCallingAppClient();
  }

  public CallingAppClient newCallingAppClient() {
    return new CallingAppClient(this, baseUrl);
  }

  private CallingAppClientFactory(Builder builder) {
    super(builder);
  }

  public static Builder builder(ClientFactory.Properties props, URI baseUrl) {
    return new Builder(props, baseUrl);
  }

  public static class Builder extends ClientFactory.Builder<Builder> {

    public Builder(ClientFactory.Properties props, URI baseUrl) {
      super(props, baseUrl);
    }

    public CallingAppClientFactory build() {
      return new CallingAppClientFactory(this);
    }
  }
}
