package com.science.gtnl.client.wireless;

import static codechicken.nei.NEIClientUtils.cropText;
import static com.science.gtnl.client.wireless.WirelessCardGui.tr;
import static com.science.gtnl.common.wireless.WirelessCardActions.TELEPORT;

import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.RichTooltip;
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
import com.gtnllib.gui.theme.AE2Theme.Icon;
import com.gtnllib.gui.widget.FadingNotice;
import com.gtnllib.gui.widget.GTNLListWidget;
import com.gtnllib.gui.widget.HandleScrollData;
import com.science.gtnl.client.wireless.WirelessCardClientView.DeviceGroup;
import com.science.gtnl.client.wireless.WirelessCardClientView.DeviceView;
import com.science.gtnl.client.wireless.WirelessCardClientView.Section;
import com.science.gtnl.client.wireless.WirelessCardGui.Palette;
import com.science.gtnl.client.wireless.WirelessCardRenameDialog.Target;
import com.science.gtnl.common.wireless.WirelessCardBinding;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

@SideOnly(Side.CLIENT)
public class WirelessCardDeviceDialog extends Dialog<Void> {

    private static final int MAX_WIDTH = 440, EDGE = 10, TOP = 8, BOTTOM = 6, GAP = 6, CLOSE_ROOM = 20,
        TITLE_HEIGHT = 12, NAME_HEIGHT = 14, NAME_GAP = 2, SCOPE_HEIGHT = 10, MODE_GAP = 4, DONE_WIDTH = 80,
        RENAME_WIDTH = 64, ROW_HEIGHT = WirelessCardGui.ROW_HEIGHT, WELL_GAP = 5, BUTTON_SIZE = 16, BUTTON_STEP = 18,
        COUNT_MARGIN = 6, WELL_BORDER = AE2Theme.INSET_TOP + AE2Theme.INSET_EDGE;
    private static final int CHROME = TOP + TITLE_HEIGHT
        + NAME_HEIGHT
        + NAME_GAP
        + SCOPE_HEIGHT
        + AE2Theme.BUTTON_HEIGHT * 2
        + GAP * 4
        + BOTTOM;
    private final WirelessCardGui owner;
    private final String key;
    private final ListWidget<IWidget, ?> list;
    private final HandleScrollData scroll;
    private final FadingNotice notice = new FadingNotice().duration(5000);
    private final Set<String> expanded = new ObjectOpenHashSet<>();
    private final int rowWidth, modeX, buttonsX;
    private DeviceGroup group;
    private List<Line> lines = List.of();
    private List<NBTTagCompound> devices = List.of();
    private String title = "", scope = "", layout = "";
    private long version = -1;
    private int restoredScroll;

    private record Line(NBTTagCompound data, boolean section, int depth, boolean expanded, int count, int online) {}

    WirelessCardDeviceDialog(WirelessCardGui owner, DeviceView view) {
        super("wireless_card_devices");
        this.owner = owner;
        this.key = view.key();
        expanded.addAll(view.expanded());
        restoredScroll = view.scroll();
        refreshData();
        int width = Math.min(MAX_WIDTH, owner.xSize - 20);
        int wellHeight = Math.max(1, (owner.ySize - 8 - CHROME - WELL_BORDER) / ROW_HEIGHT) * ROW_HEIGHT + WELL_BORDER;
        size(width, CHROME + wellHeight);
        child(ButtonWidget.panelCloseButton());
        scroll = new HandleScrollData(owner.variant).rows(ROW_HEIGHT);
        rowWidth = width - EDGE * 2 - scroll.getThickness() - WELL_GAP - AE2Theme.INSET_EDGE * 2;
        int modeColumn = 0;
        for (int mode = 0; mode < WirelessCardBinding.DISPLAY_MODES; mode++)
            modeColumn = Math.max(modeColumn, owner.fontRendererObj.getStringWidth(tr("display.mode." + mode)));
        modeX = rowWidth - MODE_GAP - BUTTON_SIZE;
        buttonsX = modeX - MODE_GAP - modeColumn - MODE_GAP * 2 - BUTTON_SIZE - BUTTON_STEP * 3;
        list = new GTNLListWidget<>().scrollDirection(scroll)
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .left(AE2Theme.INSET_EDGE)
            .top(AE2Theme.INSET_TOP)
            .bottom(AE2Theme.INSET_EDGE)
            .right(0);
        rebuildRows();
        ParentWidget<?> box = new ParentWidget<>().widthRel(1f)
            .height(wellHeight);
        if (owner.variant != null) box.child(
            new Widget<>().left(0)
                .top(0)
                .bottom(0)
                .right(scroll.getThickness() + WELL_GAP)
                .background(owner.variant.inset));
        box.child(list);
        boolean renameable = renameTarget() != null;
        int nameWidth = width - EDGE * 2 - (renameable ? RENAME_WIDTH + GAP : 0);
        Flow name = Flow.row()
            .widthRel(1f)
            .height(NAME_HEIGHT)
            .childPadding(GAP)
            .child(
                IKey.dynamic(() -> cropText(owner.fontRendererObj, title, nameWidth))
                    .asWidget()
                    .expanded()
                    .height(NAME_HEIGHT));
        if (renameable) name.child(renameButton());
        Flow modes = Flow.row()
            .widthRel(1f)
            .height(AE2Theme.BUTTON_HEIGHT)
            .childPadding(MODE_GAP);
        for (int mode = 0; mode < WirelessCardBinding.DISPLAY_MODES; mode++) modes.child(
            modeButton(mode).expanded()
                .height(AE2Theme.BUTTON_HEIGHT));
        ButtonWidget<?> done = textButton(() -> StatCollector.translateToLocal("gui.done"), this::closeIfOpen);
        child(
            Flow.column()
                .left(EDGE)
                .right(EDGE)
                .top(TOP)
                .bottom(BOTTOM)
                .childPadding(GAP)
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .child(
                    IKey.str(tr("devices_title"))
                        .asWidget()
                        .widthRel(1f)
                        .marginRight(CLOSE_ROOM)
                        .height(TITLE_HEIGHT))
                .child(
                    Flow.column()
                        .widthRel(1f)
                        .height(NAME_HEIGHT + NAME_GAP + SCOPE_HEIGHT)
                        .childPadding(NAME_GAP)
                        .crossAxisAlignment(Alignment.CrossAxis.START)
                        .child(name)
                        .child(
                            IKey.dynamic(() -> scope)
                                .color(owner.palette.suffix)
                                .alignment(Alignment.CenterLeft)
                                .asWidget()
                                .widthRel(1f)
                                .height(SCOPE_HEIGHT)))
                .child(modes)
                .child(box)
                .child(
                    Flow.row()
                        .widthRel(1f)
                        .height(AE2Theme.BUTTON_HEIGHT)
                        .childPadding(GAP)
                        .child(
                            notice.expanded()
                                .height(AE2Theme.BUTTON_HEIGHT))
                        .child(done.size(DONE_WIDTH, AE2Theme.BUTTON_HEIGHT))));
    }

    void receive(String text, int color) {
        notice.show(text, color);
    }

    DeviceView view() {
        return new DeviceView(key, Set.copyOf(expanded), scroll.getScroll());
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!isOpen() || isClosing()) return;
        // The list only accepts a scroll position after its first layout has measured the rows.
        if (restoredScroll > 0 && scroll.getScrollSize() > 0) {
            scroll.scrollTo(list.getScrollArea(), restoredScroll);
            restoredScroll = 0;
        }
        if (owner.clientView()
            .version() == version) return;
        refreshData();
        if (group == null) closeIfOpen();
        else if (!layout.equals(signature())) rebuildRows();
    }

    private void refreshData() {
        version = owner.clientView()
            .version();
        group = owner.clientView()
            .deviceGroup(key);
        if (group == null) return;
        List<Line> next = new ObjectArrayList<>();
        List<NBTTagCompound> all = new ObjectArrayList<>();
        int clusters = 0, sections = 0;
        for (Section section : group.sections()) {
            if (section.devices()
                .isEmpty()) continue;
            sections++;
            clusters += section.clusters();
            all.addAll(section.devices());
        }
        boolean tree = sections > 1;
        List<Line> flat = new ObjectArrayList<>();
        for (Section section : group.sections()) {
            List<NBTTagCompound> members = section.devices();
            if (members.isEmpty()) continue;
            NBTTagCompound row = section.row();
            if (!tree || members.size() == 1 && !row.getBoolean("group")) {
                for (NBTTagCompound device : members) flat.add(new Line(device, false, 0, false, 0, 0));
                continue;
            }
            boolean open = expanded.contains(row.getString("key"));
            next.add(new Line(row, true, 0, open, members.size(), row.getInteger("online")));
            if (open) for (NBTTagCompound device : members) next.add(new Line(device, false, 1, false, 0, 0));
        }
        next.addAll(flat);
        lines = List.copyOf(next);
        devices = List.copyOf(all);
        NBTTagCompound header = group.header();
        title = WirelessCardGui.rowName(header, ItemStack.loadItemStackFromNBT(header.getCompoundTag("icon")));
        int total = header.getInteger("devices"), online = header.getInteger("online"),
            missing = header.getInteger("missing");
        scope = tree ? tr("devices.scope_clusters", total, clusters, online, total, missing)
            : tr("devices.scope", total, online, total, missing);
    }

    private String signature() {
        StringBuilder builder = new StringBuilder();
        for (Line line : lines) builder.append(
            line.section() ? "s" + line.data()
                .getString("key") + line.expanded()
                : "d" + line.depth()
                    + ":"
                    + line.data()
                        .getLong("targetId"))
            .append('|');
        return builder.toString();
    }

    private void rebuildRows() {
        layout = signature();
        list.removeAll();
        for (int i = 0; i < lines.size(); i++) list.child(
            lines.get(i)
                .section() ? new SectionLine(i) : new DeviceLine(i));
        list.scheduleResize();
    }

    private Target renameTarget() {
        return group == null ? null : Target.row(group.header());
    }

    private WirelessCardActionButton renameButton() {
        return textButton(
            () -> StatCollector.translateToLocal("selectWorld.rename"),
            () -> owner.openRename(renameTarget())).size(RENAME_WIDTH, NAME_HEIGHT)
                .tooltipBuilder(tooltip -> tooltip.addLine(tr(renameTooltip(group.header()))));
    }

    private static String renameTooltip(NBTTagCompound row) {
        return row.getBoolean("group") ? "devices.rename_group" : "devices.rename_cluster";
    }

    private static WirelessCardActionButton textButton(Supplier<String> text, Runnable action) {
        return new WirelessCardActionButton(() -> true).text(text, () -> false)
            .onClick(action);
    }

    private WirelessCardActionButton modeButton(int mode) {
        BooleanSupplier selected = () -> !devices.isEmpty() && devices.stream()
            .allMatch(
                device -> owner.clientView()
                    .displayMode(device) == mode);
        return new WirelessCardActionButton(() -> !devices.isEmpty()).text(() -> tr("display.mode." + mode), selected)
            .tooltipBuilder(tooltip -> {
                tooltip.addLine(tr("display.apply_all", devices.size(), tr("display.mode." + mode)));
                tooltip.addLine(tr("display.auto_hint"));
                tooltip.addLine(tr("display.fallback_hint"));
            })
            .tooltipAutoUpdate(true)
            .onClick(() -> {
                if (!owner.clientView()
                    .setDisplayMode(devices, mode))
                    owner.receive(
                        new ChatComponentTranslation(
                            WirelessCardGui.KEY_PREFIX + "display.limit",
                            WirelessCardBinding.MAX_DISPLAY_ENTRIES));
            });
    }

    private static WirelessCardActionButton iconButton(Icon icon, BooleanSupplier available, Runnable action,
        Consumer<RichTooltip> tooltip) {
        return new WirelessCardActionButton(available).icon(() -> icon)
            .onClick(action)
            .size(BUTTON_SIZE, BUTTON_SIZE)
            .tooltipBuilder(tooltip)
            .tooltipAutoUpdate(true);
    }

    private NBTTagCompound lineData(int index) {
        return index < lines.size() ? lines.get(index)
            .data() : new NBTTagCompound();
    }

    private void paintRow(int index, int depth, boolean group, boolean lit) {
        WirelessCardTreeRows.drawBackground(owner, 0, 0, rowWidth, 0, depth, group, lit, index < lines.size() - 1);
    }

    private void addLocationButtons(ParentWidget<?> row, int index) {
        row.child(iconButton(Icon.MOVE_TO, () -> true, () -> owner.sendTarget(TELEPORT, lineData(index)), tooltip -> {
            tooltip.addLine(tr("teleport_tip"));
            tooltip.addLine(EnumChatFormatting.GRAY + lineData(index).getString("positionLabel"));
        }).pos(buttonsX + BUTTON_STEP, 4));
        row.child(iconButton(Icon.VISIBLE, () -> true, () -> owner.highlightTarget(lineData(index)), tooltip -> {
            tooltip.addLine(tr("highlight_tip"));
            tooltip.addLine(EnumChatFormatting.GRAY + WirelessCardClientView.positionWithSide(lineData(index)));
            tooltip.addLine(WirelessCardButton.highlightRange(lineData(index), owner));
        }).pos(buttonsX + BUTTON_STEP * 2, 4));
    }

    private class DeviceLine extends ParentWidget<DeviceLine> {

        private final int index;

        private DeviceLine(int index) {
            this.index = index;
            size(rowWidth, ROW_HEIGHT);
            child(
                new WirelessCardActionButton(this::remoteOpen).plain()
                    .onClick(() -> owner.openDevice(view(), data().getLong("targetId")))
                    .size(rowWidth, ROW_HEIGHT)
                    .tooltipBuilder(this::describe)
                    .tooltipAutoUpdate(true));
            child(
                iconButton(
                    Icon.EDIT,
                    () -> data().getBoolean("renameable"),
                    () -> owner.openRename(Target.device(data())),
                    tooltip -> tooltip.addLine(tr("devices.rename_device"))).pos(buttonsX, 4));
            addLocationButtons(this, index);
            child(
                iconButton(
                    Icon.GEAR,
                    this::remoteOpen,
                    () -> owner.openDevice(view(), data().getLong("targetId")),
                    tooltip -> {
                        tooltip.addLine(tr("remote.open_tip"));
                        WirelessCardButton.remoteState(tooltip, data());
                    }).pos(buttonsX + BUTTON_STEP * 3, 4));
            child(
                textButton(
                    () -> tr("display.short." + mode()),
                    () -> owner.clientView()
                        .cycleDisplayMode(data())).size(BUTTON_SIZE, BUTTON_SIZE)
                            .pos(modeX, 4)
                            .tooltipBuilder(tooltip -> {
                                tooltip.addLine(tr("display.mode", tr("display.mode." + mode())));
                                tooltip.addLine(EnumChatFormatting.GRAY + tr("display.cycle_hint"));
                                tooltip.addLine(EnumChatFormatting.GRAY + tr("display.auto_hint"));
                                tooltip.addLine(EnumChatFormatting.GRAY + tr("display.fallback_hint"));
                            })
                            .tooltipAutoUpdate(true));
        }

        private NBTTagCompound data() {
            return lineData(index);
        }

        private int mode() {
            return owner.clientView()
                .displayMode(data());
        }

        private boolean remoteOpen() {
            return data().getString("remote")
                .equals("open");
        }

        private void describe(RichTooltip tooltip) {
            NBTTagCompound device = data();
            tooltip.addLine(
                WirelessCardGui.rowName(device, ItemStack.loadItemStackFromNBT(device.getCompoundTag("icon"))));
            tooltip.addLine(EnumChatFormatting.GRAY + device.getString("positionLabel"));
            tooltip.addLine(EnumChatFormatting.YELLOW + tr("row_device_hint"));
            WirelessCardButton.remoteState(tooltip, device);
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            if (index >= lines.size()) return;
            Line line = lines.get(index);
            NBTTagCompound device = line.data();
            int mode = mode();
            device.setInteger("displayMode", mode);
            boolean lit = isBelowMouse(), last = index + 1 >= lines.size() || lines.get(index + 1)
                .depth() == 0;
            Palette ink = lit ? owner.highlightPalette : owner.palette;
            paintRow(index, line.depth(), false, lit);
            WirelessCardTreeRows.drawTree(owner, 0, 0, line.depth(), false, 0, last, lit);
            int iconX = WirelessCardTreeRows.iconX(line.depth(), false);
            ItemStack shown = WirelessCardGui.rowIcon(device);
            if (shown != null) owner.drawDeviceIcon(shown, iconX, 4);
            String modeName = tr("display.mode." + mode), state = device.getString("deviceState");
            int textX = iconX + WirelessCardTreeRows.TEXT_GAP, textWidth = buttonsX - MODE_GAP - textX;
            owner.drawText(() -> {
                owner.drawRowTitle(
                    device,
                    ItemStack.loadItemStackFromNBT(device.getCompoundTag("icon")),
                    textX,
                    2,
                    textWidth,
                    ink);
                owner.fontRendererObj.drawString(
                    cropText(owner.fontRendererObj, tr("device_state." + state), textWidth),
                    textX,
                    13,
                    ink.deviceState(state));
                owner.fontRendererObj.drawString(
                    modeName,
                    modeX - MODE_GAP - owner.fontRendererObj.getStringWidth(modeName),
                    8,
                    ink.link);
            });
        }
    }

    private class SectionLine extends ParentWidget<SectionLine> {

        private final int index;

        private SectionLine(int index) {
            this.index = index;
            size(rowWidth, ROW_HEIGHT);
            boolean machine = lineData(index).getBoolean("group");
            child(
                new WirelessCardActionButton(() -> true).plain()
                    .onClick(() -> {
                        String section = lineData(index).getString("key");
                        if (!expanded.remove(section)) expanded.add(section);
                        // The clicked row is replaced on the next update, not while it handles its own click.
                        version = -1;
                    })
                    .size(rowWidth, ROW_HEIGHT)
                    .tooltipBuilder(tooltip -> {
                        NBTTagCompound row = lineData(index);
                        tooltip.addLine(
                            WirelessCardGui.rowName(row, ItemStack.loadItemStackFromNBT(row.getCompoundTag("icon"))));
                        String position = row.getString("positionLabel");
                        if (!position.isEmpty()) tooltip.addLine(EnumChatFormatting.GRAY + position);
                    })
                    .tooltipAutoUpdate(true));
            if (Target.row(lineData(index)) != null) child(
                iconButton(
                    Icon.EDIT,
                    () -> Target.row(lineData(index)) != null,
                    () -> owner.openRename(Target.row(lineData(index))),
                    tooltip -> tooltip.addLine(tr(renameTooltip(lineData(index))))).pos(buttonsX, 4));
            // Machine groups have no single position, as in the main list.
            if (!machine) addLocationButtons(this, index);
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            if (index >= lines.size()) return;
            Line line = lines.get(index);
            NBTTagCompound row = line.data();
            boolean lit = line.expanded() || isBelowMouse();
            Palette ink = lit ? owner.highlightPalette : owner.palette;
            paintRow(index, 0, true, lit);
            owner.drawArrow(line.expanded(), WirelessCardTreeRows.arrowX(0), WirelessCardTreeRows.ARROW_Y);
            int iconX = WirelessCardTreeRows.iconX(0, true);
            ItemStack shown = WirelessCardGui.rowIcon(row);
            if (shown != null) owner.drawDeviceIcon(shown, iconX, 4);
            int textX = iconX + WirelessCardTreeRows.TEXT_GAP, textWidth = buttonsX - MODE_GAP - textX;
            String count = "×" + line.count();
            owner.drawText(() -> {
                owner.drawRowTitle(
                    row,
                    ItemStack.loadItemStackFromNBT(row.getCompoundTag("icon")),
                    textX,
                    2,
                    textWidth,
                    ink);
                owner.fontRendererObj.drawString(
                    cropText(
                        owner.fontRendererObj,
                        tr(
                            line.expanded() ? "devices.cluster_expanded" : "devices.cluster_collapsed",
                            line.count(),
                            line.online(),
                            line.count()),
                        textWidth),
                    textX,
                    13,
                    ink.suffix);
                owner.fontRendererObj.drawString(
                    count,
                    rowWidth - COUNT_MARGIN - owner.fontRendererObj.getStringWidth(count),
                    8,
                    ink.name);
            });
        }
    }

    @Override
    public boolean onKeyPressed(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            closeIfOpen();
            return true;
        }
        return super.onKeyPressed(character, key);
    }
}
