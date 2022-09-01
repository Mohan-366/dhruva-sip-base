package com.cisco.dhruva.application;

import com.cisco.dhruva.application.errormapping.ErrorMappingPolicy;
import java.util.*;
import javax.annotation.PostConstruct;
import javax.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "app")
@RefreshScope
public class CallingAppConfigurationProperty {
  @NotBlank @Getter @Setter private String networkPSTN;
  @NotBlank @Getter @Setter private String networkB2B;
  @NotBlank @Getter @Setter private String networkCallingCore;
  @NotBlank @Getter @Setter private String b2bEgress;
  @NotBlank @Getter @Setter private String callingEgress;
  @NotBlank @Getter @Setter private String pstnIngress;

  @NotBlank @Getter @Setter
  private Maintenance maintenance = Maintenance.MaintenanceBuilder().build();

  @Getter @Setter Map<String, ErrorMappingPolicy> errorMappingPolicy = new HashMap<>();
  @Getter private Map<String, CallTypeConfig> callTypesMap = new HashMap<>();

  @Getter @Setter
  private Map<String, CallTypeConfigProperties> callTypesPropertiesMap = new HashMap<>();

  public void setCallTypes(Map<String, CallTypeConfigProperties> callTypesProperties) {
    this.callTypesPropertiesMap = callTypesProperties;
    // PostConstruct is not called for refresh scope changes.
    if (Objects.nonNull(this.errorMappingPolicy) && !this.errorMappingPolicy.isEmpty()) {
      this.updateCallTypes();
    }
  }

  @PostConstruct
  public void updateCallTypes() {
    // Update CallTypesMap.
    // Since only CallTypesMap is dependent on ErrorMappingPolicy and setCallTypes is invoked before
    // setErrorMappingPolicy due to spring behavior we need to initialize explicity once all
    // variables are initialized
    this.getCallTypesPropertiesMap()
        .keySet()
        .forEach(
            (key) -> {
              CallTypeConfigProperties callTypeConfigProperties =
                  this.callTypesPropertiesMap.get(key);
              CallTypeConfig callTypeConfig = new CallTypeConfig();
              callTypeConfig.setName(callTypeConfigProperties.getName());
              callTypeConfig.setErrorMappingPolicyConfig(
                  this.errorMappingPolicy.get(callTypeConfigProperties.getErrorMappingPolicy()));
              this.callTypesMap.put(key, callTypeConfig);
            });
  }
}
