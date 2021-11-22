package com.cisco.dsb;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.trunks.*;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "trunk")
public class TrunkConfigurationProperties {
  private CommonConfigurationProperties commonConfigurationProperties;

  @Autowired
  public void setCommonConfigurationProperties(
      CommonConfigurationProperties commonConfigurationProperties) {
    this.commonConfigurationProperties = commonConfigurationProperties;
  }
  // Key is name of trunk
  // <String,PSTNTrunk> pstnTrunks;
  @Getter private PSTNTrunks pstnTrunks = new PSTNTrunks();
  @Getter private BeechTrunk beechTrunk;
  @Getter private CallingTrunk callingTrunk;

  public void setPSTN(Map<String, PSTNTrunk> pstnTrunkMap) {
    pstnTrunkMap.values().forEach(this::validateTrunk);
    this.pstnTrunks.setTrunkMap(pstnTrunkMap);
  }

  public void setB2B(BeechTrunk beechTrunk) {
    validateTrunk(beechTrunk);
    this.beechTrunk = beechTrunk;
  }

  public void setCallingCore(CallingTrunk callingTrunk) {
    validateTrunk(callingTrunk);
    this.callingTrunk = callingTrunk;
  }

  private void validateTrunk(AbstractTrunk trunk) {
    List<String> serverGroupsConfig = trunk.getEgress().getServerGroupsConfig();
    Map<String, ServerGroup> serverGroupMap = trunk.getEgress().getServerGroupMap();
    serverGroupsConfig.forEach(
        sgName -> {
          ServerGroup sg = commonConfigurationProperties.getServerGroups().get(sgName);
          if (sg == null)
            throw new DhruvaRuntimeException(
                String.format("Unable to Configure Egress, servergroup %s not present", sgName));
          serverGroupMap.put(sgName, sg);
        });
    // TODO remove SGE from pingMap,
  }
}
