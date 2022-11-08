package com.cisco.dhruva.application.controller;

import com.cisco.dsb.common.dns.DnsInjectionService;
import com.cisco.dsb.common.dto.OverrideSipRouting;
import com.cisco.dsb.common.metric.DsbTimed;
import com.cisco.wx2.dto.DataTransferObject;
import com.cisco.wx2.dto.GenericResponse;
import com.cisco.wx2.server.AbstractController;
import com.cisco.wx2.server.auth.ng.*;
import com.cisco.wx2.server.auth.ng.annotation.AuthorizeWhen;
import com.cisco.wx2.util.JsonUtil;
import java.util.concurrent.Callable;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import lombok.CustomLog;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@CustomLog
@RequestMapping("${cisco-spark.server.api-path:/api}/v1/admin")
public class AdminController extends AbstractController {

  private static final String USER_ID_PATTERN = "^([A-Za-z0-9]|([A-Za-z0-9]+[\\-]*[A-Za-z0-9]+)*)$";
  private static final int USER_ID_LENGTH = 50;
  private DnsInjectionService dnsInjectionService;

  @Autowired
  public void setDnsInjectionService(DnsInjectionService dnsInjectionService) {
    this.dnsInjectionService = dnsInjectionService;
  }

  @AuthorizeWhen(
      scopes = Scope.Identity.SCIM,
      accountType = AccountType.MACHINE,
      targetOrgId = Auth.Org.NONE)
  @PostMapping(value = "SipRoutingOverrides/{userId}")
  @DsbTimed(name = "api.setOverrideSipRouting")
  public GenericResponse setOverrideSipRouting(
      @RequestBody @Valid final OverrideSipRouting overrides,
      @PathVariable("userId") @Length(max = USER_ID_LENGTH) @Pattern(regexp = USER_ID_PATTERN)
          String userId) {
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

  @AuthorizeWhen(
      scopes = Scope.Identity.SCIM,
      accountType = AccountType.MACHINE,
      targetOrgId = Auth.Org.NONE)
  @DeleteMapping(value = "SipRoutingOverrides/{userId}")
  @DsbTimed(name = "deleteOverrideSipRouting")
  public GenericResponse deleteOverrideSipRouting(
      @PathVariable("userId") @Length(max = USER_ID_LENGTH) @Pattern(regexp = USER_ID_PATTERN)
          String userId) {
    logger.info("Delete overrides at userId={}", userId);
    dnsInjectionService.clear(userId);
    return DataTransferObject.genericResponse("SUCCESS");
  }

  @AuthorizeWhen(
      scopes = Scope.Identity.SCIM,
      accountType = AccountType.MACHINE,
      targetOrgId = Auth.Org.NONE)
  @GetMapping(value = "SipRoutingOverrides/{userId}")
  @DsbTimed(name = "getOverrideSipRouting")
  public Callable<OverrideSipRouting> getOverrideSipRouting(
      @PathVariable("userId") @Length(max = USER_ID_LENGTH) @Pattern(regexp = USER_ID_PATTERN)
          String userId) {
    return () ->
        new OverrideSipRouting(
            dnsInjectionService.getInjectedA(userId), dnsInjectionService.getInjectedSRV(userId));
  }
}
