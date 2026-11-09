package com.science.gtnl.common.wireless;

import java.nio.charset.StandardCharsets;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.DimensionManager;

import com.google.common.hash.Hashing;
import com.science.gtnl.common.wireless.WirelessChannelManager.Address;

import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;

public class WirelessDeviceDisplay {

    private WirelessDeviceDisplay() {}

    static long id(Address address, IGridNode node) {
        ItemStack icon = node.getGridBlock()
            .getMachineRepresentation();
        String identity = icon == null || icon.getItem() == null ? node.getMachine()
            .getClass()
            .getName() : Item.itemRegistry.getNameForObject(icon.getItem()) + ":" + icon.getItemDamage();
        return Hashing.murmur3_128()
            .newHasher()
            .putInt(address.dimension())
            .putInt(address.x())
            .putInt(address.y())
            .putInt(address.z())
            .putInt(
                address.side()
                    .ordinal())
            .putString(identity, StandardCharsets.UTF_8)
            .hash()
            .asLong();
    }

    static boolean stillExists(long id, Address address) {
        var world = DimensionManager.getWorld(address.dimension());
        if (world == null || !world.blockExists(address.x(), address.y(), address.z())) return true;
        TileEntity tile = world.getTileEntity(address.x(), address.y(), address.z());
        if (tile == null) return false;
        // Invalid tiles and missing nodes may be rebuilding; only confirmed absence or replacement is discarded.
        if (tile.isInvalid()) return true;
        IGridNode node;
        if (tile instanceof IPartHost host) {
            IPart part = host.getPart(address.side());
            if (part == null) return false;
            node = part.getGridNode();
        } else if (tile instanceof IGridHost host) node = host.getGridNode(address.side());
        else return false;
        return node == null || id(address, node) == id;
    }
}
