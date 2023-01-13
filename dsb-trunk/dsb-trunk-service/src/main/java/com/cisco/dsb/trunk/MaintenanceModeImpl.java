package com.cisco.dsb.trunk;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.maintanence.Maintenance;
import com.cisco.dsb.common.maintanence.MaintenancePolicy;
import com.cisco.dsb.common.sip.util.SipUtils;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.sip.ProxySendMessage;
import com.cisco.dsb.trunk.trunks.AbstractTrunk;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.sip.message.Response;
import lombok.CustomLog;

@CustomLog
public class MaintenanceModeImpl implements MaintenanceMode {

  private Maintenance maintenanceConfig;
  private TrunkConfigurationProperties configurationProperties;
  private AbstractTrunk trunk;
  public static final String GLOBAL_MAINTENANCE_POLICY = "globalPolicy";

  public MaintenanceModeImpl(
      Maintenance maintenanceConfig,
      TrunkConfigurationProperties configurationProperties,
      AbstractTrunk trunk) {
    this.maintenanceConfig = maintenanceConfig;
    this.configurationProperties = configurationProperties;
    this.trunk = trunk;
  }

  public static MaintenanceModeImpl getInstance(
      Maintenance maintenanceConfig,
      TrunkConfigurationProperties configurationProperties,
      AbstractTrunk trunk) {
    return new MaintenanceModeImpl(maintenanceConfig, configurationProperties, trunk);
  }

  @Override
  public Predicate<ProxySIPRequest> isInMaintenanceMode() {
    return this::isMaintenanceMode;
  }

  public boolean isMaintenanceMode(ProxySIPRequest proxySIPRequest) {
    if (maintenanceConfig.isEnabled()
        && !SipUtils.isMidDialogRequest(proxySIPRequest.getRequest())) {
      logger.debug("Maintenance mode is activated");
      return true;
    }
    return false;
  }

  @Override
  public Function<ProxySIPRequest, ProxySIPRequest> maintenanceBehaviour() {
    return this::executeMaintenanceBehaviour;
  }

  public ProxySIPRequest executeMaintenanceBehaviour(ProxySIPRequest proxySIPRequest) {
    if (!configurationProperties.getTrunkToMaintenancePolicyMap().isEmpty()) {
      MaintenancePolicy mpToApply =
          configurationProperties.getTrunkToMaintenancePolicyMap().get(trunk.getName());
      if (mpToApply != null) {
        logger.info("Maintenance policy from trunk config applied: " + mpToApply);
        if (isDropMsgsConfigured().test(mpToApply)) {
          ArrayList<String> dropMsgs = new ArrayList<>(Arrays.asList(mpToApply.getDropMsgTypes()));
          dropMsgs.replaceAll(String::toUpperCase);
          if (dropMsgs.contains(proxySIPRequest.getRequest().getMethod())) {
            logger.warn("Received msg is dropped as per maintenance policy");
            return null;
          }
        } else if (isResponseCodeConfigured().test(mpToApply)) {
          return sendMaintenanceResponse(proxySIPRequest, mpToApply.getResponseCode());
        }
      }
    }
    return tryGlobalMaintenancePolicy(proxySIPRequest);
  }

  public ProxySIPRequest tryGlobalMaintenancePolicy(ProxySIPRequest proxySIPRequest) {
    Map<String, MaintenancePolicy> maintenancePolicyMap =
        configurationProperties.getCommonConfigurationProperties().getMaintenancePolicyMap();
    if (isGlobalPolicyConfigured().test(maintenancePolicyMap)) {
      MaintenancePolicy mpToApply = maintenancePolicyMap.get(GLOBAL_MAINTENANCE_POLICY);
      logger.info("Global Maintenance policy applied: " + mpToApply);
      if (isResponseCodeConfigured().test(mpToApply)) {
        return sendMaintenanceResponse(proxySIPRequest, mpToApply.getResponseCode());
      }
    }
    return sendMaintenanceResponse(proxySIPRequest, Response.SERVICE_UNAVAILABLE);
  }

  protected ProxySIPRequest sendMaintenanceResponse(
      ProxySIPRequest proxySIPRequest, int responseCode) {
    try {
      ProxySendMessage.sendResponse(
          responseCode,
          proxySIPRequest.getCallTypeName(),
          proxySIPRequest.getProvider(),
          proxySIPRequest.getServerTransaction(),
          proxySIPRequest.getRequest(),
          "Dhruva is in maintenance mode");
    } catch (DhruvaException e) {
      logger.warn("Error sending {} response from chosen maintenance policy", responseCode);
    }
    return null;
  }

  protected Predicate<MaintenancePolicy> isDropMsgsConfigured() {
    return mpToApply ->
        mpToApply.getDropMsgTypes() != null && mpToApply.getDropMsgTypes().length > 0;
  }

  protected Predicate<MaintenancePolicy> isResponseCodeConfigured() {
    return mpToApply -> mpToApply.getResponseCode() > 0;
  }

  protected Predicate<Map<String, MaintenancePolicy>> isGlobalPolicyConfigured() {
    return maintenancePolicyMap ->
        !maintenancePolicyMap.isEmpty()
            && maintenancePolicyMap.containsKey(GLOBAL_MAINTENANCE_POLICY);
  }
}
