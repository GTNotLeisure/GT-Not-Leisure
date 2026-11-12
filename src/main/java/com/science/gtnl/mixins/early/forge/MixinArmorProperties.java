package com.science.gtnl.mixins.early.forge;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.ISpecialArmor.ArmorProperties;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.science.gtnl.common.item.items.ItemElainaBrooch;

@Mixin(value = ArmorProperties.class, remap = false)
public class MixinArmorProperties {

    @Inject(method = "ApplyArmor", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gtnl$elainaBroochArmor(EntityLivingBase entity, ItemStack[] inventory, DamageSource source,
        double damage, CallbackInfoReturnable<Float> cir) {
        if (entity instanceof EntityPlayer player && !source.isUnblockable()
            && ItemElainaBrooch.overridesArmor(player)) {
            double reduction = Math.min(25, ItemElainaBrooch.getArmorValue()) / 25.0D;
            cir.setReturnValue((float) (damage * (1.0D - reduction)));
        }
    }
}
