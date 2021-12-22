package com.cisco.dhruva.application.controller;

import static org.springframework.web.bind.annotation.RequestMethod.*;

import com.cisco.dsb.common.dns.DnsInjectionService;
import com.cisco.dsb.common.dto.OverrideSipRouting;
import com.cisco.dsb.common.metric.DsbTimed;
import com.cisco.wx2.dto.DataTransferObject;
import com.cisco.wx2.dto.GenericResponse;
import com.cisco.wx2.server.AbstractController;
import com.cisco.wx2.server.auth.ng.*;
import com.cisco.wx2.server.auth.ng.annotation.AuthorizeAnonymous;
import com.cisco.wx2.server.auth.ng.annotation.AuthorizeSuppressWarnings;
import com.cisco.wx2.util.JsonUtil;
import java.util.concurrent.Callable;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CustomLog
@RequestMapping("${cisco-spark.server.api-path:/api}/v1/admin")
public class AdminController extends AbstractController {

  private DnsInjectionService dnsInjectionService;

  @Autowired
  public void setDnsInjectionService(DnsInjectionService dnsInjectionService) {
    this.dnsInjectionService = dnsInjectionService;
  }

  @AuthorizeAnonymous
  @AuthorizeSuppressWarnings(ValidationType.ANONYMOUS_AUTH_APPLIED_TO_WRITE_API)
  @RequestMapping(value = "SipRoutingOverrides/{userId}", method = POST)
  @DsbTimed(name = "api.setOverrideSipRouting")
  public GenericResponse setOverrideSipRouting(
      @RequestBody final OverrideSipRouting overrides, @PathVariable("userId") String userId) {
    final String sOverrides = JsonUtil.toJson(overrides);
    logger.info("Set userId={} overrides={}", userId, sOverrides);

    dnsInjectionService.clear(userId);
    if (overrides.getDnsSRVRecords() != null) {
      dnsInjectionService.injectSRV(userId, overrides.getDnsSRVRecords());
    }
    if (overrides.getDnsARecords() != null) {
      dnsInjectionService.injectA(userId, overrides.getDnsARecords());
    }
    return DataTransferObject.genericResponse("SUCCESS");
  }

  @AuthorizeAnonymous
  @AuthorizeSuppressWarnings(ValidationType.ANONYMOUS_AUTH_APPLIED_TO_WRITE_API)
  @RequestMapping(value = "SipRoutingOverrides/{userId}", method = DELETE)
  @DsbTimed(name = "deleteOverrideSipRouting")
  public GenericResponse deleteOverrideSipRouting(@PathVariable("userId") String userId) {
    logger.info("Delete overrides at userId={}", userId);
    dnsInjectionService.clear(userId);
    return DataTransferObject.genericResponse("SUCCESS");
  }

  @AuthorizeAnonymous
  @RequestMapping(value = "SipRoutingOverrides/{userId}", method = GET)
  @DsbTimed(name = "getOverrideSipRouting")
  public Callable<OverrideSipRouting> getOverrideSipRouting(@PathVariable("userId") String userId) {
    return () ->
        new OverrideSipRouting(
            dnsInjectionService.getInjectedA(userId), dnsInjectionService.getInjectedSRV(userId));
  }
}
