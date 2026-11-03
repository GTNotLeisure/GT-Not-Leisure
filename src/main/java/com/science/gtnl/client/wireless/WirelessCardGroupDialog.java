package com.science.gtnl.client.wireless;

import static codechicken.nei.NEIClientUtils.cropText;
import static com.science.gtnl.client.wireless.WirelessCardGui.tr;

import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnllib.gui.theme.AE2Theme;
import com.gtnllib.gui.widget.FadingNotice;
import com.gtnllib.gui.widget.GTNLListWidget;
import com.gtnllib.gui.widget.HandleScrollData;
import com.science.gtnl.client.wireless.WirelessCardGui.Palette;
import com.science.gtnl.common.wireless.WirelessCardBinding;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class WirelessCardGroupDialog extends Dialog<Void> {

    private static final int MAX_WIDTH = 440, MAX_HEIGHT = 300, EDGE = 10, TOP = 8, BOTTOM = 6, GAP = 3,
        CLOSE_ROOM = 20, TITLE_HEIGHT = 14, SCOPE_HEIGHT = 12, NOTICE_HEIGHT = 18, DONE_WIDTH = 80, ROW_HEIGHT = 32,
        WELL_GAP = 5, CHILDREN = 7;
    private static final int CHROME = TOP + TITLE_HEIGHT * 2
        + SCOPE_HEIGHT
        + AE2Theme.BUTTON_HEIGHT * 2
        + NOTICE_HEIGHT
        + GAP * (CHILDREN - 1)
        + BOTTOM;
    private final WirelessCardGui owner;
    private final List<NBTTagCompound> devices;
    private final FadingNotice notice = new FadingNotice().duration(5000);

    WirelessCardGroupDialog(WirelessCardGui owner, WirelessCardClientView.GroupDetails group) {
        super("wireless_card_group");
        this.owner = owner;
        devices = group.devices();
        int width = Math.min(MAX_WIDTH, owner.xSize - 20);
        int maxRows = Math.max(1, (Math.min(MAX_HEIGHT, owner.ySize - 8) - CHROME - 2) / ROW_HEIGHT);
        int wellHeight = Math.max(1, Math.min(devices.size(), maxRows)) * ROW_HEIGHT + 2;
        size(width, CHROME + wellHeight);
        child(ButtonWidget.panelCloseButton());
        String name = WirelessCardGui.rowName(
            group.header(),
            ItemStack.loadItemStackFromNBT(
                group.header()
                    .getCompoundTag("icon")));
        Flow modes = Flow.row()
            .widthRel(1f)
            .height(AE2Theme.BUTTON_HEIGHT)
            .childPadding(GAP + 1);
        for (int mode = 0; mode < WirelessCardBinding.DISPLAY_MODES; mode++) modes.child(
            modeButton(mode).expanded()
                .height(AE2Theme.BUTTON_HEIGHT));
        var scroll = new HandleScrollData(owner.variant).rows(ROW_HEIGHT);
        ParentWidget<?> box = new ParentWidget<>().widthRel(1f)
            .height(wellHeight);
        if (owner.variant != null) box.child(
            new Widget<>().left(0)
                .top(0)
                .bottom(0)
                .right(scroll.getThickness() + WELL_GAP)
                .background(owner.variant.inset));
        ListWidget<IWidget, ?> list = new GTNLListWidget<>().scrollDirection(scroll)
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .left(1)
            .top(1)
            .bottom(1)
            .right(0);
        int rowWidth = width - EDGE * 2 - scroll.getThickness() - WELL_GAP - 2;
        for (NBTTagCompound device : devices) list.child(new DeviceRow(device, rowWidth));
        box.child(list);
        ButtonWidget<?> done = new ButtonWidget<>();
        done.size(DONE_WIDTH, AE2Theme.BUTTON_HEIGHT)
            .overlay(AE2Theme.label(done, () -> StatCollector.translateToLocal("gui.done"), () -> true, () -> false))
            .onMousePressed(button -> {
                if (button != 0) return false;
                closeIfOpen();
                return true;
            });
        child(
            Flow.column()
                .left(EDGE)
                .right(EDGE)
                .top(TOP)
                .bottom(BOTTOM)
                .childPadding(GAP)
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .child(
                    IKey.str(tr("display.group_title"))
                        .asWidget()
                        .widthRel(1f)
                        .marginRight(CLOSE_ROOM)
                        .height(TITLE_HEIGHT))
                .child(
                    IKey.str(cropText(owner.fontRendererObj, name, width - EDGE * 2))
                        .asWidget()
                        .widthRel(1f)
                        .height(TITLE_HEIGHT))
                .child(
                    IKey.str(tr("display.scope", devices.size()))
                        .asWidget()
                        .widthRel(1f)
                        .height(SCOPE_HEIGHT))
                .child(modes)
                .child(box)
                .child(
                    notice.widthRel(1f)
                        .height(NOTICE_HEIGHT))
                .child(
                    Flow.row()
                        .widthRel(1f)
                        .height(AE2Theme.BUTTON_HEIGHT)
                        .mainAxisAlignment(Alignment.MainAxis.END)
                        .child(done)));
    }

    private ButtonWidget<?> modeButton(int mode) {
        ButtonWidget<?> button = new ButtonWidget<>();
        BooleanSupplier selected = () -> !devices.isEmpty() && devices.stream()
            .allMatch(
                device -> owner.clientView()
                    .displayMode(device) == mode);
        var background = AE2Theme.button(button, () -> !devices.isEmpty(), selected);
        return button.background(background)
            .hoverBackground(background)
            .overlay(AE2Theme.label(button, () -> tr("display.mode." + mode), () -> !devices.isEmpty(), selected))
            .tooltipBuilder(tooltip -> {
                tooltip.addLine(tr("display.apply_all", devices.size(), tr("display.mode." + mode)));
                tooltip.addLine(tr("display.auto_hint"));
                tooltip.addLine(tr("display.fallback_hint"));
            })
            .onMousePressed(pressed -> {
                if (pressed != 0 || devices.isEmpty()) return false;
                if (!owner.clientView()
                    .setDisplayMode(devices, mode))
                    owner.receive(
                        new ChatComponentTranslation(
                            WirelessCardGui.KEY_PREFIX + "display.limit",
                            WirelessCardBinding.MAX_DISPLAY_ENTRIES));
                return true;
            });
    }

    void receive(String text, int color) {
        notice.show(text, color);
    }

    @Override
    public boolean onKeyPressed(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            closeIfOpen();
            return true;
        }
        return super.onKeyPressed(character, key);
    }

    private class DeviceRow extends ButtonWidget<DeviceRow> {

        private final NBTTagCompound device;

        private DeviceRow(NBTTagCompound device, int width) {
            this.device = device;
            size(width, 32);
            IDrawable row = AE2Theme.listRow(this::isHovering);
            background(row).hoverBackground(row);
            onMousePressed(button -> {
                if (button != 0) return false;
                owner.clientView()
                    .cycleDisplayMode(device);
                return true;
            });
            tooltipAutoUpdate(true).tooltipBuilder(tooltip -> {
                ItemStack own = ItemStack.loadItemStackFromNBT(device.getCompoundTag("icon"));
                if (own != null) tooltip.addLine(own.getDisplayName());
                tooltip.addLine(device.getString("targetName"));
                tooltip.addLine(device.getString("positionLabel"));
                tooltip.addLine(
                    tr(
                        "display.mode",
                        tr(
                            "display.mode." + owner.clientView()
                                .displayMode(device))));
                tooltip.addLine(tr("display.cycle_hint"));
            });
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            device.setInteger(
                "displayMode",
                owner.clientView()
                    .displayMode(device));
            ItemStack icon = WirelessCardGui.rowIcon(device);
            if (icon != null) owner.drawDeviceIcon(icon, 3, 8);
            String mode = tr("display.mode." + device.getInteger("displayMode"));
            int modeWidth = owner.fontRendererObj.getStringWidth(mode), textWidth = getArea().width - modeWidth - 34;
            String name = WirelessCardGui
                .rowName(device, ItemStack.loadItemStackFromNBT(device.getCompoundTag("icon")));
            Palette ink = isHovering() ? owner.highlightPalette : owner.palette;
            boolean onRow = owner.variant != null && !isHovering();
            int nameColor = onRow ? owner.variant.colors.buttonText() : ink.name,
                positionColor = onRow ? owner.variant.colors.buttonText() : ink.suffix,
                modeColor = onRow ? owner.variant.colors.buttonText() : ink.link;
            owner.drawText(cropText(owner.fontRendererObj, name, textWidth), 24, 5, nameColor, false);
            owner.drawText(
                cropText(owner.fontRendererObj, device.getString("positionLabel"), textWidth),
                24,
                18,
                positionColor,
                false);
            owner.drawText(mode, getArea().width - modeWidth - 4, 11, modeColor, false);
        }
    }
}
