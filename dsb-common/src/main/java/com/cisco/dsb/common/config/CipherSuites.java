package com.cisco.dsb.common.config;

import java.util.List;
import java.util.stream.Collectors;

public class CipherSuites {

  static final List<String> allowedCiphers =
      List.of(
          "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
          "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
          "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
          "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
          "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
          "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
          "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
          "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
          "TLS_RSA_WITH_AES_256_GCM_SHA384",
          "TLS_RSA_WITH_AES_256_CBC_SHA256",
          "TLS_RSA_WITH_AES_128_GCM_SHA256",
          "TLS_RSA_WITH_AES_128_CBC_SHA256",
          "TLS_DHE_DSS_WITH_AES_256_GCM_SHA384",
          "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",
          "TLS_DHE_DSS_WITH_AES_256_CBC_SHA256",
          "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256",
          "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256");

  static List<String> getAllowedCiphers(List<String> ciphers) {

    return ciphers.stream().filter(allowedCiphers::contains).collect(Collectors.toList());
  }
}
