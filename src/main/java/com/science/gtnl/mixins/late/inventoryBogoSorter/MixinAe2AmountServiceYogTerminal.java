package com.science.gtnl.mixins.late.inventoryBogoSorter;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.bogosorter.common.network.ae2.impl.Ae2AmountServiceImpl;
import com.science.gtnl.common.yogsothoth.YogBaublesView;

@Mixin(value = Ae2AmountServiceImpl.class, remap = false)
public abstract class MixinAe2AmountServiceYogTerminal {

    @Inject(
        method = "getBaublesInventory(Lnet/minecraft/entity/player/EntityPlayerMP;)Lnet/minecraft/inventory/IInventory;",
        at = @At("RETURN"),
        cancellable = true)
    private static void gtnl$includeStoredTerminals(EntityPlayerMP player, CallbackInfoReturnable<IInventory> cir) {
        IInventory original = cir.getReturnValue();
        IInventory inventory = YogBaublesView.wrap(player, original);
        if (inventory != original) cir.setReturnValue(inventory);
    }
}
