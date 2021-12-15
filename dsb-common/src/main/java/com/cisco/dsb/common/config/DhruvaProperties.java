package com.cisco.dsb.common.config;

import com.cisco.wx2.dto.BuildInfo;
import com.cisco.wx2.server.config.ConfigProperties;
import com.google.common.base.Strings;
import java.net.URI;
import javax.annotation.PostConstruct;
import lombok.CustomLog;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * Properties that are specific to the dhruva service.
 *
 * <p>See also:
 * http://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html
 */
@CustomLog
// @ConfigurationProperties(prefix = "dhruva")
public class DhruvaProperties extends ConfigProperties {
  // implements DiagnosticsClientFactory.DiagnosticsClientFactoryProperties {
  private static final String DEFAULT_DHRUVA_USER_AGENT = "WX2_DHRUVA";

  public static final String CLIENT_ID = "clientId";
  public static final String CLIENT_SECRET = "clientSecret";
  public static final String MACHINE_ACCOUNT_USER = "machineAccountUser";
  public static final String MACHINE_ACCOUNT_PASSWORD = "machineAccountPassword";
  public static final String ORG_ID = "orgId";
  public static final String POD_NAME_ENV_VAR_KEY = "POD_NAME";

  private static BuildInfo buildInfo;
  private final Environment env;

  @Getter private String podNameEnvVar;


  @PostConstruct
  public void init(){
    /* Pod name can be in form of dhruva-abc-0, dhruva-xyz-1 where 0,1 are the unique ordinal indexes of the stateful set in k8s env */
    this.podNameEnvVar =
            StringUtils.isBlank(System.getenv(POD_NAME_ENV_VAR_KEY))
                    ? String.valueOf(0)
                    : System.getenv(POD_NAME_ENV_VAR_KEY);
  }
  public enum Env {
    integration,
    production
  }

  @Autowired
  public DhruvaProperties(Environment env) {
    super(env, createUserAgentString(DEFAULT_DHRUVA_USER_AGENT, env));
    this.env = env;
  }

  @Override
  public int getApplicationInstanceIndex() {

    int instanceIndex = 0;

    logger.debug("Instance Index is: {} ", String.valueOf(instanceIndex));
    return instanceIndex;
  }

  public static String createUserAgentString(String uaType, Environment env) {
    String userAgent = uaType;

    // Also, set the static buildInfo instance
    buildInfo = BuildInfo.fromEnv(env);

    if (!Strings.isNullOrEmpty(buildInfo.getBuildId())) {
      userAgent += "/" + buildInfo.getBuildId();
    }
    userAgent += " (" + env.getProperty("environment", "local") + ")";

    return userAgent;
  }

  public String getDhruvaClientId() {
    return env.getProperty(
        CLIENT_ID,
        String.class,
        "C3dd242d35ce85d1e8aa1cf9256e28cb6655151d5827aa931004a2b3919fe9326");
  }

  public String getDhruvaClientSecret() {
    return env.getProperty(
        CLIENT_SECRET,
        String.class,
        "cd643f180ee30f46a6e1a06bf03b8dd0bce554cd1273a77f30eaaaff88ac660e");
  }

  public String getDhruvaMachineAccountUser() {
    return env.getProperty(MACHINE_ACCOUNT_USER, String.class, "Dhruva-dev-test");
  }

  public String getDhruvaMachineAccountPassword() {
    return env.getProperty(
        MACHINE_ACCOUNT_PASSWORD, String.class, "BUGJ.aruj.04.QRMO.cvjk.47.BSYO.abvh.1578");
  }

  public String getDhruvaOrgId() {
    return env.getProperty(ORG_ID, String.class, "9e2b500c-4899-4d00-b663-2d84c6901fc4");
  }

  public URI getDiagnosticsUrl() {
    return URI.create(
        env.getProperty(
            ConfigProperties.DIAGNOSTICS_API_SERVICE_URL_PROP,
            getDiagnosticsPublicUrl().toString()));
  }

  public URI getDiagnosticsPublicUrl() {
    return URI.create(
        env.getProperty(
            ConfigProperties.DIAGNOSTICS_API_SERVICE_PUBLIC_URL_PROP,
            getDefaultUrl(getSparkEnv())));
  }

  public String getDefaultUrl(Env env) {
    switch (env) {
      case production:
        logger.info("CDS Events to be sent to PROD env");
        return "https://diagnostics-a.wbx2.com/diagnostics/api/v1";
      default:
        logger.info("CDS Events to be sent to INT env");
        return "https://diagnostics-intb.ciscospark.com/diagnostics/api/v1";
    }
  }

  public Env getSparkEnv() {
    Env defaultEnv = isProductionEnvironment() ? Env.production : Env.integration;
    return env.getProperty("sparkEnv", Env.class, defaultEnv);
  }

  @Override
  public boolean isIntegrationEnvironment() {
    String environment = this.getEnvironment();
    return "wsjcint01".equals(environment)
        || "wdfwint01".equals(environment)
        || "integration".equals(environment)
        || "intb1".equals(environment)
        || "intb2".equals(environment)
        || "intb3".equals(environment)
        || "intb4".equals(environment)
        || "integration".equals(this.getCloud());
  }
}
