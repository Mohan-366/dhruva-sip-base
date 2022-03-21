package com.cisco.dsb.trunk.trunks;

import com.cisco.dsb.trunk.TrunkConfigurationProperties;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TrunkPlugins {
  TrunkConfigurationProperties configurationProperties;

  @Autowired
  public TrunkPlugins(TrunkConfigurationProperties configurationProperties) {
    this.configurationProperties = configurationProperties;
  }

  public TrunkPluginInterface getPSTN() {
    return new TrunkPluginInterface() {
      @Override
      public Map<String, PSTNTrunk> getTrunkMap() {
        return configurationProperties.getPstnTrunkMap();
      }

      @Override
      public boolean supports(TrunkType type) {
        return type == TrunkType.PSTN;
      }
    };
  }

  public TrunkPluginInterface getB2B() {
    return new TrunkPluginInterface() {

      @Override
      public Map<String, ? extends B2BTrunk> getTrunkMap() {
        return configurationProperties.getB2BTrunkMap();
      }

      @Override
      public boolean supports(TrunkType type) {
        return type == TrunkType.B2B;
      }
    };
  }

  public TrunkPluginInterface getCalling() {
    return new TrunkPluginInterface() {
      @Override
      public Map<String, ? extends AbstractTrunk> getTrunkMap() {
        return configurationProperties.getCallingTrunkMap();
      }

      @Override
      public boolean supports(TrunkType type) {
        return type == TrunkType.Calling_Core;
      }
    };
  }

  public TrunkPluginInterface getDefault() {
    return new TrunkPluginInterface() {
      @Override
      public Map<String, ? extends AbstractTrunk> getTrunkMap() {
        return configurationProperties.getDefaultTrunkMap();
      }

      @Override
      public boolean supports(TrunkType type) {
        return type == TrunkType.DEFAULT;
      }
    };
  }
}
