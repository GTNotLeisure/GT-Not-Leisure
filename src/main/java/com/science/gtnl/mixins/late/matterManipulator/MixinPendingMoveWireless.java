package com.science.gtnl.mixins.late.matterManipulator;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.recursive_pineapple.matter_manipulator.common.building.PendingMove;
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

    @Unique
    private Runnable gtnl$moved;

    @Inject(method = "tryPlaceBlocks", at = @At("RETURN"))
    private void gtnl$moving(ItemStack stack, EntityPlayer player, CallbackInfo ci) {
        if (gtnl$task == null) gtnl$start((PendingMove) (Object) this, player.worldObj.provider.dimensionId);
        boolean building = moves != null && !moves.isEmpty();
        WirelessConstructionSites.update(gtnl$task, building, gtnl$moved, gtnl$source, gtnl$target);
    }

    @Unique
    private void gtnl$start(PendingMove move, int dimension) {
        gtnl$task = new Object();
        int dx = move.getMoveOffsetX(), dy = move.getMoveOffsetY(), dz = move.getMoveOffsetZ();
        Site source = gtnl$source = gtnl$sourceSite(move, dimension);
        gtnl$target = source.offset(dx, dy, dz);
        gtnl$moved = WirelessConstructionSites.once(() -> WirelessMachineGroups.followMove(source, dx, dy, dz));
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
