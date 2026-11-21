package com.science.gtnl.common.item.items;

import static net.minecraft.util.EnumChatFormatting.AQUA;
import static net.minecraft.util.EnumChatFormatting.GRAY;
import static net.minecraft.util.EnumChatFormatting.YELLOW;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.science.gtnl.client.projector.ProjectorClient;
import com.science.gtnl.client.projector.ProjectorGui;
import com.science.gtnl.common.projector.ProjectorMachines;
import com.science.gtnl.common.projector.ProjectorPlan;

import baubles.api.BaubleType;
import baubles.api.expanded.BaubleExpandedSlots;
import baubles.api.expanded.IBaubleExpanded;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class MultiblockProjector extends Item implements IBaubleExpanded, IGuiHolder<PlayerInventoryGuiData> {

    public static final String LANG = "item.gtnl.multiblock_projector.";
    public static final int GUI_WIDTH = 480, GUI_HEIGHT = 300;

    public MultiblockProjector() {
        setMaxStackSize(1);
    }

    @Override
    public ModularPanel buildUI(PlayerInventoryGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return syncManager.isClient() ? createPanel(data)
            : new ModularPanel("multiblock_projector").size(GUI_WIDTH, GUI_HEIGHT);
    }

    @SideOnly(Side.CLIENT)
    private ModularPanel createPanel(PlayerInventoryGuiData data) {
        return new ProjectorGui(data);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(PlayerInventoryGuiData data, ModularPanel panel) {
        return ((ProjectorGui) panel).screen();
    }

    @Override
    public BaubleType getBaubleType(ItemStack stack) {
        return BaubleType.RING;
    }

    @Override
    public String[] getBaubleTypes(ItemStack stack) {
        return new String[] { BaubleExpandedSlots.universalType };
    }

    @Override
    public void onWornTick(ItemStack stack, EntityLivingBase player) {}

    @Override
    public void onEquipped(ItemStack stack, EntityLivingBase player) {}

    @Override
    public void onUnequipped(ItemStack stack, EntityLivingBase player) {}

    @Override
    public boolean canEquip(ItemStack stack, EntityLivingBase player) {
        return true;
    }

    @Override
    public boolean canUnequip(ItemStack stack, EntityLivingBase player) {
        return true;
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        // Returning true on the client keeps the click from reaching the block and the server.
        if (!world.isRemote) return false;
        clientClick(player);
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) clientClick(player);
        return stack;
    }

    @SideOnly(Side.CLIENT)
    private static void clientClick(EntityPlayer player) {
        ProjectorClient.rightClick(player.isSneaking());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        ProjectorPlan plan = ProjectorPlan.read(stack);
        if (plan.hasMachine()) {
            ItemStack machine = ProjectorMachines.stackOf(plan.machine);
            tooltip.add(
                AQUA + StatCollector.translateToLocalFormatted(
                    LANG + "machine",
                    machine == null ? "?" : machine.getDisplayName(),
                    StatCollector.translateToLocal(LANG + "state." + plan.state.name())));
        } else {
            tooltip.add(GRAY + StatCollector.translateToLocal(LANG + "no_machine"));
        }
        tooltip.add(GRAY + StatCollector.translateToLocal(LANG + "bauble_hint"));
        if (GuiScreen.isShiftKeyDown()) {
            for (int line = 0; line < 7; line++)
                tooltip.add(GRAY + StatCollector.translateToLocal(LANG + "tooltip." + line));
        } else {
            tooltip.add(YELLOW + StatCollector.translateToLocal(LANG + "more_hint"));
        }
    }
}
