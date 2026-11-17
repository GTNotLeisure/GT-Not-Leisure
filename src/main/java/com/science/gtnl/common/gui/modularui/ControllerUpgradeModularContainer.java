package com.science.gtnl.common.gui.modularui;

import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.utils.item.ItemHandlerHelper;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PlayerSlotGroup;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class ControllerUpgradeModularContainer extends ModularContainer {

    public ControllerUpgradeModularContainer() {}

    @Override
    public @Nullable ItemStack slotClick(int slotId, int mouseButton, int mode, EntityPlayer player) {
        if (slotId >= inventorySlots.size()) return null;
        if (slotId >= 0) {
            ModularSlot slot = getModularSlot(slotId);
            ItemStack stored = slot.getStack();
            ItemStack held = player.inventory.getItemStack();
            if (stored != null && stored.stackSize > stored.getMaxStackSize()
                && (mode == 2 || mode == 0 && held != null && !ItemHandlerHelper.canItemStacksStack(stored, held)))
                return null;
        }
        return super.slotClick(slotId, mouseButton, mode, player);
    }

    @Override
    public List<ModularSlot> getShiftClickSlots() {
        List<ModularSlot> slots = new ObjectArrayList<>();
        for (ModularSlot slot : super.getShiftClickSlots()) {
            if (!slot.isInitialized()) continue;
            String panelName = slot.getSyncHandler()
                .getSyncManager()
                .getPanelName();
            if (getSyncManager().getMainPSM()
                .getPanelName()
                .equals(panelName)) {
                slots.add(slot);
                continue;
            }
            IPanelHandler panel = getSyncManager().getMainPSM()
                .findPanelHandlerNullable(panelName);
            if (panel != null && panel.isPanelOpen()) slots.add(slot);
        }
        return Collections.unmodifiableList(slots);
    }

    @Override
    public @Nullable ItemStack transferStackInSlot(@NotNull EntityPlayer playerIn, int index) {
        ModularSlot sourceSlot = getModularSlot(index);
        if (!isPlayerSlot(sourceSlot) || !isUpgradePanelOpen()) {
            return super.transferStackInSlot(playerIn, index);
        }

        ItemStack sourceStack = sourceSlot.getStack();
        if (sourceStack == null) return null;

        ItemStack originalStack = sourceStack.copy();
        ItemStack remainingStack = sourceStack.copy();
        transferToUpgradeInput(remainingStack);
        if (remainingStack.stackSize == originalStack.stackSize) return super.transferStackInSlot(playerIn, index);

        sourceSlot.putStack(remainingStack.stackSize > 0 ? remainingStack : null);
        sourceSlot.onSlotChange(remainingStack, originalStack);
        sourceSlot.onPickupFromSlot(playerIn, remainingStack);
        sourceSlot.onCraftShiftClick(playerIn, remainingStack);
        return originalStack;
    }

    private boolean isPlayerSlot(ModularSlot slot) {
        SlotGroup slotGroup = slot.getSlotGroup();
        return slotGroup != null && PlayerSlotGroup.NAME.equals(slotGroup.getName());
    }

    private boolean isUpgradePanelOpen() {
        IPanelHandler upgradePanel = getSyncManager().getMainPSM()
            .findPanelHandlerNullable(GTNLControllerUpgradePanels.UPGRADE_CURRENT_PANEL_KEY);
        return upgradePanel != null && upgradePanel.isPanelOpen();
    }

    private void transferToUpgradeInput(ItemStack stack) {
        List<ModularSlot> targets = getShiftClickSlots();
        for (ModularSlot targetSlot : targets) {
            if (!isUpgradeInputSlot(targetSlot) || !targetSlot.isItemValid(stack)) continue;

            ItemStack targetStack = targetSlot.getStack();
            if (targetStack == null || !targetStack.isItemEqual(stack)
                || targetStack.hasTagCompound() != stack.hasTagCompound()
                || targetStack.hasTagCompound() && !targetStack.getTagCompound()
                    .equals(stack.getTagCompound()))
                continue;

            int acceptedAmount = Math.min(stack.stackSize, targetSlot.getItemStackLimit(stack) - targetStack.stackSize);
            if (acceptedAmount <= 0) continue;

            ItemStack updatedStack = targetStack.copy();
            updatedStack.stackSize += acceptedAmount;
            targetSlot.putStack(updatedStack);
            stack.stackSize -= acceptedAmount;
            if (stack.stackSize == 0) return;
        }

        for (ModularSlot targetSlot : targets) {
            if (!isUpgradeInputSlot(targetSlot) || !targetSlot.isItemValid(stack) || targetSlot.getStack() != null)
                continue;

            int acceptedAmount = Math.min(stack.stackSize, targetSlot.getItemStackLimit(stack));
            if (acceptedAmount <= 0) continue;

            ItemStack insertedStack = stack.copy();
            insertedStack.stackSize = acceptedAmount;
            targetSlot.putStack(insertedStack);
            stack.stackSize -= acceptedAmount;
            if (stack.stackSize == 0) return;
        }
    }

    private boolean isUpgradeInputSlot(ModularSlot slot) {
        SlotGroup slotGroup = slot.getSlotGroup();
        return slot.func_111238_b() && slotGroup != null
            && GTNLControllerUpgradePanels.UPGRADE_INPUT_SLOT_GROUP.equals(slotGroup.getName());
    }
}
