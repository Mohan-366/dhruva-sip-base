package com.cisco.dsb.common.config;

import java.security.KeyStore;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/** This class holds configuration of truststore. */
@Getter
@Setter
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "common.tls")
@RefreshScope
@Component
public class TruststoreConfigurationProperties {

  private boolean enableCertService = false;
  private List<String> ciphers = CipherSuites.allowedCiphers;
  private int ocspResponseTimeoutInSeconds = 5;
  private String trustStoreFilePath = System.getProperty("javax.net.ssl.trustStore");
  private String trustStoreType = KeyStore.getDefaultType();
  private String trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword", "");
  private String keyStoreFilePath = System.getProperty("javax.net.ssl.keyStore");
  private String keyStoreType = KeyStore.getDefaultType();
  private String keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword", "");
  private List<String> tlsProtocols = Collections.singletonList("TLSv1.2");
}
