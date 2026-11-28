package com.science.gtnl.common.projector;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.science.gtnl.compat.baubles.BaublesInventory;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.Platform;

// The ME network behind the first linked wireless terminal the player carries, reached the way AE's own terminal
// screen reaches it, so its range and security rules apply.
public class ProjectorNetwork {

    public record Link(IGrid grid, PlayerSource source, int terminalSlot) {}

    public static final double MIN_POWER = 0.5;

    private ProjectorNetwork() {}

    // Every carried terminal is tried in turn, so an unlinked, empty or out-of-range one does not hide a working one.
    public static Link link(EntityPlayerMP player, SecurityPermissions... permissions) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < player.inventory.mainInventory.length; i++) slots.add(i);
        IInventory worn = Platform.isBaublesLoaded ? BaublesInventory.of(player) : null;
        if (worn != null) for (int i = 0; i < worn.getSizeInventory(); i++) slots.add(Platform.baublesSlotsOffset + i);
        for (int slot : slots) {
            ItemStack terminal = slot >= Platform.baublesSlotsOffset && worn != null
                ? worn.getStackInSlot(slot - Platform.baublesSlotsOffset)
                : slot < player.inventory.mainInventory.length ? player.inventory.mainInventory[slot] : null;
            Link link = terminal == null ? null : tryTerminal(player, terminal, slot, permissions);
            if (link != null) return link;
        }
        return null;
    }

    private static Link tryTerminal(EntityPlayerMP player, ItemStack terminal, int slot,
        SecurityPermissions[] permissions) {
        if (!AEApi.instance()
            .registries()
            .wireless()
            .isWirelessTerminal(terminal)) return null;
        IWirelessTermHandler handler = AEApi.instance()
            .registries()
            .wireless()
            .getWirelessTerminalHandler(terminal);
        if (handler == null || !handler.hasPower(player, MIN_POWER, terminal)) return null;
        WirelessTerminalGuiObject host = new WirelessTerminalGuiObject(
            handler,
            terminal,
            player,
            player.worldObj,
            slot,
            0,
            0);
        if (!host.rangeCheck()) return null;
        IGridNode node = host.getActionableNode();
        IGrid grid = host.getGrid();
        if (grid == null || node == null || !node.isActive()) return null;
        ISecurityGrid security = grid.getCache(ISecurityGrid.class);
        for (SecurityPermissions permission : permissions)
            if (security != null && !security.hasPermission(player, permission)) return null;
        return new Link(grid, new PlayerSource(player, host), slot);
    }

    // Puts a stack into the network and gives back what did not fit, or null when all of it went in.
    public static ItemStack inject(Link link, ItemStack stack) {
        if (link == null || stack == null || stack.stackSize <= 0) return stack;
        IStorageGrid storage = link.grid()
            .getCache(IStorageGrid.class);
        IAEItemStack wanted = AEApi.instance()
            .storage()
            .createItemStack(stack);
        if (storage == null || wanted == null) return stack;
        IAEItemStack left = storage.getItemInventory()
            .injectItems(wanted, Actionable.MODULATE, link.source());
        return left == null || left.getStackSize() <= 0 ? null : left.getItemStack();
    }

}
