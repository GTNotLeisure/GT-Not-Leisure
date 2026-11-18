package com.science.gtnl.common.me;

import appeng.api.util.WorldCoord;
import appeng.me.cluster.implementations.CraftingCPUCluster;

public class StandaloneCraftingCPUCluster extends CraftingCPUCluster {

    public StandaloneCraftingCPUCluster(WorldCoord min, WorldCoord max, int parallelism) {
        super(min, max);
        if (parallelism <= 0) {
            throw new IllegalArgumentException("Crafting parallelism must be positive");
        }
        // AE2 adds one base operation to the accelerator count.
        accelerator = parallelism - 1;
    }
}
