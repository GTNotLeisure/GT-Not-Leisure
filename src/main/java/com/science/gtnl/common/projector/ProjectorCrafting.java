package com.science.gtnl.common.projector;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.science.gtnl.compat.baubles.BaublesInventory;

import appeng.api.AEApi;
import appeng.api.config.SecurityPermissions;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.AEBaseContainer;
import appeng.container.PrimaryGui;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.core.sync.GuiBridge;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.Platform;
import appeng.util.PlayerInventoryUtil;

public class ProjectorCrafting {

    private ProjectorCrafting() {}

    // AE calls open() after the player starts or cancels a craft; each call shows the next missing material.
    public static class Chain extends PrimaryGui {

        public final Deque<IAEItemStack> queue;
        public final int terminalSlot;
        public WeakReference<Container> opened = new WeakReference<>(null);

        public Chain(ItemStack icon, int terminalSlot, Deque<IAEItemStack> queue) {
            super(GuiBridge.GUI_CRAFTING_AMOUNT, icon, null, ForgeDirection.UNKNOWN);
            this.terminalSlot = terminalSlot;
            this.queue = queue;
            setSlotIndex(terminalSlot);
        }

        @Override
        public void open(EntityPlayer player) {
            if (!(player instanceof EntityPlayerMP serverPlayer) || !openNext(serverPlayer, this)) player.closeScreen();
        }
    }

    public static Chain start(EntityPlayerMP player, List<ItemStack> missing) {
        ItemStack terminal = PlayerInventoryUtil.getFirstWirelessTerminal(player);
        int slot = terminal == null ? -1 : slotOf(player, terminal);
        if (slot < 0) return null;
        IWirelessTermHandler handler = AEApi.instance()
            .registries()
            .wireless()
            .getWirelessTerminalHandler(terminal);
        if (handler == null) return null;
        WirelessTerminalGuiObject host = new WirelessTerminalGuiObject(
            handler,
            terminal,
            player,
            player.worldObj,
            slot,
            0,
            0);
        if (!host.rangeCheck()) return null;
        IGrid grid = host.getGrid();
        if (grid == null) return null;
        ISecurityGrid security = grid.getCache(ISecurityGrid.class);
        if (security != null && !security.hasPermission(player, SecurityPermissions.CRAFT)) return null;
        ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
        if (crafting == null) return null;
        Deque<IAEItemStack> queue = new ArrayDeque<>();
        for (ItemStack stack : missing) {
            IAEItemStack wanted = AEApi.instance()
                .storage()
                .createItemStack(stack);
            if (wanted != null && !crafting.getCraftingFor(wanted, null, -1, player.worldObj)
                .isEmpty()) queue.add(wanted);
        }
        if (queue.isEmpty()) return null;
        Chain chain = new Chain(terminal.copy(), slot, queue);
        return openNext(player, chain) ? chain : null;
    }

    // AE's back and cancel buttons give the screen they open a return screen of their own right after the chain opened
    // it; this puts the chain back before the player can act on that screen.
    public static boolean isOpen(EntityPlayerMP player, Chain chain) {
        Container open = player.openContainer;
        if (!(open instanceof AEBaseContainer container)) return false;
        if (container.getPrimaryGui() == chain) return true;
        if (open != chain.opened.get()) return false;
        container.setPrimaryGui(chain);
        return true;
    }

    private static boolean openNext(EntityPlayerMP player, Chain chain) {
        IAEItemStack next = chain.queue.poll();
        if (next == null) return false;
        Platform.openGUI(player, null, null, GuiBridge.GUI_CRAFTING_AMOUNT, chain.terminalSlot);
        if (!(player.openContainer instanceof ContainerCraftAmount amount)) return false;
        amount.setPrimaryGui(chain);
        amount.setItemToCraft(next.copy());
        amount.setInitialCraftAmount(next.getStackSize());
        amount.detectAndSendChanges();
        chain.opened = new WeakReference<>(amount);
        return true;
    }

    // AE's slot numbering for item screens: main inventory 0-35, Baubles slots from Platform.baublesSlotsOffset.
    private static int slotOf(EntityPlayerMP player, ItemStack terminal) {
        if (Platform.isBaublesLoaded) {
            IInventory worn = BaublesInventory.of(player);
            if (worn != null) {
                for (int i = 0; i < worn.getSizeInventory(); i++) {
                    if (worn.getStackInSlot(i) == terminal) return Platform.baublesSlotsOffset + i;
                }
            }
        }
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            if (player.inventory.mainInventory[i] == terminal) return i;
        }
        return -1;
    }
}
