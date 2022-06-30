package com.cisco.dhruva.calltype;

import com.cisco.dhruva.application.TrunkSampleAppConfigurationProperty;
import com.cisco.dhruva.normalization.SampleNormalization;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.TrunkManager;
import com.cisco.dsb.trunk.trunks.TrunkType;
import javax.sip.message.Response;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@CustomLog
@Component
public class DefaultCallType {

  private TrunkManager trunkManager;
  private TrunkSampleAppConfigurationProperty trunkSampleAppConfigurationProperty;

  @Autowired
  public DefaultCallType(
      TrunkManager trunkManager,
      TrunkSampleAppConfigurationProperty trunkSampleAppConfigurationProperty) {
    this.trunkManager = trunkManager;
    this.trunkSampleAppConfigurationProperty = trunkSampleAppConfigurationProperty;
  }

  public void processRequest(ProxySIPRequest proxySIPRequest) {
    trunkManager
        .handleEgress(
            TrunkType.DEFAULT,
            proxySIPRequest,
            trunkSampleAppConfigurationProperty.getDefaultEgress(),
            new SampleNormalization())
        .subscribe(
            proxySIPResponse -> {
              logger.debug(
                  "Received response, callid:{} statusCode:{}",
                  proxySIPResponse.getCallId(),
                  proxySIPResponse.getStatusCode());
              proxySIPResponse.proxy();
            },
            err -> {
              logger.error(
                  "exception while sending the request with callId:{}",
                  proxySIPRequest.getCallId(),
                  err);
              if (err instanceof DhruvaRuntimeException)
                proxySIPRequest.reject(
                    ((DhruvaRuntimeException) err).getErrCode().getResponseCode());
              else proxySIPRequest.reject(Response.SERVER_INTERNAL_ERROR);
            });
  }
}
