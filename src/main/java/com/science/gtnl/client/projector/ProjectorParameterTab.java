package com.science.gtnl.client.projector;

import static com.science.gtnl.client.projector.ProjectorWidgets.CONTROL;
import static com.science.gtnl.client.projector.ProjectorWidgets.MENU;
import static com.science.gtnl.client.projector.ProjectorWidgets.ROW;
import static com.science.gtnl.client.projector.ProjectorWidgets.SMALL;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.ObjectValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.gtnllib.gui.widget.CycleDropdown;
import com.gtnllib.gui.widget.GTNLListWidget;
import com.gtnllib.gui.widget.HandleScrollData;
import com.gtnllib.gui.widget.ListRow;
import com.gtnllib.gui.widget.NumberEntry;
import com.science.gtnl.common.projector.ProjectorPlan;
import com.science.gtnl.common.projector.ProjectorStructure;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ProjectorParameterTab {

    public static final int CHANNEL_MENUS = 16;
    private static final String LANG = ProjectorGui.LANG;

    private final ProjectorGui gui;
    private final ProjectorPlan working;
    private final ListWidget<IWidget, ?> parameterList;
    private Object parameterLayout;

    private record ParameterLayout(int machine, boolean locked, List<String> channels) {}

    public ProjectorParameterTab(ProjectorGui gui) {
        this.gui = gui;
        this.working = gui.working;
        parameterList = new GTNLListWidget<>().scrollDirection(new HandleScrollData(gui.variant).rows(ROW))
            .crossAxisAlignment(Alignment.CrossAxis.START);
    }

    public ParentWidget<?> build() {
        int width = ProjectorGui.RIGHT_WIDTH, height = ProjectorGui.PAGE_HEIGHT;
        return new ParentWidget<>().size(width, height)
            .background(gui.well())
            .disableHoverThemeBackground(true)
            .child(
                parameterList.left(0)
                    .top(1)
                    .size(width, height - 2)
                    .onUpdateListener(list -> refresh(), true));
    }

    private void refresh() {
        ProjectorStructure structure = gui.structure();
        ParameterLayout layout = new ParameterLayout(
            working.machine,
            working.state == ProjectorPlan.State.LOCKED,
            structure == null ? List.of() : structure.channels);
        if (layout.equals(parameterLayout)) return;
        parameterLayout = layout;
        parameterList.removeAll();
        Consumer<IWidget> add = parameterList::child;
        add.accept(
            gui.controlRow(
                LANG + "master",
                entry(1, ProjectorPlan.MAX_MASTER, 1, () -> working.master, value -> working.master = (int) value)));
        add.accept(gui.controlRow(LANG + "hatch_tier", hatchTierDropdown()));
        if (!layout.channels()
            .isEmpty()) {
            add.accept(gui.header(LANG + "channels"));
            for (int i = 0; i < layout.channels()
                .size(); i++)
                add.accept(
                    channelRow(
                        layout.channels()
                            .get(i),
                        i));
        }
        if (layout.machine() > 0) addPoseRows(add, layout.locked());
        parameterList.scheduleResize();
    }

    private CycleDropdown<Integer> hatchTierDropdown() {
        List<Integer> tiers = new ArrayList<>();
        for (int tier = 0; tier <= ProjectorPlan.MAX_HATCH_TIER; tier++) tiers.add(tier);
        return new CycleDropdown<>(MENU + "hatch_tier", Integer.class).wheel(true)
            .choices(tiers)
            .optionToWidget(
                (tier, forShown) -> CycleDropdown
                    .iconOption(null, tier == null ? "" : ProjectorHatchPage.coloredTier(tier), Alignment.Center))
            .value(new ObjectValue.Dynamic<>(Integer.class, () -> working.hatchTier, tier -> working.hatchTier = tier))
            .size(CONTROL, SMALL);
    }

    private void addPoseRows(Consumer<IWidget> add, boolean locked) {
        add.accept(gui.header(LANG + "pose"));
        if (locked) add.accept(
            gui.controlRow(
                LANG + "height",
                entry(
                    0,
                    255,
                    MathHelper.floor_double(Minecraft.getMinecraft().thePlayer.posY),
                    () -> working.y,
                    value -> working.y = (int) value)));
        else add.accept(
            gui.controlRow(
                LANG + "raise",
                entry(
                    -ProjectorPlan.MAX_OFFSET,
                    ProjectorPlan.MAX_OFFSET,
                    0,
                    () -> working.verticalOffset,
                    value -> working.verticalOffset = working.groundSnap ? Math.max(0, (int) value) : (int) value)));
    }

    private ListRow channelRow(String channel, int index) {
        TreeMap<Integer, ItemStack> tiers = ProjectorChannels.tiers(channel, gui.structure());
        Widget<?> control;
        if (tiers.isEmpty()) {
            control = entry(
                0,
                ProjectorChannels.max(channel),
                0,
                () -> working.channels.getOrDefault(channel, 0),
                next -> working.setChannel(channel, (int) next));
        } else {
            List<Integer> choices = new ArrayList<>();
            choices.add(0);
            choices.addAll(tiers.keySet());
            String fallback = StatCollector.translateToLocal(LANG + "channel_default");
            control = new CycleDropdown<>(MENU + "channel_" + Math.min(index, CHANNEL_MENUS - 1), Integer.class)
                .wheel(true)
                .choices(choices)
                .optionToWidget(
                    (value, forShown) -> value == null || value == 0 ? CycleDropdown.iconOption(null, fallback)
                        : CycleDropdown.iconOption(tiers.get(value), ProjectorChannels.name(tiers.get(value))))
                .value(
                    new ObjectValue.Dynamic<>(
                        Integer.class,
                        () -> working.channels.getOrDefault(channel, 0),
                        value -> working.setChannel(channel, value == null ? 0 : value)))
                .size(CONTROL, SMALL);
        }
        String label = ProjectorChannels.label(channel);
        return new ListRow(() -> label).width(gui.wellWidth)
            .control(control, CONTROL, SMALL);
    }

    private static NumberEntry entry(long min, long max, long reset, LongSupplier current, LongConsumer set) {
        return new NumberEntry(null).wheel(true)
            .bounds(min, max)
            .textAlignment(Alignment.Center)
            .resetValue(reset)
            .follow(current)
            .onChange(set)
            .size(CONTROL, SMALL);
    }
}
