package com.cisco.dsb.trunk.trunks;

import java.util.Map;
import org.springframework.plugin.core.Plugin;

public interface TrunkPluginInterface extends Plugin<TrunkType> {
  Map<String, ? extends AbstractTrunk> getTrunkMap();
}
