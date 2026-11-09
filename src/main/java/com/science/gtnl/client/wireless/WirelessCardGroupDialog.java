package com.science.gtnl.client.wireless;

import static codechicken.nei.NEIClientUtils.cropText;
import static com.science.gtnl.client.wireless.WirelessCardGui.tr;
import static net.minecraft.client.gui.Gui.drawRect;

import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Platform;
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
        CLOSE_ROOM = 20, TITLE_HEIGHT = 14, SCOPE_HEIGHT = 12, NOTICE_HEIGHT = 18, DONE_WIDTH = 80, RENAME_WIDTH = 64,
        ROW_HEIGHT = WirelessCardGui.ROW_HEIGHT, WELL_GAP = 5, CHILDREN = 7, ICON_X = 3, TEXT_X = 24, MODE_SIZE = 16,
        MODE_GAP = 4;
    private static final int CHROME = TOP + TITLE_HEIGHT * 2
        + SCOPE_HEIGHT
        + AE2Theme.BUTTON_HEIGHT * 2
        + NOTICE_HEIGHT
        + GAP * (CHILDREN - 1)
        + BOTTOM;
    private final WirelessCardGui owner;
    private final String key;
    private final NBTTagCompound header;
    private final ItemStack icon;
    private final List<NBTTagCompound> devices;
    private final FadingNotice notice = new FadingNotice().duration(5000);

    WirelessCardGroupDialog(WirelessCardGui owner, WirelessCardClientView.GroupDetails group) {
        super("wireless_card_group");
        this.owner = owner;
        header = group.header();
        key = header.getString("key");
        icon = ItemStack.loadItemStackFromNBT(header.getCompoundTag("icon"));
        devices = group.devices();
        int width = Math.min(MAX_WIDTH, owner.xSize - 20);
        int maxRows = Math.max(1, (Math.min(MAX_HEIGHT, owner.ySize - 8) - CHROME - 2) / ROW_HEIGHT);
        int wellHeight = Math.max(1, Math.min(devices.size(), maxRows)) * ROW_HEIGHT + 2;
        size(width, CHROME + wellHeight);
        child(ButtonWidget.panelCloseButton());
        boolean renameable = header.hasKey("machineId");
        int nameWidth = width - EDGE * 2 - (renameable ? RENAME_WIDTH + GAP : 0);
        Flow nameRow = Flow.row()
            .widthRel(1f)
            .height(TITLE_HEIGHT)
            .childPadding(GAP)
            .child(
                IKey.dynamic(() -> cropText(owner.fontRendererObj, groupName(), nameWidth))
                    .asWidget()
                    .expanded()
                    .height(TITLE_HEIGHT));
        if (renameable) nameRow.child(renameButton());
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
        for (int i = 0; i < devices.size(); i++)
            list.child(new DeviceRow(devices.get(i), rowWidth, i == devices.size() - 1));
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
                .child(nameRow)
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

    private String groupName() {
        NBTTagCompound current = owner.clientView()
            .groupHeader(key);
        return WirelessCardGui.rowName(current == null ? header : current, icon);
    }

    private ButtonWidget<?> renameButton() {
        ButtonWidget<?> button = new ButtonWidget<>();
        IDrawable background = AE2Theme.button(button, () -> true, () -> false);
        return button.size(RENAME_WIDTH, TITLE_HEIGHT)
            .background(background)
            .hoverBackground(background)
            .overlay(
                AE2Theme
                    .label(button, () -> StatCollector.translateToLocal("selectWorld.rename"), () -> true, () -> false))
            .onMousePressed(pressed -> {
                if (pressed != 0) return false;
                owner.openRename(key);
                return true;
            });
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
        private final boolean last;
        private final IDrawable modeButton, modeLabel;

        private DeviceRow(NBTTagCompound device, int width, boolean last) {
            this.device = device;
            this.last = last;
            size(width, ROW_HEIGHT);
            background(IDrawable.EMPTY).hoverBackground(IDrawable.EMPTY);
            modeButton = AE2Theme.button(this, () -> true, () -> false);
            modeLabel = AE2Theme
                .label(this, () -> tr("display.short." + device.getInteger("displayMode")), () -> true, () -> false);
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
            int mode = owner.clientView()
                .displayMode(device), width = getArea().width;
            device.setInteger("displayMode", mode);
            boolean lit = isHovering();
            Palette ink = lit ? owner.highlightPalette : owner.palette;
            if (lit) owner.drawHighlight(0, width, 0, ROW_HEIGHT);
            if (!last) drawRect(
                0,
                ROW_HEIGHT - 1,
                width,
                ROW_HEIGHT,
                lit ? owner.palette.line : owner.tint(WirelessCardGui.SEPARATOR_ALPHA));
            ItemStack shown = WirelessCardGui.rowIcon(device);
            if (shown != null) owner.drawDeviceIcon(shown, ICON_X, 4);
            String modeName = tr("display.mode." + mode), state = device.getString("deviceState");
            int buttonX = width - MODE_GAP - MODE_SIZE,
                modeX = buttonX - MODE_GAP - owner.fontRendererObj.getStringWidth(modeName),
                textWidth = modeX - MODE_GAP * 2 - TEXT_X;
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            try {
                Platform.setupDrawFont();
                owner.drawRowTitle(
                    device,
                    ItemStack.loadItemStackFromNBT(device.getCompoundTag("icon")),
                    TEXT_X,
                    2,
                    textWidth,
                    ink);
                owner.fontRendererObj.drawString(
                    cropText(owner.fontRendererObj, tr("device_state." + state), textWidth),
                    TEXT_X,
                    13,
                    ink.deviceState(state));
                owner.fontRendererObj.drawString(modeName, modeX, 8, ink.link);
            } finally {
                GL11.glPopAttrib();
            }
            modeButton.draw(context, buttonX, 4, MODE_SIZE, MODE_SIZE, theme.getTheme());
            modeLabel.draw(context, buttonX, 4, MODE_SIZE, MODE_SIZE, theme.getTheme());
        }
    }
}
