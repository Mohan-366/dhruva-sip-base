package com.cisco.dsb.trunk.config;

import com.cisco.dsb.common.util.JsonUtilFactory;
import com.cisco.dsb.trunk.dto.DynamicServer;
import com.cisco.dsb.trunk.dto.SGPolicy;
import com.cisco.dsb.trunk.dto.StaticServer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@CustomLog
@Configuration
public class TrunkConfigProperties {

  public static final String SIP_SERVER_GROUPS = "sipServerGroups";

  public static final String SIP_DYNAMIC_SERVER_GROUPS = "sipDynamicServerGroups";

  public static final String SIP_SG_POLICY = "sgPolicies";

  @Autowired private Environment env;

  @Bean(name = "staticServers")
  public List<StaticServer> getServerGroups() {

    String configuredServerGroups = env.getProperty(SIP_SERVER_GROUPS);

    List<StaticServer> sipServerGroups;

    if (configuredServerGroups != null) {
      try {
        sipServerGroups =
            Arrays.asList(
                JsonUtilFactory.getInstance(JsonUtilFactory.JsonUtilType.LOCAL)
                    .toObject(configuredServerGroups, StaticServer[].class));
      } catch (Exception e) {
        // TODO should we generate an Alarm
        logger.error(
            "Error converting JSON ServerGroup configuration provided in the environment", e);
        return getDefaultServerGroups();
      }
    } else {
      sipServerGroups = getDefaultServerGroups();
    }
    logger.info("Sip ServerGroup from the {} configuration {}", SIP_SERVER_GROUPS, sipServerGroups);

    return sipServerGroups;
  }

  private List<StaticServer> getDefaultServerGroups() {

    List<StaticServer> serverArrayList = new ArrayList<>();

    StaticServer serverGroup = StaticServer.builder().build();

    serverArrayList.add(serverGroup);

    return serverArrayList;
  }

  @Bean(name = "dynamicServers")
  public List<DynamicServer> getDynamicServerGroups() {

    String configuredDynamicServerGroups = env.getProperty(SIP_DYNAMIC_SERVER_GROUPS);

    List<DynamicServer> sipDynamicServerGroups = null;

    if (configuredDynamicServerGroups != null) {
      try {
        sipDynamicServerGroups =
            Arrays.asList(
                JsonUtilFactory.getInstance(JsonUtilFactory.JsonUtilType.LOCAL)
                    .toObject(configuredDynamicServerGroups, DynamicServer[].class));
      } catch (Exception e) {
        // TODO should we generate an Alarm
        logger.error(
            "Error converting JSON Dynamic ServerGroup configuration provided in the environment",
            e);
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

    List<SGPolicy> sgPolicies;

    if (configuredSgPolicies != null) {
      try {
        sgPolicies =
            Arrays.asList(
                JsonUtilFactory.getInstance(JsonUtilFactory.JsonUtilType.LOCAL)
                    .toObject(configuredSgPolicies, SGPolicy[].class));
      } catch (Exception e) {
        // TODO should we generate an Alarm
        logger.error("Error converting JSON SGPolicy configuration provided in the environment", e);
        return getDefaultSGPolicy();
      }
    } else {

      return getDefaultSGPolicy();
    }
    logger.info("Sip SG Policies from the {} configuration {}", SIP_SG_POLICY, sgPolicies);

    return sgPolicies;
  }

  private List<SGPolicy> getDefaultSGPolicy() {

    List<SGPolicy> sgPolicyList = new ArrayList<>();

    SGPolicy sgPolicy = SGPolicy.builder().build();

    sgPolicyList.add(sgPolicy);

    return sgPolicyList;
  }
}
