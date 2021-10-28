package com.cisco.dsb.trunk.config;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.util.JSONUtilityException;
import com.cisco.dsb.common.util.JsonSchemaValidator;
import com.cisco.dsb.common.util.JsonUtilFactory;
import com.cisco.dsb.trunk.dto.DynamicServer;
import com.cisco.dsb.trunk.dto.SGPolicy;
import com.cisco.dsb.trunk.dto.StaticServer;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import java.io.IOException;
import java.util.*;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@CustomLog
@Configuration
public class TrunkConfigProperties {

  public static final String SIP_SERVER_GROUPS = "sipServerGroups";

  public static final String SIP_DYNAMIC_SERVER_GROUPS = "sipDynamicServerGroups";

  public static final String SIP_SG_POLICY = "sgPolicies";

  public static final String STATIC_SCHEMA = "staticServerSchema";
  public static final String SGPOLICY_SCHEMA = "sgPolicySchema";
  public static final String DYNAMIC_SCHEMA = "dynamicServerGroupSchema";
  @Autowired private ApplicationContext context;
  @Autowired private Environment env;
  private CommonConfigurationProperties commonConfigurationProperties;

  @Autowired
  public void setCommonConfigurationProperties(CommonConfigurationProperties properties) {
    this.commonConfigurationProperties = properties;
  }

  @Bean(name = "staticServers")
  public List<StaticServer> getServerGroups() {
    String configuredServerGroups = env.getProperty(SIP_SERVER_GROUPS);
    List<StaticServer> sipServerGroups = null;
    if (configuredServerGroups != null) {
      try {
        JsonSchemaValidator.validateSchema(configuredServerGroups, STATIC_SCHEMA);
        sipServerGroups =
            new LinkedList<>(
                Arrays.asList(
                    JsonUtilFactory.getInstance(JsonUtilFactory.JsonUtilType.LOCAL)
                        .toObject(configuredServerGroups, StaticServer[].class)));
        validateNetwork(sipServerGroups);

      } catch (IOException | ProcessingException | JSONUtilityException | DhruvaException pe) {
        logger.error("Schema validation failed for staticServerGroup, exiting", pe);
        SpringApplication.exit(context, () -> 0);
      }

    } else {
      return Collections.emptyList();
    }

    logger.info("Sip ServerGroup from the {} configuration {}", SIP_SERVER_GROUPS, sipServerGroups);
    return Collections.unmodifiableList(sipServerGroups);
  }

  @Bean(name = "dynamicServers")
  public List<DynamicServer> getDynamicServerGroups() {

    String configuredDynamicServerGroups = env.getProperty(SIP_DYNAMIC_SERVER_GROUPS);

    List<DynamicServer> sipDynamicServerGroups = null;

    if (configuredDynamicServerGroups != null) {
      try {

        JsonSchemaValidator.validateSchema(configuredDynamicServerGroups, DYNAMIC_SCHEMA);
        sipDynamicServerGroups =
            Arrays.asList(
                JsonUtilFactory.getInstance(JsonUtilFactory.JsonUtilType.LOCAL)
                    .toObject(configuredDynamicServerGroups, DynamicServer[].class));

      } catch (IOException | ProcessingException | JSONUtilityException pe) {
        logger.error("Schema validation failed for dynamicServers", pe);
        SpringApplication.exit(context, () -> 0);
      }
    }
    logger.info(
        "Sip Dynamic ServerGroup from the {} configuration {}",
        SIP_DYNAMIC_SERVER_GROUPS,
        sipDynamicServerGroups);

    return sipDynamicServerGroups;
  }

  @Bean(name = "sgPolicies")
  public List<SGPolicy> getSGPolicies() {

    String configuredSgPolicies = env.getProperty(SIP_SG_POLICY);

    List<SGPolicy> sgPolicies = null;

    if (configuredSgPolicies != null) {

      try {
        JsonSchemaValidator.validateSchema(
            configuredSgPolicies, TrunkConfigProperties.SGPOLICY_SCHEMA);
        sgPolicies =
            Arrays.asList(
                JsonUtilFactory.getInstance(JsonUtilFactory.JsonUtilType.LOCAL)
                    .toObject(configuredSgPolicies, SGPolicy[].class));
      } catch (IOException | ProcessingException | JSONUtilityException pe) {
        logger.error("Schema validation failed for sgPolicies, exiting", pe);
        SpringApplication.exit(context, () -> 0);
      }
    }
    logger.info("Sip SG Policies from the {} configuration {}", SIP_SG_POLICY, sgPolicies);
    return sgPolicies;
  }

  public void validateNetwork(List<StaticServer> staticServers) throws DhruvaException {

    for (StaticServer staticServer : staticServers) {
      if (commonConfigurationProperties.getListenPoints().stream()
          .noneMatch(e -> e.getName().equals(staticServer.getNetworkName())))
        throw new DhruvaException("wrong network name, does not exist");
    }
  }
}
