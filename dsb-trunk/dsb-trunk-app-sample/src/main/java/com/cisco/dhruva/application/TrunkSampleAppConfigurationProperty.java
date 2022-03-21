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
public class TrunkSampleAppConfigurationProperty {
  @NotBlank @Getter @Setter private String networkIn;
  @NotBlank @Getter @Setter private String networkOut;
  @NotBlank @Getter @Setter private String defaultEgress;
}
