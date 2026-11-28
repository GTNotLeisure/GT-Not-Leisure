package com.science.gtnl.client.gui;

import java.util.List;
import java.util.stream.IntStream;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.block.blocks.tile.TileEntityDirePatternEncoder;
import com.science.gtnl.common.gui.container.ContainerDirePatternEncoder;
import com.science.gtnl.common.packet.DirePatternHandler;
import com.science.gtnl.utils.AspectTooltipUtils;
import com.science.gtnl.utils.enums.ModsItemlist;

import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.common.config.ConfigItems;

@SideOnly(Side.CLIENT)
public class GuiDirePatternEncoder extends AEBaseGui {

    private static final ResourceLocation tex = new ResourceLocation("avaritia:textures/gui/dire_crafting_gui.png");
    private static final int[] OUTER_SLOTS = IntStream.range(0, 81)
        .filter(slot -> !ContainerDirePatternEncoder.isArcaneSlot(slot))
        .toArray();

    private GuiImgButton encodeBtn;
    private GuiImgButton clearBtn;
    private GuiTabButton direButton, arcaneButton;
    private GuiTabButton writeOn, writeOff;
    private final List<ItemStack> preview = new ObjectArrayList<>();
    private NBTTagCompound shown;

    public GuiDirePatternEncoder(InventoryPlayer ip, TileEntityDirePatternEncoder anchor) {
        super(new ContainerDirePatternEncoder(ip, anchor));
        this.ySize = 256;
        this.xSize = 238;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.encodeBtn = new GuiImgButton(this.guiLeft + 210, this.guiTop + 130, Settings.ACTIONS, ActionItems.ENCODE);
        this.buttonList.add(this.encodeBtn);
        this.clearBtn = new GuiImgButton(
            this.guiLeft + 12 + 9 * 18,
            this.guiTop + 8,
            Settings.ACTIONS,
            ActionItems.CLOSE);
        this.clearBtn.setHalfSize(true);
        this.buttonList.add(this.clearBtn);
        if (!encoder().supportsArcaneMode()) return;
        direButton = new GuiTabButton(
            guiLeft + 207,
            guiTop + 35,
            GameRegistry.findItemStack("Avaritia", "Dire_Crafting", 1),
            tr("mode.dire"),
            itemRender);
        arcaneButton = new GuiTabButton(
            guiLeft + 207,
            guiTop + 35,
            ModsItemlist.ThaumcraftArcaneWorkbench.get(1),
            tr("mode.arcane"),
            itemRender);
        buttonList.add(direButton);
        buttonList.add(arcaneButton);
        var fullPhial = new ItemStack(ConfigItems.itemEssence, 1, 1);
        ((IEssentiaContainerItem) fullPhial.getItem()).setAspects(fullPhial, new AspectList().add(Aspect.AIR, 8));
        writeOn = new GuiTabButton(guiLeft + 180, guiTop + 35, fullPhial, tr("write.on"), itemRender);
        writeOff = new GuiTabButton(
            guiLeft + 180,
            guiTop + 35,
            new ItemStack(ConfigItems.itemEssence),
            tr("write.off"),
            itemRender);
        buttonList.add(writeOn);
        buttonList.add(writeOff);
        shown = null;
        refreshPreview();
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal("gtnl.dire_encoder." + key);
    }

    private ContainerDirePatternEncoder encoder() {
        return (ContainerDirePatternEncoder) inventorySlots;
    }

    private void refreshPreview() {
        if (direButton == null) return;
        boolean supported = encoder().supportsArcaneMode();
        boolean arcane = encoder().isArcaneMode();
        boolean writing = arcane && encoder().writesEssentia();
        direButton.visible = supported && !arcane;
        arcaneButton.visible = arcane;
        writeOn.visible = writing;
        writeOff.visible = arcane && !writing;
        var costs = encoder().getAspectAmounts();
        if (costs.equals(shown)) return;
        shown = costs;
        preview.clear();
        if (Loader.isModLoaded("aspectrecipeindex")) {
            for (String key : new ObjectAVLTreeSet<>(costs.func_150296_c())) {
                Aspect aspect = Aspect.getAspect(key);
                if (aspect != null) preview.add(AspectTooltipUtils.createAspectStack(aspect, costs.getInteger(key)));
            }
        }
    }

    @Override
    public void drawScreen(int x, int y, float ticks) {
        refreshPreview();
        super.drawScreen(x, y, ticks);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        if (!encoder().isArcaneMode() || !encoder().writesEssentia()) return;
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        ItemStack hovered = null;
        for (int i = 0; i < Math.min(preview.size(), OUTER_SLOTS.length); i++) {
            int slot = OUTER_SLOTS[i];
            int x = 12 + slot % 9 * 18, y = 8 + slot / 9 * 18;
            var icon = preview.get(i);
            drawItem(x, y, icon);
            itemRender.renderItemOverlayIntoGUI(
                fontRendererObj,
                mc.getTextureManager(),
                icon,
                x,
                y,
                Integer.toString(icon.stackSize));
            if (mx >= x && mx < x + 16 && my >= y && my < y + 16) hovered = icon;
        }
        if (hovered != null) drawTooltip(mx, my, hovered.getDisplayName() + " ×" + hovered.stackSize);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.renderEngine.bindTexture(tex);
        int foo = (this.width - this.xSize) / 2;
        int bar = (this.height - this.ySize) / 2;
        // noinspection SuspiciousNameCombination
        this.drawTexturedModalRect(foo, bar, 0, 0, this.ySize, this.ySize);
        if (encoder().isArcaneMode()) {
            int used = encoder().writesEssentia() ? preview.size() : 0;
            for (int i = used; i < OUTER_SLOTS.length; i++) {
                int slot = OUTER_SLOTS[i];
                int x = foo + 12 + slot % 9 * 18, y = bar + 8 + slot / 9 * 18;
                drawRect(x, y, x + 16, y + 16, 0x88000000);
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == writeOn || button == writeOff) {
            ScienceNotLeisure.network.sendToServer(new DirePatternHandler((byte) 6, button == writeOff));
            return;
        }
        if (button == direButton || button == arcaneButton)
            ScienceNotLeisure.network.sendToServer(new DirePatternHandler((byte) 3));
        if (button == encodeBtn)
            ScienceNotLeisure.network.sendToServer(new DirePatternHandler((byte) 0, isShiftKeyDown()));
        if (button == clearBtn) ScienceNotLeisure.network.sendToServer(new DirePatternHandler((byte) 1));
        super.actionPerformed(button);
    }
}
