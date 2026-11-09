package com.science.gtnl.client.wireless;

import static com.science.gtnl.client.wireless.WirelessCardGui.tr;
import static com.science.gtnl.common.wireless.WirelessCardActions.HIGHLIGHT;
import static com.science.gtnl.common.wireless.WirelessCardActions.OPEN_DETAILS;
import static com.science.gtnl.common.wireless.WirelessCardActions.OPEN_DEVICE;
import static com.science.gtnl.common.wireless.WirelessCardActions.OPEN_RENAME;
import static com.science.gtnl.common.wireless.WirelessCardActions.REFRESH;
import static com.science.gtnl.common.wireless.WirelessCardActions.TELEPORT;
import static com.science.gtnl.common.wireless.WirelessCardActions.TOGGLE_AUTO;
import static com.science.gtnl.common.wireless.WirelessCardActions.TOGGLE_GROUPING;
import static com.science.gtnl.common.wireless.WirelessCardActions.TOGGLE_PAUSE;
import static com.science.gtnl.common.wireless.WirelessCardActions.isRowAction;
import static com.science.gtnl.common.wireless.WirelessCardActions.rowIndex;
import static com.science.gtnl.common.wireless.WirelessCardActions.type;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.layout.IViewportStack;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.gtnllib.gui.theme.AE2Theme;
import com.gtnllib.gui.theme.AE2Theme.Icon;
import com.science.gtnl.common.wireless.remote.RemoteDeviceState;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class WirelessCardButton {

    private static final int MAX_TOOLTIP_ENTRANCES = 8;
    public final int id;
    public final Widget<?> widget;
    private final WirelessCardGui panel;
    private boolean available, on;

    WirelessCardButton(int id, WirelessCardGui panel) {
        this.id = id;
        this.panel = panel;
        int actionType = type(id);
        if (id == TOGGLE_AUTO || actionType == TOGGLE_PAUSE) {
            ToggleButton toggle = new ToggleButton() {

                @Override
                public boolean canBeSeen(IViewportStack stack) {
                    return isEnabled() && super.canBeSeen(stack);
                }

                @Override
                public @NotNull Result onMousePressed(int button) {
                    if (button != 0) return Result.ACCEPT;
                    return available ? super.onMousePressed(button) : Result.STOP;
                }
            };
            IDrawable look = id == TOGGLE_AUTO
                ? AE2Theme.toggleSwitch(toggle, () -> on, () -> tr("auto_connect"), () -> available)
                : AE2Theme.iconButton(toggle, () -> on ? Icon.PAUSE : Icon.PLAY, () -> available);
            widget = toggle.value(new BoolValue.Dynamic(() -> on, ignored -> panel.action(id)))
                .background(false, IDrawable.EMPTY)
                .background(true, IDrawable.EMPTY)
                .hoverBackground(false, IDrawable.EMPTY)
                .hoverBackground(true, IDrawable.EMPTY)
                .overlay(false, look)
                .overlay(true, look);
        } else {
            var button = new WirelessCardActionButton(() -> available).onClick(() -> panel.action(id));
            widget = button;
            switch (actionType) {
                case OPEN_DETAILS -> button.plain();
                case TELEPORT -> button.icon(() -> Icon.MOVE_TO);
                case HIGHLIGHT -> button.icon(() -> Icon.VISIBLE);
                case OPEN_DEVICE -> button.icon(() -> Icon.GEAR);
                case OPEN_RENAME -> button.icon(() -> Icon.EDIT);
                case REFRESH -> button.plain()
                    .overlay(AE2Theme.tabButton(button, () -> Icon.REFRESH, () -> available));
                case TOGGLE_GROUPING -> button.plain()
                    .overlay(AE2Theme.toggleSwitch(button, () -> on, () -> tr("merge_types"), () -> available));
                default -> {}
            }
        }
        if (isRowAction(id) || id == TOGGLE_GROUPING || id == REFRESH) widget.tooltipBuilder(this::tooltip)
            .tooltipAutoUpdate(true);
    }

    private static boolean sameDimension(NBTTagCompound row) {
        var player = Minecraft.getMinecraft().thePlayer;
        return player != null && row.getInteger("dimension") == player.dimension;
    }

    private void tooltip(RichTooltip tooltip) {
        NBTTagCompound data = panel.view();
        NBTTagCompound row = isRowAction(id) ? data.getTagList("rows", 10)
            .getCompoundTagAt(rowIndex(id)) : new NBTTagCompound();
        NBTTagCompound location = WirelessCardClientView.locationTarget(row);
        String gray = EnumChatFormatting.GRAY.toString();
        switch (type(id)) {
            case OPEN_DETAILS -> {
                String name = WirelessCardGui.rowName(row, ItemStack.loadItemStackFromNBT(row.getCompoundTag("icon")));
                String targetName = row.getString("targetName");
                tooltip.addLine(targetName.isEmpty() || targetName.equals(name) ? name : name + " - " + targetName);
                tooltip.addLine(gray + location.getString("positionLabel"));
                entrances(tooltip, row, data.getBoolean("channels"));
                boolean single = row.getBoolean("singleDevice");
                tooltip.addLine(
                    EnumChatFormatting.YELLOW
                        + (row.getBoolean("group") ? tr("row_expand_hint", WirelessCardGui.groupContents(row))
                            : tr(single ? "row_open_device_hint" : "row_open_hint")));
                if (!row.getBoolean("group") && single) remoteState(tooltip, row);
            }
            case TELEPORT -> {
                tooltip.addLine(tr("teleport_tip"));
                tooltip.addLine(gray + location.getString("positionLabel"));
            }
            case HIGHLIGHT -> {
                tooltip.addLine(tr("highlight_tip"));
                tooltip.addLine(gray + WirelessCardClientView.positionWithSide(location));
                tooltip.addLine(highlightRange(location, panel));
            }
            case TOGGLE_PAUSE -> tooltip.addLine(tr(row.getBoolean("paused") ? "resume_tip" : "pause_tip"));
            case OPEN_DEVICE -> {
                tooltip.addLine(tr("remote.open_tip"));
                remoteState(tooltip, row);
            }
            case OPEN_RENAME -> tooltip
                .addLine(tr(row.getBoolean("singleDevice") ? "devices.rename_device" : "devices.rename_cluster"));
            case TOGGLE_AUTO -> tooltip.addLine(tr(data.getBoolean("auto") ? "auto_on" : "auto_off"));
            case TOGGLE_GROUPING -> {
                tooltip.addLine(
                    tr("merge_types") + ": "
                        + StatCollector
                            .translateToLocal(data.getBoolean("collapseTypes") ? "options.on" : "options.off"));
                tooltip.addLine(gray + tr("merge_types_tip"));
            }
            case REFRESH -> {
                tooltip.addLine(tr("refresh"));
                tooltip.addLine(gray + tr("refresh_tip"));
            }
            default -> {}
        }
    }

    private static void entrances(RichTooltip tooltip, NBTTagCompound row, boolean channels) {
        if (row.getBoolean("group")) {
            int total = row.getInteger("entrances");
            if (total > 1) tooltip.addLine(tr("entrances_compact", total));
            return;
        }
        NBTTagList entrances = row.getTagList("entranceList", 10);
        if (entrances.tagCount() < 2) return;
        tooltip.addLine(tr("row_entrances", entrances.tagCount()));
        int shown = Math.min(entrances.tagCount(), MAX_TOOLTIP_ENTRANCES);
        for (int i = 0; i < shown; i++) {
            NBTTagCompound entrance = entrances.getCompoundTagAt(i);
            int used = entrance.getInteger("usedChannels");
            String usage = !channels ? "" : used < 0 ? tr("entrance_waiting") : tr("entrance_channels", used);
            tooltip.addLine(
                EnumChatFormatting.GRAY + "  "
                    + entrance.getString("positionLabel")
                    + (usage.isEmpty() ? "" : "  " + usage));
        }
        if (entrances.tagCount() > shown)
            tooltip.addLine(EnumChatFormatting.GRAY + "  " + tr("entrances_more", entrances.tagCount() - shown));
    }

    static String highlightRange(NBTTagCompound target, WirelessCardGui panel) {
        return sameDimension(target) ? EnumChatFormatting.GRAY + tr("highlight_range")
            : EnumChatFormatting.RED
                + tr("highlight_other_dimension", panel.dimensionName(target.getInteger("dimension")));
    }

    static void remoteState(RichTooltip tooltip, NBTTagCompound row) {
        String remote = row.getString("remote");
        if (!remote.equals("open")) tooltip.addLine(
            EnumChatFormatting.RED + tr(
                "remote." + RemoteDeviceState.byKey(remote)
                    .key()));
    }

    void update(NBTTagCompound data, boolean ready, int visibleRows) {
        NBTTagList rows = data.getTagList("rows", 10);
        int rowCount = Math.min(visibleRows, rows.tagCount());
        int actionType = type(id);
        boolean visible = true;
        available = ready;
        if (isRowAction(id)) {
            int index = rowIndex(id);
            if (index >= rowCount) {
                widget.setEnabled(false);
                return;
            }
            NBTTagCompound row = rows.getCompoundTagAt(index);
            if (actionType == TOGGLE_PAUSE) on = !row.getBoolean("paused");
            if (actionType == TOGGLE_PAUSE || actionType == HIGHLIGHT
                || actionType == TELEPORT
                || actionType == OPEN_DEVICE) visible = !row.getBoolean("group");
            if (actionType == OPEN_DEVICE) available &= row.getString("remote")
                .equals("open");
            if (actionType == OPEN_RENAME) visible = WirelessCardRenameDialog.Target.listed(row) != null;
        } else if (id == TOGGLE_AUTO) on = data.getBoolean("auto");
        else if (id == TOGGLE_GROUPING) on = data.getBoolean("collapseTypes");
        widget.setEnabled(visible);
    }
}
