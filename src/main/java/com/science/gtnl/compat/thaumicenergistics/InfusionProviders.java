package com.science.gtnl.compat.thaumicenergistics;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.me.GridAccessException;
import cpw.mods.fml.common.Optional;
import gregtech.api.enums.Mods;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.common.storage.AEEssentiaStack;
import thaumicenergistics.common.storage.AEEssentiaStackType;
import thaumicenergistics.common.tiles.TileAdvancedInfusionProvider;
import thaumicenergistics.common.tiles.TileEssentiaProvider;
import thaumicenergistics.common.tiles.TileInfusionProvider;
import thaumicenergistics.common.tiles.abstraction.TileProviderBase;

public class InfusionProviders {

    public static final String ESSENTIA_PROVIDER_NAME_KEY = "thaumicenergistics.block.essentia.provider.name";
    public static final String INFUSION_PROVIDER_NAME_KEY = "thaumicenergistics.block.infusion.provider.name";
    public static final String ADVANCED_INFUSION_PROVIDER_NAME_KEY = "thaumicenergistics.block.advanced.infusion.provider.name";

    public static boolean isAvailable() {
        return Mods.ThaumicEnergistics.isModLoaded();
    }

    @Optional.Method(modid = "thaumicenergistics")
    public static IAEStack<?> nativeIngredient(Aspect aspect, long amount) {
        return new AEEssentiaStack(aspect, amount);
    }

    @Optional.Method(modid = "thaumicenergistics")
    public static IAEStack<?> loadIngredient(NBTTagCompound tag) {
        return AEEssentiaStack.loadStackFromNBT(tag);
    }

    public static Aspect aspectOf(IAEStack<?> stack) {
        return stack != null && isAvailable() ? nativeAspect(stack) : null;
    }

    @Optional.Method(modid = "thaumicenergistics")
    private static Aspect nativeAspect(IAEStack<?> stack) {
        return stack instanceof AEEssentiaStack essentia ? essentia.getAspect() : null;
    }

    public static boolean isEssentiaProvider(TileEntity tile) {
        return tile != null && isAvailable() && essentiaProvider(tile);
    }

    @Optional.Method(modid = "thaumicenergistics")
    public static IGrid activeEssentiaGrid(TileEntity tile) {
        if (!(tile instanceof TileEssentiaProvider provider) || tile.isInvalid() || !provider.isActive()) return null;
        try {
            return provider.getProxy()
                .getGrid();
        } catch (GridAccessException ignored) {
            return null;
        }
    }

    @Optional.Method(modid = "thaumicenergistics")
    public static long extractFromEssentiaProvider(TileEntity tile, Aspect aspect, long amount, boolean simulate) {
        if (!(tile instanceof TileEssentiaProvider provider) || tile.isInvalid() || !provider.isActive()) return 0;
        var monitor = essentiaMonitor(provider);
        if (monitor == null) return 0;
        var extracted = monitor.extractItems(
            new AEEssentiaStack(aspect, amount),
            simulate ? Actionable.SIMULATE : Actionable.MODULATE,
            provider.getMachineSource());
        return extracted == null ? 0 : extracted.getStackSize();
    }

    public static boolean isInfusionProvider(TileEntity tile) {
        return isAvailable() && infusionProvider(tile);
    }

    public static long networkAmount(TileEntity provider, Aspect aspect) {
        return isAvailable() ? amountInNetwork(provider, aspect) : 0;
    }

    public static boolean order(TileEntity provider, Aspect aspect, int amount) {
        return isAvailable() && orderEssentia(provider, aspect, amount);
    }

    public static boolean take(TileEntity provider, Aspect aspect, int amount) {
        return isAvailable() && takeEssentia(provider, aspect, amount);
    }

    public static long giveBack(TileEntity provider, Aspect aspect, long amount) {
        return amount > 0 && isAvailable() ? returnEssentia(provider, aspect, amount) : 0;
    }

    @Optional.Method(modid = "thaumicenergistics")
    private static boolean essentiaProvider(TileEntity tile) {
        return tile instanceof TileEssentiaProvider;
    }

    @Optional.Method(modid = "thaumicenergistics")
    private static boolean infusionProvider(TileEntity tile) {
        return tile instanceof TileInfusionProvider;
    }

    @Optional.Method(modid = "thaumicenergistics")
    private static boolean usable(TileEntity provider) {
        if (provider instanceof TileAdvancedInfusionProvider advancedProvider) {
            return advancedProvider.matrices.isEmpty();
        }
        return provider instanceof TileInfusionProvider;
    }

    @Optional.Method(modid = "thaumicenergistics")
    private static long amountInNetwork(TileEntity provider, Aspect aspect) {
        if (!usable(provider)) return 0;
        TileInfusionProvider infusionProvider = (TileInfusionProvider) provider;
        IMEMonitor<AEEssentiaStack> monitor = essentiaMonitor(infusionProvider);
        if (monitor == null) return 0;
        // The network's listed amount can exceed what extraction returns, and a failed take after the item
        // inputs were consumed would void them, so count only what a simulated extraction can supply.
        AEEssentiaStack extractable = monitor.extractItems(
            new AEEssentiaStack(aspect, Integer.MAX_VALUE),
            Actionable.SIMULATE,
            infusionProvider.getMachineSource());
        return extractable == null ? 0 : extractable.getStackSize();
    }

    @Optional.Method(modid = "thaumicenergistics")
    private static boolean orderEssentia(TileEntity provider, Aspect aspect, int amount) {
        return usable(provider) && ((TileInfusionProvider) provider).orderSomeEssentia(aspect, amount);
    }

    @Optional.Method(modid = "thaumicenergistics")
    private static boolean takeEssentia(TileEntity provider, Aspect aspect, int amount) {
        return provider instanceof TileInfusionProvider infusionProvider
            && infusionProvider.takeFromContainer(aspect, amount);
    }

    @Optional.Method(modid = "thaumicenergistics")
    private static long returnEssentia(TileEntity provider, Aspect aspect, long amount) {
        if (!(provider instanceof TileInfusionProvider infusionProvider)) return 0;
        IMEMonitor<AEEssentiaStack> monitor = essentiaMonitor(infusionProvider);
        if (monitor == null) return 0;
        AEEssentiaStack rejected = monitor
            .injectItems(new AEEssentiaStack(aspect, amount), Actionable.MODULATE, infusionProvider.getMachineSource());
        return amount - (rejected == null ? 0 : rejected.getStackSize());
    }

    @Optional.Method(modid = "thaumicenergistics")
    @SuppressWarnings("unchecked")
    private static IMEMonitor<AEEssentiaStack> essentiaMonitor(TileProviderBase provider) {
        try {
            return (IMEMonitor<AEEssentiaStack>) provider.getProxy()
                .getStorage()
                .getMEMonitor(AEEssentiaStackType.ESSENTIA_STACK_TYPE);
        } catch (GridAccessException e) {
            return null;
        }
    }
}
