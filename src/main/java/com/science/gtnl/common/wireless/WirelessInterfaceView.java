package com.science.gtnl.common.wireless;

import static com.gtnewhorizon.gtnhlib.capability.Capabilities.getCapability;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.util.ForgeDirection;

import com.science.gtnl.api.mixinHelper.IDualityInterface;
import com.science.gtnl.common.part.PartUniversalExportBus;
import com.science.gtnl.common.part.PartUniversalImportBus;
import com.science.gtnl.common.part.PartUniversalStorageBus;
import com.science.gtnl.common.part.universal.UniversalBusHelper;

import appeng.api.AEApi;
import appeng.api.crafting.ICraftingIconProvider;
import appeng.api.interfaces.IInterfaceNameProvider;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.MachineSource;
import appeng.api.parts.IStorageBus;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.IInterfaceViewable;
import appeng.helpers.ICustomNameObject;
import appeng.helpers.IInterfaceHost;
import appeng.parts.AEBasePart;
import appeng.parts.automation.PartSharedItemBus;
import appeng.parts.p2p.IPartGT5Power;
import appeng.parts.p2p.PartP2PTunnel;
import appeng.parts.p2p.PartP2PTunnelME;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.util.GTUtility;
import gregtech.crossmod.ae2.ChatComponentGhostCircuitSuffix;
import gregtech.crossmod.ae2.ChatComponentNonConsumedItemsSuffix;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public class WirelessInterfaceView {

    private final Map<Object, List<MTEHatch>> targets = new Reference2ObjectOpenHashMap<>();
    private final Map<Set<IGridNode>, List<Name>> names = new Object2ObjectOpenHashMap<>();
    private final Map<Object, Name> deviceNames = new Reference2ObjectOpenHashMap<>();

    record Name(String rawName, String suffix, ItemStack icon, ItemStack configurationIcon, boolean connected,
        String customName) {

        Name(String rawName, IChatComponent suffix, ItemStack icon, boolean connected) {
            this(rawName, suffix, icon, connected, "");
        }

        Name(String rawName, IChatComponent suffix, ItemStack icon, boolean connected, String customName) {
            this(
                rawName,
                suffix == null ? "" : IChatComponent.Serializer.func_150696_a(suffix),
                icon,
                WirelessInterfaceView.configurationIcon(suffix),
                connected,
                customName);
        }

    }

    static boolean attached(Object device) {
        return device instanceof IInterfaceHost || device instanceof AEBasePart part && attachedPart(part);
    }

    private static boolean attachedPart(AEBasePart part) {
        return part instanceof IStorageBus || part instanceof PartSharedItemBus<?>
            || part instanceof IPartGT5Power
            || part instanceof PartP2PTunnel<?> && !(part instanceof PartP2PTunnelME);
    }

    List<MTEHatch> hatches(Object device) {
        if (!targets.containsKey(device)) targets.put(device, readHatches(device));
        return targets.get(device);
    }

    private static List<MTEHatch> readHatches(Object device) {
        if (device instanceof IInterfaceHost host) return readHatches(host.getTileEntity(), host.getTargets());
        if (!(device instanceof AEBasePart part) || part.getSide() == null) return null;
        var host = part.getHost();
        return readHatches(host == null ? null : host.getTile(), EnumSet.of(part.getSide()));
    }

    private static List<MTEHatch> readHatches(TileEntity tile, Set<ForgeDirection> sides) {
        if (tile == null || tile.isInvalid() || tile.getWorldObj() == null) return null;
        var world = tile.getWorldObj();
        List<MTEHatch> hatches = new ObjectArrayList<>();
        for (ForgeDirection side : sides) {
            if (side == ForgeDirection.UNKNOWN) continue;
            int x = tile.xCoord + side.offsetX, y = tile.yCoord + side.offsetY, z = tile.zCoord + side.offsetZ;
            if (!world.blockExists(x, y, z)) return null;
            var target = world.getTileEntity(x, y, z);
            if (target != null && target.isInvalid()) return null;
            // AE checks all horizontal neighbors when resolving double chests.
            if (target instanceof TileEntityChest && !world.checkChunksExist(x - 1, y, z - 1, x + 1, y, z + 1))
                return null;
            if (target instanceof IGregTechTileEntity base && base.getMetaTileEntity() instanceof MTEHatch hatch)
                hatches.add(hatch);
        }
        return hatches;
    }

    List<Name> names(IWirelessLinkView link) {
        return names.computeIfAbsent(link.members(), this::readNames);
    }

    Name name(IGridNode node) {
        Object machine = machine(node);
        if (!deviceNames.containsKey(machine)) deviceNames.put(machine, readName(machine));
        return deviceNames.get(machine);
    }

    private List<Name> readNames(Set<IGridNode> members) {
        Set<Object> seen = new ReferenceOpenHashSet<>();
        Map<String, Name> names = new Object2ObjectAVLTreeMap<>();
        for (var node : members) {
            Object machine = machine(node);
            if (!seen.add(machine)) continue;
            Name name = name(node);
            if (name != null) names.putIfAbsent(name.rawName() + '\0' + name.suffix() + '\0' + name.customName(), name);
        }
        return List.copyOf(names.values());
    }

    private static Object machine(IGridNode node) {
        Object machine = node.getMachine();
        return !(machine instanceof IInterfaceViewable) && machine instanceof IGregTechTileEntity base
            ? base.getMetaTileEntity()
            : machine;
    }

    private Name readName(Object machine) {
        if (machine instanceof IInterfaceHost host) return ((IDualityInterface) host.getInterfaceDuality())
            .gtnl$withoutCustomName(() -> readInterfaceName(host));
        if (machine instanceof IInterfaceViewable host) return readInterfaceName(host);
        if (machine instanceof AEBasePart part && attachedPart(part)) return readTargetName(part, busTypes(part));
        if (machine instanceof MTEHatch hatch) return hatchName(hatch, null, false);
        return null;
    }

    private static List<IAEStackType<?>> busTypes(AEBasePart part) {
        if (part instanceof PartUniversalStorageBus || part instanceof PartUniversalImportBus
            || part instanceof PartUniversalExportBus) return UniversalBusHelper.getTypes();
        if (part instanceof IStorageBus bus) return List.of(bus.getStackType());
        if (part instanceof PartSharedItemBus<?>bus) return List.of(bus.getStackType());
        return null;
    }

    // Machine groups already name the controller, so every hatch below them is named after itself.
    private static Name hatchName(MTEHatch hatch, IChatComponent suffix, boolean connected) {
        ItemStack icon = hatch.getStackForm(1);
        if (icon == null || icon.getItem() == null) return null;
        String rawName = icon.getUnlocalizedName();
        String customName = hatch instanceof ICustomNameObject named && named.hasCustomName() ? named.getCustomName()
            : "";
        if (suffix == null && hatch.getBaseMetaTileEntity() instanceof IInterfaceNameProvider provider)
            suffix = provider.getInterfaceNameSuffix();
        return new Name(rawName, suffix, icon, connected, customName);
    }

    private static ItemStack configurationIcon(IChatComponent suffix) {
        if (suffix == null) return null;
        Integer circuit = null;
        for (Object entry : suffix) {
            // Prefer the mold or lens when the same input also has a circuit setting.
            if (entry instanceof ChatComponentNonConsumedItemsSuffix items) {
                for (ItemStack stack : items.items) if (stack != null && stack.getItem() != null) return stack;
            } else if (circuit == null && entry instanceof ChatComponentGhostCircuitSuffix circuits
                && !circuits.circuitNumbers.isEmpty()) circuit = circuits.circuitNumbers.get(0);
        }
        return circuit == null ? null : GTUtility.getIntegratedCircuit(circuit);
    }

    private Name readInterfaceName(IInterfaceViewable host) {
        var tile = host.getTileEntity();
        if (tile == null || tile.isInvalid() || tile.getWorldObj() == null) return null;
        if (host instanceof IInterfaceHost iface && (hatches(iface) == null || !canReadIcons(iface))) return null;
        List<MTEHatch> hatches = host instanceof IInterfaceHost iface ? hatches(iface)
            : host instanceof MTEHatch hatch ? List.of(hatch) : List.of();
        if (hatches.size() == 1) return hatchName(hatches.get(0), host.getNameSuffix(), host instanceof IInterfaceHost);
        ItemStack icon = host.getDisplayRep();
        // An interface without a target reports "Nothing"; retain its normal device label instead.
        if (host instanceof IInterfaceHost && (icon == null || icon.getItem() == null)) return null;
        if (icon == null) icon = host.getSelfRep();
        String rawName = host.getRawName();
        if (rawName == null || rawName.isEmpty()) return null;
        Name name = new Name(rawName, host.getNameSuffix(), icon, host instanceof IInterfaceHost);
        // Custom names can hide the suffix while the GT tile still exposes its configuration.
        if (name.configurationIcon() == null && tile instanceof IInterfaceNameProvider provider) return new Name(
            name.rawName(),
            name.suffix(),
            icon,
            configurationIcon(provider.getInterfaceNameSuffix()),
            name.connected(),
            name.customName());
        return name;
    }

    private static Name readTargetName(AEBasePart part, List<IAEStackType<?>> types) {
        var host = part.getHost();
        var tile = host == null ? null : host.getTile();
        ForgeDirection side = part.getSide();
        if (tile == null || tile.isInvalid()
            || tile.getWorldObj() == null
            || side == null
            || side == ForgeDirection.UNKNOWN) return null;
        var world = tile.getWorldObj();
        int x = tile.xCoord + side.offsetX, y = tile.yCoord + side.offsetY, z = tile.zCoord + side.offsetZ;
        if (!world.blockExists(x, y, z)) return null;
        var target = world.getTileEntity(x, y, z);
        if (target == null || target.isInvalid()) return null;
        if (target instanceof TileEntityChest && !world.checkChunksExist(x - 1, y, z - 1, x + 1, y, z + 1)) return null;
        if (types != null) {
            // Select AE's adapter without constructing an inventory or resetting the bus's live storage handler.
            var storage = AEApi.instance()
                .registries()
                .externalStorage();
            var source = new MachineSource(part);
            if (types.stream()
                .noneMatch(type -> storage.getHandler(target, side.getOpposite(), type, source) != null)) return null;
        }
        if (target instanceof IGregTechTileEntity base && base.getMetaTileEntity() instanceof MTEHatch hatch)
            return hatchName(hatch, null, true);
        var iconProvider = getCapability(target, ICraftingIconProvider.class);
        ItemStack icon = iconProvider == null ? null : iconProvider.getMachineCraftingIcon();
        if (icon == null || icon.getItem() == null) {
            var block = world.getBlock(x, y, z);
            icon = new ItemStack(block, 1, block.getDamageValue(world, x, y, z));
        }
        if (icon.getItem() == null) return null;
        var nameProvider = getCapability(target, IInterfaceNameProvider.class);
        return new Name(
            icon.getUnlocalizedName(),
            nameProvider == null ? null : nameProvider.getInterfaceNameSuffix(),
            icon,
            true);
    }

    private static boolean canReadIcons(IInterfaceHost host) {
        var tile = host.getTileEntity();
        for (ForgeDirection side : host.getTargets()) {
            int x = tile.xCoord + side.offsetX * 2, z = tile.zCoord + side.offsetZ * 2;
            // AE's fallback icon ray trace extends one block beyond the already checked neighbor.
            if (((x >> 4) != ((x - side.offsetX) >> 4) || (z >> 4) != ((z - side.offsetZ) >> 4)) && !tile.getWorldObj()
                .blockExists(x, tile.yCoord + side.offsetY * 2, z)) return false;
        }
        return true;
    }
}
