package com.cisco.dhruva.client;

import com.cisco.wx2.client.Client;
import com.cisco.wx2.client.health.ServiceHealthPinger;
import com.cisco.wx2.dto.health.ServiceHealth;
import java.net.URI;
import org.apache.http.HttpResponse;

public class CallingAppClient extends Client implements ServiceHealthPinger {

  protected CallingAppClient(CallingAppClientFactory factory, URI baseUrl) {
    super(factory, baseUrl);
  }

  public ServiceHealth ping() {
    return get("ping").execute(ServiceHealth.class);
  }

  public HttpResponse pingResponse() {
    return get("ping").execute();
  }
}
