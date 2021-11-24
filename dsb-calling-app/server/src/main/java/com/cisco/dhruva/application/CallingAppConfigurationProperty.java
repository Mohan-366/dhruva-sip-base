package com.cisco.dhruva.application;

import javax.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "app")
public class CallingAppConfigurationProperty {
  @NotBlank @Getter @Setter private String networkPSTN;
  @NotBlank @Getter @Setter private String networkB2B;
  @NotBlank @Getter @Setter private String networkCallingCore;
}
