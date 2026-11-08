package com.science.gtnl.mixins.late.thaumcraft;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.science.gtnl.compat.thaumcraft.CelestweaveWandCapacity;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.items.wands.ItemWandCasting;

@Mixin(value = ItemWandCasting.class, remap = false)
public class MixinItemWandCastingCelestweave {

    @Inject(method = "onUpdate", at = @At("HEAD"), remap = true)
    private void gtnl$trackOwner(ItemStack stack, World world, Entity entity, int slot, boolean selected,
        CallbackInfo ci) {
        if (entity instanceof EntityPlayer player) CelestweaveWandCapacity.track(stack, player, slot);
    }

    @Inject(method = "addInformation", at = @At("HEAD"), remap = true)
    private void gtnl$trackTooltipOwner(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced,
        CallbackInfo ci) {
        CelestweaveWandCapacity.track(stack, player);
    }

    @ModifyReturnValue(method = "getMaxVis", at = @At("RETURN"), remap = false)
    private int gtnl$capacity(int original, ItemStack stack) {
        return CelestweaveWandCapacity.capacity(stack, original);
    }

    @Inject(method = { "addVis", "addRealVis" }, at = @At("HEAD"), cancellable = true)
    private void gtnl$preserveStoredVis(ItemStack stack, Aspect aspect, int amount, boolean commit,
        CallbackInfoReturnable<Integer> cir) {
        ItemWandCasting wand = (ItemWandCasting) (Object) this;
        if (amount >= 0 && aspect.isPrimal() && wand.getVis(stack, aspect) > wand.getMaxVis(stack))
            cir.setReturnValue(amount);
    }
}
