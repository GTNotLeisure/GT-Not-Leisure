package com.science.gtnl.mixins.late.blockRenderer6343;

import java.util.function.Consumer;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.science.gtnl.api.IStructureScanProvider;

import blockrenderer6343.client.world.ObserverWorld;
import blockrenderer6343.integration.gregtech.GTConstructableScan;

@Mixin(value = GTConstructableScan.class, remap = false)
public abstract class MixinGTConstructableScan {

    @WrapOperation(
        method = "run",
        at = @At(
            value = "INVOKE",
            target = "Lblockrenderer6343/client/world/ObserverWorld;estimateTierFromConstructable(Ljava/util/function/Consumer;Lcom/gtnewhorizon/structurelib/alignment/constructable/IConstructable;)I"),
        require = 1)
    private int gtnl$useDeclaredStructureTier(ObserverWorld world, Consumer<ItemStack> result, IConstructable machine,
        Operation<Integer> original) {
        if (machine instanceof IStructureScanProvider provider
            && machine.getStructureDefinition() instanceof StructureDefinition<?>) {
            int tier = provider.getStructureScanTier();
            // The first scan already indexed every material from the compiled structure elements.
            if (tier > 0) return tier;
        }
        return original.call(world, result, machine);
    }
}
