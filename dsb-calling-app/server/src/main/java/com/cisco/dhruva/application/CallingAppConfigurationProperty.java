package com.cisco.dhruva.application;

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
}
