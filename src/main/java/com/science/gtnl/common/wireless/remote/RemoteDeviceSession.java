package com.science.gtnl.common.wireless.remote;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;

import com.science.gtnl.common.wireless.WirelessCardBinding;
import com.science.gtnl.common.wireless.WirelessCardInventory.TrackedCard;
import com.science.gtnl.common.wireless.WirelessChannelManager.Address;

import appeng.api.networking.IGridNode;

public class RemoteDeviceSession {

    public final long id;
    public final EntityPlayerMP player;
    public final TrackedCard card;
    public final WirelessCardBinding binding;
    public final IGridNode node;
    public final Address address;
    public final RemoteDeviceHost host;
    public Container container;

    public RemoteDeviceSession(long id, EntityPlayerMP player, TrackedCard card, WirelessCardBinding binding,
        IGridNode node, Address address, RemoteDeviceHost host) {
        this.id = id;
        this.player = player;
        this.card = card;
        this.binding = binding;
        this.node = node;
        this.address = address;
        this.host = host;
    }

    public RemoteDeviceState check() {
        if (!player.isEntityAlive()) return RemoteDeviceState.STALE;
        if (!card.carriedBy(player)) return RemoteDeviceState.CARD;
        var located = RemoteDeviceSessions.locate(node);
        if (located.state() != RemoteDeviceState.OPEN) return located.state();
        if (!host.same(located.host())) return RemoteDeviceState.STALE;
        return RemoteDeviceSessions.checkAccess(player, binding.frequency(), node);
    }

    public boolean isOpen() {
        return container != null && player.openContainer == container;
    }
}
