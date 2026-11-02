package com.science.gtnl.client.wireless;

import static com.science.gtnl.client.wireless.WirelessCardGui.tr;
import static com.science.gtnl.common.wireless.WirelessCardActions.BACK;
import static com.science.gtnl.common.wireless.WirelessCardActions.COPY_POSITION;
import static com.science.gtnl.common.wireless.WirelessCardActions.HIGHLIGHT;
import static com.science.gtnl.common.wireless.WirelessCardActions.NEXT_PAGE;
import static com.science.gtnl.common.wireless.WirelessCardActions.OPEN_DETAILS;
import static com.science.gtnl.common.wireless.WirelessCardActions.OPEN_ENTRANCES;
import static com.science.gtnl.common.wireless.WirelessCardActions.PREVIOUS_PAGE;
import static com.science.gtnl.common.wireless.WirelessCardActions.REFRESH;
import static com.science.gtnl.common.wireless.WirelessCardActions.RENAME;
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
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.gtnllib.gui.theme.AE2Theme;
import com.gtnllib.gui.theme.AE2Theme.Icon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class WirelessCardButton {

    public final int id;
    public final Widget<?> widget;
    private final WirelessCardGui panel;
    private boolean available, on;
    private String label = "";

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
                ? AE2Theme.toggleSwitch(toggle, () -> on, () -> tr(on ? "auto_on" : "auto_off"), () -> available)
                : AE2Theme.iconButton(toggle, () -> on ? Icon.PAUSE : Icon.PLAY, () -> available);
            widget = toggle.value(new BoolValue.Dynamic(() -> on, ignored -> panel.action(id)))
                .background(false, IDrawable.EMPTY)
                .background(true, IDrawable.EMPTY)
                .hoverBackground(false, IDrawable.EMPTY)
                .hoverBackground(true, IDrawable.EMPTY)
                .overlay(false, look)
                .overlay(true, look);
        } else {
            widget = new ActionButton().onMousePressed(button -> {
                if (button != 0) return false;
                panel.action(id);
                return true;
            });
            switch (actionType) {
                case OPEN_DETAILS -> widget.background(IDrawable.EMPTY)
                    .hoverBackground(IDrawable.EMPTY);
                case TELEPORT -> styleIcon(Icon.MOVE_TO);
                case HIGHLIGHT -> styleIcon(Icon.VISIBLE);
                case COPY_POSITION -> styleIcon(Icon.COPY);
                case OPEN_ENTRANCES -> styleIcon(Icon.MORE);
                default -> styleButton();
            }
        }
        if (isRowAction(id) || id == TOGGLE_GROUPING || id == REFRESH || id == BACK)
            widget.tooltipBuilder(this::tooltip)
                .tooltipAutoUpdate(true);
    }

    private class ActionButton extends ButtonWidget<ActionButton> {

        @Override
        public boolean canBeSeen(IViewportStack stack) {
            return isEnabled() && super.canBeSeen(stack);
        }

        @Override
        public @NotNull Result onMousePressed(int button) {
            // Icon buttons sit on top of the row's open area, so a refused click must not fall through to it.
            return button == 0 && !available ? Result.STOP : super.onMousePressed(button);
        }
    }

    private void styleButton() {
        widget.background(IDrawable.EMPTY)
            .hoverBackground(IDrawable.EMPTY);
        if (id == REFRESH) widget.overlay(AE2Theme.tabButton(widget, () -> Icon.REFRESH, () -> available));
        else if (id == TOGGLE_GROUPING)
            widget.overlay(AE2Theme.toggleSwitch(widget, () -> on, () -> label, () -> available));
        else {
            IDrawable background = AE2Theme.button(widget, () -> available, () -> false);
            widget.background(background)
                .hoverBackground(background)
                .overlay(AE2Theme.label(widget, () -> label, () -> available, () -> false));
        }
    }

    private void styleIcon(Icon icon) {
        widget.background(IDrawable.EMPTY)
            .hoverBackground(IDrawable.EMPTY)
            .overlay(AE2Theme.iconButton(widget, () -> icon, () -> available));
    }

    private static boolean sameDimension(NBTTagCompound row) {
        var player = Minecraft.getMinecraft().thePlayer;
        return player != null && row.getInteger("dimension") == player.dimension;
    }

    private void tooltip(RichTooltip tooltip) {
        NBTTagCompound data = panel.view();
        NBTTagCompound row = isRowAction(id) ? data.getTagList("rows", 10)
            .getCompoundTagAt(rowIndex(id)) : new NBTTagCompound();
        String gray = EnumChatFormatting.GRAY.toString();
        switch (type(id)) {
            case OPEN_DETAILS -> {
                String name = WirelessCardGui.rowName(row, ItemStack.loadItemStackFromNBT(row.getCompoundTag("icon")));
                String targetName = row.getString("targetName");
                tooltip.addLine(targetName.isEmpty() || targetName.equals(name) ? name : name + " - " + targetName);
                tooltip.addLine(gray + row.getString("positionLabel"));
                tooltip.addLine(
                    EnumChatFormatting.YELLOW + (row.getBoolean("group")
                        ? tr(
                            "row_expand_hint",
                            row.getBoolean("category") ? row.getInteger("items") : row.getInteger("clusters"))
                        : tr("row_open_hint")));
            }
            case TELEPORT -> {
                tooltip.addLine(tr("teleport_tip"));
                tooltip.addLine(gray + row.getString("positionLabel"));
            }
            case HIGHLIGHT -> {
                tooltip.addLine(tr("highlight_tip"));
                tooltip.addLine(
                    sameDimension(row) ? gray + tr("highlight_range")
                        : EnumChatFormatting.RED
                            + tr("highlight_other_dimension", panel.dimensionName(row.getInteger("dimension"))));
            }
            case COPY_POSITION -> {
                tooltip.addLine(tr("copy_tip"));
                tooltip.addLine(
                    gray + row.getString("position")
                        + (row.getBoolean("group") || row.getString("side")
                            .isEmpty() ? "" : " / " + row.getString("side")));
            }
            case TOGGLE_PAUSE -> tooltip.addLine(tr(row.getBoolean("paused") ? "resume_tip" : "pause_tip"));
            case OPEN_ENTRANCES -> {
                tooltip.addLine(tr("entrances_tip"));
                tooltip.addLine(gray + row.getString("positionLabel"));
            }
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
            case BACK -> tooltip.addLine(tr("back_hint"));
            default -> {}
        }
    }

    void update(NBTTagCompound data, boolean ready, int visibleRows) {
        NBTTagList rows = data.getTagList("rows", 10);
        int rowCount = Math.min(visibleRows, rows.tagCount());
        int actionType = type(id);
        boolean visible = true, detail = data.getBoolean("detail");
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
                || actionType == COPY_POSITION) visible = !row.getBoolean("group");
            if (actionType == TOGGLE_PAUSE) visible &= !row.getBoolean("device");
            if (actionType == OPEN_ENTRANCES) visible = !detail && !row.getBoolean("group");
            if (actionType == OPEN_DETAILS) visible = !detail;
            if (actionType == HIGHLIGHT) available &= sameDimension(row);
        } else switch (id) {
            case TOGGLE_AUTO -> on = data.getBoolean("auto");
            case BACK -> visible = detail;
            case RENAME -> {
                visible = detail;
                available &= data.getBoolean("renameable");
            }
            case TOGGLE_GROUPING -> {
                visible = !detail;
                on = data.getBoolean("collapseTypes");
            }
            case PREVIOUS_PAGE -> available &= data.getInteger("page") > 0;
            case NEXT_PAGE -> available &= data.getInteger("page") + 1 < data.getInteger("pages");
            default -> {}
        }
        widget.setEnabled(visible);
        label = switch (id) {
            case PREVIOUS_PAGE -> "<";
            case NEXT_PAGE -> ">";
            case BACK -> tr("back_compact");
            case RENAME -> StatCollector.translateToLocal("selectWorld.rename");
            case TOGGLE_GROUPING -> tr("merge_types");
            default -> "";
        };
    }
}
