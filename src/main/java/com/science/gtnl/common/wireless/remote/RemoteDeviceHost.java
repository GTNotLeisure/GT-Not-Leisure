package com.science.gtnl.common.wireless.remote;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;

public record RemoteDeviceHost(TileEntity tile, @Nullable IPart part, ForgeDirection side) {

    public static @Nullable RemoteDeviceHost resolve(@Nullable TileEntity tile, ForgeDirection side) {
        if (tile == null || tile.isInvalid()) return null;
        if (!(tile instanceof IPartHost host)) return new RemoteDeviceHost(tile, null, side);
        IPart part = host.getPart(side);
        return part == null ? null : new RemoteDeviceHost(tile, part, side);
    }

    public Object device() {
        return part != null ? part : tile;
    }

    public boolean owns(IGridNode node) {
        if (part != null) return part.getGridNode() == node || part.getExternalFacingNode() == node;
        return tile instanceof IGridHost host && host.getGridNode(side) == node;
    }

    public boolean same(RemoteDeviceHost other) {
        return other != null && tile == other.tile && part == other.part;
    }
}
