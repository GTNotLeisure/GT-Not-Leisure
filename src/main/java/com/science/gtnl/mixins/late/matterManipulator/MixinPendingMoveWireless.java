package com.science.gtnl.mixins.late.matterManipulator;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.recursive_pineapple.matter_manipulator.common.building.BlockSpec;
import com.recursive_pineapple.matter_manipulator.common.building.PendingMove;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Location;
import com.science.gtnl.common.wireless.WirelessChannelManager.Address;
import com.science.gtnl.common.wireless.WirelessConstructionSites;
import com.science.gtnl.common.wireless.WirelessConstructionSites.Site;
import com.science.gtnl.common.wireless.WirelessMachineGroups;

@Mixin(value = PendingMove.class, remap = false)
public abstract class MixinPendingMoveWireless {

    @Shadow
    private List<?> moves;

    @Unique
    private Site gtnl$source;

    @Unique
    private Site gtnl$target;

    @Unique
    private Object gtnl$task;

    @Inject(method = "tryPlaceBlocks", at = @At("RETURN"))
    private void gtnl$moving(ItemStack stack, EntityPlayer player, CallbackInfo ci) {
        if (gtnl$task == null) {
            gtnl$task = new Object();
            PendingMove move = (PendingMove) (Object) this;
            gtnl$source = gtnl$sourceSite(move, player.worldObj.provider.dimensionId);
            gtnl$target = gtnl$source.offset(move.getMoveOffsetX(), move.getMoveOffsetY(), move.getMoveOffsetZ());
        }
        WirelessConstructionSites.update(gtnl$task, moves != null && !moves.isEmpty(), gtnl$source, gtnl$target);
    }

    @Inject(method = "moveBlock", at = @At("RETURN"))
    private static void gtnl$followMovedController(PendingMove move, World world, Location source,
        BlockSpec sourceBlock, Location target, BlockSpec targetBlock, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValueZ()) WirelessMachineGroups.followMove(gtnl$address(source), gtnl$address(target));
    }

    @Unique
    private static Address gtnl$address(Location location) {
        return new Address(location.worldId, location.x, location.y, location.z, ForgeDirection.UNKNOWN);
    }

    @Unique
    private static Site gtnl$sourceSite(PendingMove move, int dimension) {
        return new Site(
            dimension,
            move.getSrcMinX(),
            move.getSrcMinY(),
            move.getSrcMinZ(),
            move.getSrcMaxX(),
            move.getSrcMaxY(),
            move.getSrcMaxZ());
    }
}
