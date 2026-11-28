package com.science.gtnl.common.projector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fluids.IFluidHandler;

import com.gtnewhorizon.structurelib.alignment.IAlignment;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

// Block and item handling the builder and the dismantler share: breaking as the player, handing items back, facing
// machines and undoing a cancelled place.
public class ProjectorBlocks {

    private ProjectorBlocks() {}

    // Blocks in the way that hold items, fluids or a machine are left alone.
    public static int clear(EntityPlayerMP player, int x, int y, int z) {
        return breakAsPlayer(player, x, y, z, true, null, drop -> give(player, drop));
    }

    // A dead player's inventory is gone with the respawn, so what does not fit in a living one lands at their feet.
    public static void give(EntityPlayerMP player, ItemStack drop) {
        if (player.isEntityAlive()) player.inventory.addItemStackToInventory(drop);
        if (drop.stackSize > 0) player.dropPlayerItemWithRandomChoice(drop, false);
    }

    // Breaks a block as the player would, so protection mods can refuse it; its drops, and anything it spills while
    // breaking, go to the sink instead of the ground. A block without a tile entity gives back the item it was placed
    // from when that is known, as glass or ice would otherwise drop nothing.
    public static int breakAsPlayer(EntityPlayerMP player, int x, int y, int z, boolean protect, ItemStack placedFrom,
        Consumer<ItemStack> sink) {
        World world = player.worldObj;
        Block block = world.getBlock(x, y, z);
        if (block.isAir(world, x, y, z)) return ProjectorBuilder.CLEARED;
        if (block.getBlockHardness(world, x, y, z) < 0 || protect && isProtected(world.getTileEntity(x, y, z)))
            return ProjectorBuilder.OCCUPIED;
        if (!world.canMineBlock(player, x, y, z) || !player.canPlayerEdit(x, y, z, ForgeDirection.UP.ordinal(), null))
            return ProjectorBuilder.BLOCKED;
        BlockEvent.BreakEvent event = ForgeHooks
            .onBlockBreakEvent(world, player.theItemInWorldManager.getGameType(), player, x, y, z);
        if (event.isCanceled()) return ProjectorBuilder.REFUSED;
        int meta = world.getBlockMetadata(x, y, z);
        List<ItemStack> drops = new ArrayList<>();
        if (!player.capabilities.isCreativeMode) {
            if (placedFrom != null && !block.hasTileEntity(meta)) drops.add(placedFrom.copy());
            else drops.addAll(block.getDrops(world, x, y, z, meta, 0));
        }
        block.onBlockHarvested(world, x, y, z, meta, player);
        if (!block.removedByPlayer(world, player, x, y, z, false)) return ProjectorBuilder.FAILED;
        block.onBlockDestroyedByPlayer(world, x, y, z, meta);
        for (Object entity : world.getEntitiesWithinAABB(
            EntityItem.class,
            AxisAlignedBB.getBoundingBox(x - 0.5, y - 0.5, z - 0.5, x + 1.5, y + 1.5, z + 1.5))) {
            EntityItem spilled = (EntityItem) entity;
            if (spilled.isDead || spilled.age > 0) continue;
            drops.add(spilled.getEntityItem());
            spilled.setDead();
        }
        for (ItemStack drop : drops) {
            if (drop != null && drop.stackSize > 0) sink.accept(drop);
        }
        return ProjectorBuilder.CLEARED;
    }

    public static boolean isProtected(TileEntity tile) {
        return tile instanceof IInventory || tile instanceof IFluidHandler || tile instanceof IGregTechTileEntity;
    }

    public static void face(IGregTechTileEntity tile, ExtendedFacing facing) {
        tile.setFrontFacing(facing.getDirection());
        if (tile.getMetaTileEntity() instanceof IAlignment alignment) alignment.setExtendedFacing(facing);
    }

    // Restoring with this flag set keeps the removed blocks from dropping items, as Forge does for cancelled places.
    public static void restore(World world, List<BlockSnapshot> snapshots) {
        world.restoringBlockSnapshots = true;
        try {
            for (int i = snapshots.size() - 1; i >= 0; i--) snapshots.get(i)
                .restore(true, false);
        } finally {
            world.restoringBlockSnapshots = false;
        }
    }

    // A plain copy without NBT, so InventoryUtility only takes items that carry no data either.
    public static ItemStack single(ItemStack stack, int count) {
        return new ItemStack(stack.getItem(), count, stack.getItemDamage());
    }
}
