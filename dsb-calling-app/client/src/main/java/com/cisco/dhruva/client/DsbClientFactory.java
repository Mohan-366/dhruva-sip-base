package com.cisco.dhruva.client;

import com.cisco.wx2.client.ClientFactory;
import com.cisco.wx2.client.health.MonitorableClientFactory;
import com.cisco.wx2.client.health.ServiceHealthPinger;
import java.net.URI;

public class DsbClientFactory extends ClientFactory implements MonitorableClientFactory {
  @Override
  public ServiceHealthPinger getServiceHealthPinger() {
    return newDsbClient();
  }

  public DsbClient newDsbClient() {
    return new DsbClient(this, baseUrl);
  }

  private DsbClientFactory(Builder builder) {
    super(builder);
  }

  public static Builder builder(ClientFactory.Properties props, URI baseUrl) {
    return new Builder(props, baseUrl);
  }

  public static class Builder extends ClientFactory.Builder<Builder> {

    public Builder(ClientFactory.Properties props, URI baseUrl) {
      super(props, baseUrl);
    }

    public DsbClientFactory build() {
      return new DsbClientFactory(this);
    }
  }
}
