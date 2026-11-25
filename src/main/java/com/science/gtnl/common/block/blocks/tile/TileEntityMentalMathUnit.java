package com.science.gtnl.common.block.blocks.tile;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.science.gtnl.common.block.blocks.BlockMentalMathUnit;
import com.science.gtnl.common.block.blocks.MentalMathUnitDefinition;
import com.science.gtnl.common.me.StandaloneCraftingCPUCalculator;

import appeng.api.networking.GridFlags;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.crafting.TileCraftingStorageTile;

public class TileEntityMentalMathUnit extends TileCraftingStorageTile {

    private final StandaloneCraftingCPUCalculator calculator = new StandaloneCraftingCPUCalculator(this);

    public TileEntityMentalMathUnit() {
        getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    @Override
    public void onReady() {
        if (getForward() == ForgeDirection.UNKNOWN || getUp() == ForgeDirection.UNKNOWN) {
            setOrientation(ForgeDirection.SOUTH, ForgeDirection.UP);
        }
        super.onReady();
    }

    public MentalMathUnitDefinition getDefinition() {
        return ((BlockMentalMathUnit) getBlockType()).getDefinition();
    }

    @Override
    protected AENetworkProxy createProxy() {
        return new AENetworkProxy(this, "proxy", null, true);
    }

    @Override
    protected ItemStack getItemFromTile(Object object) {
        TileEntityMentalMathUnit tile = (TileEntityMentalMathUnit) object;
        if (tile.worldObj == null || !(tile.getBlockType() instanceof BlockMentalMathUnit block)) {
            return null;
        }
        return new ItemStack(block);
    }

    @Override
    public long getStorageBytes() {
        if (worldObj == null || notLoaded()) {
            return 0;
        }
        return getDefinition().bytes();
    }

    @Override
    public void updateMultiBlock() {
        if (worldObj == null || worldObj.isRemote || isInvalid() || getCluster() != null || notLoaded()) {
            return;
        }
        // Neighbors cannot change a singleton CPU; network events update its power and channels.
        calculator.calculateMultiblock(worldObj, getLocation());
    }

    @Override
    public void invalidate() {
        super.invalidate();
        // AE2 preserves the job when its core is invalidated instead of broken.
        breakCluster();
    }
}
