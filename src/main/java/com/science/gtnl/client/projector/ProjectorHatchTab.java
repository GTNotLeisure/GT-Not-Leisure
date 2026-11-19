package com.science.gtnl.client.projector;

import static com.science.gtnl.client.projector.ProjectorWidgets.CONTROL;
import static com.science.gtnl.client.projector.ProjectorWidgets.MENU;
import static com.science.gtnl.client.projector.ProjectorWidgets.MENU_WIDTH;
import static com.science.gtnl.client.projector.ProjectorWidgets.ROW;
import static com.science.gtnl.client.projector.ProjectorWidgets.SMALL;
import static com.science.gtnl.client.projector.ProjectorWidgets.column;
import static com.science.gtnl.client.projector.ProjectorWidgets.overlay;
import static com.science.gtnl.client.projector.ProjectorWidgets.trim;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.ObjectValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnllib.gui.theme.AE2Theme;
import com.gtnllib.gui.widget.ActionButton;
import com.gtnllib.gui.widget.CycleDropdown;
import com.gtnllib.gui.widget.IconRowList;
import com.gtnllib.gui.widget.IconRowList.Row;
import com.gtnllib.gui.widget.ListRow;
import com.gtnllib.gui.widget.ValueStepper;
import com.science.gtnl.common.projector.ProjectorMachines;
import com.science.gtnl.common.projector.ProjectorPlan;
import com.science.gtnl.config.ClientConfig;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ProjectorHatchTab {

    private static final String LANG = ProjectorGui.LANG;
    private static final int HEADER_HEIGHT = 26, CARD_HEIGHT = HEADER_HEIGHT + 4 * ROW + 2;

    private final ProjectorGui gui;
    private final ProjectorHatchPage hatches;
    private final IconRowList hatchList = new IconRowList(this::hatchRows);

    public ProjectorHatchTab(ProjectorGui gui) {
        this.gui = gui;
        this.hatches = gui.hatches;
    }

    public Flow build() {
        int wellWidth = gui.wellWidth;
        BooleanSupplier editable = hatches::editable, block = hatches::blockCell,
            casing = () -> editable.getAsBoolean() && ProjectorHatchPage.CASING.equals(hatches.kind()),
            model = () -> editable.getAsBoolean() && !casing.getAsBoolean(),
            casings = () -> casing.getAsBoolean() && hatches.casings()
                .size() > 1;
        Flow card = column().size(wellWidth, CARD_HEIGHT)
            .padding(0, 0, 1, 1)
            .background(gui.well())
            .child(new SpotHeader())
            .child(
                new ListRow(
                    () -> StatCollector.translateToLocal(LANG + (block.getAsBoolean() ? "block" : "hatch_kind")))
                        .width(wellWidth)
                        .control(
                            overlay(
                                kindDropdown().setEnabledIf(w -> editable.getAsBoolean()),
                                blockDropdown().setEnabledIf(w -> block.getAsBoolean()))
                                    .setEnabledIf(w -> editable.getAsBoolean() || block.getAsBoolean()),
                            CONTROL,
                            SMALL))
            .child(
                gui.controlRow(
                    LANG + "hatch_variant",
                    overlay(
                        variantDropdown().setEnabledIf(w -> model.getAsBoolean()),
                        casingDropdown().setEnabledIf(w -> casings.getAsBoolean()))
                            .setEnabledIf(w -> model.getAsBoolean() || casings.getAsBoolean())))
            .child(
                gui.controlRow(
                    LANG + "hatch_level",
                    new ValueStepper(hatches::tierLabel, hatches::stepTier).size(CONTROL, SMALL)
                        .setEnabledIf(w -> model.getAsBoolean() && hatches.hasTiers())))
            .child(
                new ParentWidget<>().size(wellWidth, ROW)
                    .child(
                        new ListRow(
                            () -> StatCollector
                                .translateToLocal(LANG + (casing.getAsBoolean() ? "same_casing" : "same_kind")))
                                    .width(wellWidth)
                                    .toggle(
                                        () -> casing.getAsBoolean() ? hatches.sameCasing() : hatches.sameKind(),
                                        () -> {
                                            if (casing.getAsBoolean()) hatches.toggleSameCasing();
                                            else hatches.toggleSameKind();
                                        })
                                    .setEnabledIf(w -> !block.getAsBoolean() && !hatches.channelCasing()))
                    .child(
                        new ListRow(
                            () -> block.getAsBoolean()
                                ? StatCollector.translateToLocalFormatted(LANG + "group_cells", hatches.groupSize())
                                : StatCollector.translateToLocal(ProjectorSpotScreen.LANG + "channel_casing"))
                                    .width(wellWidth)
                                    .setEnabledIf(w -> block.getAsBoolean() || hatches.channelCasing())));
        int width = ProjectorGui.RIGHT_WIDTH, height = ProjectorGui.PAGE_HEIGHT;
        return column().size(width, height)
            .childPadding(4)
            .child(card)
            .child(hatchList.size(width, height - CARD_HEIGHT - 4));
    }

    private CycleDropdown<String> casingDropdown() {
        CycleDropdown<String> dropdown = new CycleDropdown<>(MENU + "casing", String.class);
        dropdown.optionToWidget((key, forShown) -> blockOption(key))
            .value(new ObjectValue.Dynamic<>(String.class, hatches::casingKey, hatches::chooseCasing))
            .menuWidth(MENU_WIDTH)
            .size(CONTROL, SMALL)
            .onUpdateListener(widget -> widget.choices(hatches.casingKeys()), true);
        return dropdown;
    }

    private CycleDropdown<String> blockDropdown() {
        CycleDropdown<String> dropdown = new CycleDropdown<>(MENU + "block", String.class);
        dropdown.optionToWidget((key, forShown) -> blockOption(key))
            .value(new ObjectValue.Dynamic<>(String.class, hatches::blockKey, hatches::chooseBlock))
            .menuWidth(MENU_WIDTH)
            .size(CONTROL, SMALL)
            .onUpdateListener(widget -> widget.choices(hatches.blockKeys()), true);
        return dropdown;
    }

    private IWidget blockOption(String key) {
        if (key == null) return CycleDropdown.iconOption(null, "");
        if (ProjectorPlan.AIR.equals(key))
            return CycleDropdown.iconOption(null, StatCollector.translateToLocal(ProjectorSpotScreen.LANG + "empty"));
        ItemStack stack = hatches.stackOf(key);
        return CycleDropdown.iconOption(stack, stack == null ? key : ProjectorChannels.name(stack));
    }

    private CycleDropdown<Object> kindDropdown() {
        CycleDropdown<Object> dropdown = new CycleDropdown<>(MENU + "kind", Object.class);
        dropdown
            .optionToWidget(
                (kind, forShown) -> CycleDropdown.iconOption(hatches.kindIcon(kind), hatches.kindName(kind)))
            .value(new ObjectValue.Dynamic<>(Object.class, hatches::kind, hatches::chooseKind))
            .menuWidth(MENU_WIDTH)
            .size(CONTROL, SMALL)
            .onUpdateListener(widget -> widget.choices(hatches.kinds()), true);
        return dropdown;
    }

    private CycleDropdown<Integer> variantDropdown() {
        CycleDropdown<Integer> dropdown = new CycleDropdown<>(MENU + "variant", Integer.class);
        dropdown
            .optionToWidget(
                (first, forShown) -> CycleDropdown.iconOption(
                    first == null ? null : ProjectorMachines.stackOf(first),
                    first == null ? "" : ProjectorHatchPage.variantName(first)))
            .value(new ObjectValue.Dynamic<>(Integer.class, hatches::variant, hatches::chooseVariant))
            .menuWidth(MENU_WIDTH)
            .size(CONTROL, SMALL)
            .onUpdateListener(widget -> widget.choices(hatches.variants()), true);
        return dropdown;
    }

    private List<Row> hatchRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.header(StatCollector.translateToLocal(LANG + "outline")));
        rows.add(
            Row.toggle(
                StatCollector.translateToLocal(LANG + "hatch_outlines"),
                ClientConfig.projectorHatchOutlines,
                () -> {
                    ClientConfig.projectorHatchOutlines = !ClientConfig.projectorHatchOutlines;
                    gui.configChanged = true;
                }));
        rows.add(
            Row.toggle(
                StatCollector.translateToLocal(LANG + "outline_merge"),
                ClientConfig.projectorMergeOutlines,
                () -> {
                    ClientConfig.projectorMergeOutlines = !ClientConfig.projectorMergeOutlines;
                    gui.configChanged = true;
                }));
        rows.addAll(hatches.spotRows());
        rows.addAll(hatches.choiceRows());
        return rows;
    }

    private class SpotHeader extends ParentWidget<SpotHeader> {

        private SpotHeader() {
            size(gui.wellWidth, HEADER_HEIGHT);
            child(
                ActionButton.text(() -> "<", () -> hatches.step(-1))
                    .size(ValueStepper.ARROW_WIDTH, ProjectorWidgets.BUTTON)
                    .left(2)
                    .top(3));
            child(
                ActionButton.text(() -> ">", () -> hatches.step(1))
                    .size(ValueStepper.ARROW_WIDTH, ProjectorWidgets.BUTTON)
                    .right(2)
                    .top(3));
        }

        @Override
        public boolean canHover() {
            return true;
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            int width = getArea().width;
            ItemStack icon = hatches.icon();
            if (icon != null) new ItemDrawable(icon).draw(context, 17, 5, 16, 16, widgetTheme.getTheme());
            int text = gui.variant == null ? IconRowList.TEXT : gui.variant.colors.text();
            int left = icon == null ? 17 : 36, right = width - 17;
            String count = hatches.countLine();
            int countWidth = count.isEmpty() ? 0 : Minecraft.getMinecraft().fontRenderer.getStringWidth(count) + 3;
            AE2Theme.text(trim(hatches.placeLine(), right - left - countWidth), left, 4, gui.muted, false);
            if (!count.isEmpty()) AE2Theme.text(count, right - countWidth + 3, 4, gui.muted, false);
            AE2Theme.text(trim(hatches.nameLine(), right - left), left, 15, text, false);
        }
    }
}
