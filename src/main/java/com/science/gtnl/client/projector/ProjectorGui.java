package com.science.gtnl.client.projector;

import static com.science.gtnl.client.projector.ProjectorWidgets.BUTTON;
import static com.science.gtnl.client.projector.ProjectorWidgets.CONTROL;
import static com.science.gtnl.client.projector.ProjectorWidgets.LINE;
import static com.science.gtnl.client.projector.ProjectorWidgets.MENU;
import static com.science.gtnl.client.projector.ProjectorWidgets.SMALL;
import static com.science.gtnl.client.projector.ProjectorWidgets.column;
import static com.science.gtnl.client.projector.ProjectorWidgets.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.inventory.InventoryTypes;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnllib.gui.theme.AE2Theme;
import com.gtnllib.gui.theme.ae2.AE2Draw;
import com.gtnllib.gui.theme.ae2.AE2Icon;
import com.gtnllib.gui.theme.ae2.AE2PanelPainter;
import com.gtnllib.gui.theme.ae2.AE2Variant;
import com.gtnllib.gui.widget.AE2Button;
import com.gtnllib.gui.widget.FadingNotice;
import com.gtnllib.gui.widget.IconButton;
import com.gtnllib.gui.widget.IconRowList;
import com.gtnllib.gui.widget.IconRowList.Row;
import com.gtnllib.gui.widget.ListRow;
import com.gtnllib.gui.widget.PageTabs;
import com.gtnllib.gui.widget.VerticalButtonBar;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.gui.ScreenThemes;
import com.science.gtnl.common.item.items.MultiblockProjector;
import com.science.gtnl.common.projector.ProjectorMachines;
import com.science.gtnl.common.projector.ProjectorOrientation;
import com.science.gtnl.common.projector.ProjectorPlan;
import com.science.gtnl.common.projector.ProjectorSimulation;
import com.science.gtnl.common.projector.ProjectorStructure;
import com.science.gtnl.common.projector.ProjectorStructure.Choice;
import com.science.gtnl.common.projector.ProjectorStructure.Material;
import com.science.gtnl.common.structureanimation.StructureAnimationStyle;
import com.science.gtnl.config.ClientConfig;
import com.science.gtnl.config.ConfigRegistry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

@SideOnly(Side.CLIENT)
public class ProjectorGui extends ModularPanel {

    public static final String LANG = "gtnl.projector.gui.", POSE = "gtnl.projector.pose.";
    public static final int MACHINES = 0, PARAMETERS = 1, HATCHES = 2, MATERIALS = 3;
    private static final String NAME = "multiblock_projector";
    private static final int WIDTH = MultiblockProjector.GUI_WIDTH, HEIGHT = MultiblockProjector.GUI_HEIGHT;
    private static final int BODY_LEFT = VerticalButtonBar.WIDTH - VerticalButtonBar.OVERLAP,
        BODY_RIGHT = AE2Theme.PAGE_TAB_SIZE - AE2Theme.PAGE_TAB_INSET, PAD = 8, TOP = 6, TABS_TOP = 22,
        STATUS_INSET = 5, REFRESH_TICKS = 20, NOTICE_MILLIS = 3000, PLAIN_MUTED = 0xFF808080;
    private static final int CONTENT_WIDTH = WIDTH - BODY_LEFT - BODY_RIGHT - 2 * PAD,
        CONTENT_HEIGHT = HEIGHT - TOP - PAD, BODY_HEIGHT = CONTENT_HEIGHT - LINE - 4;
    public static final int RIGHT_WIDTH = 176, PAGE_HEIGHT = BODY_HEIGHT - LINE - BUTTON - 8;
    private static final int PREVIEW_WIDTH = CONTENT_WIDTH - RIGHT_WIDTH - 6;
    private static final String[] TAB_NAMES = { "tab_machines", "tab_parameters", "tab_hatches", "tab_materials" };
    private static final AE2Icon[] TAB_ICONS = { AE2Icon.BLOCK, AE2Icon.GEAR, AE2Icon.PROCESSOR, AE2Icon.FILE };
    private static int lastPage = -1, lastLayerMachine, lastLayer = ProjectorPreviewWidget.ALL_LAYERS;

    public final ProjectorPlan working;
    public final ProjectorHatchPage hatches;
    public final AE2Variant variant;
    public final int muted, wellWidth;
    public boolean configChanged;
    private final int location;
    private final ProjectorPlan stored;
    private final String themeId = ScreenThemes.projector();
    private final int goodColor, badColor;
    private final ProjectorPreviewWidget preview;
    private final ProjectorMachineTab machines;
    private Int2IntOpenHashMap have = new Int2IntOpenHashMap();
    private ProjectorStructure shown;
    private int page, ticks;
    private final FadingNotice notice = new FadingNotice().duration(NOTICE_MILLIS);
    private boolean hasSelection, displayChosen;
    private int selectedA, selectedB, selectedC;

    public ProjectorGui(PlayerInventoryGuiData data) {
        super(NAME);
        size(WIDTH, HEIGHT);
        background(AE2PanelPainter.panelBackground(this, BODY_LEFT, BODY_RIGHT)).disableThemeBackground(true)
            .disableHoverThemeBackground(true);
        variant = AE2Theme.variant(themeId);
        muted = variant == null ? PLAIN_MUTED : variant.colors.muted();
        goodColor = variant == null ? IconRowList.GOOD : variant.colors.good();
        badColor = variant == null ? IconRowList.BAD : variant.colors.bad();
        wellWidth = variant == null ? RIGHT_WIDTH : RIGHT_WIDTH - IconRowList.AE2_SCROLL_COLUMN;
        EntityPlayer player = data.getPlayer();
        int index = data.getSlotIndex();
        location = data.getInventoryType() == InventoryTypes.BAUBLES ? -index - 1
            : index == player.inventory.currentItem ? 0 : index + 1;
        stored = ProjectorPlan.read(data.getUsedItemStack());
        working = stored.copy();
        machines = new ProjectorMachineTab(this);
        refreshInventory(player);
        if (!working.hasMachine() && machines.firstCarried() > 0) selectMachine(machines.firstCarried());
        page = !working.hasMachine() ? MACHINES : lastPage >= 0 ? lastPage : PARAMETERS;
        int[] spot = ProjectorClient.takeSpot(working.machine);
        if (spot != null) {
            hasSelection = true;
            selectedA = spot[0];
            selectedB = spot[1];
            selectedC = spot[2];
            page = HATCHES;
        }
        preview = new ProjectorPreviewWidget(this::structure).selection(this::selectedCell, this::select)
            .markers(() -> ClientConfig.projectorHatchOutlines, working)
            .facing(() -> working.hasMachine() ? working.facing : null);
        if (working.machine == lastLayerMachine) preview.restoreLayer(lastLayer);
        hatches = new ProjectorHatchPage(this::structure, working, this::selectedCell, this::select);
        child(
            column().pos(BODY_LEFT + PAD, TOP)
                .size(CONTENT_WIDTH, CONTENT_HEIGHT)
                .childPadding(4)
                .child(titleRow())
                .child(
                    Flow.row()
                        .size(CONTENT_WIDTH, BODY_HEIGHT)
                        .childPadding(6)
                        .child(previewColumn())
                        .child(sideColumn())));
        buildTabs();
        child(toolbar());
    }

    public ModularScreen screen() {
        int channelMenus = ProjectorParameterTab.CHANNEL_MENUS;
        String[] menus = new String[channelMenus + 5];
        for (int i = 0; i < channelMenus; i++) menus[i] = MENU + "channel_" + i;
        menus[channelMenus] = MENU + "hatch_tier";
        menus[channelMenus + 1] = MENU + "kind";
        menus[channelMenus + 2] = MENU + "variant";
        menus[channelMenus + 3] = MENU + "casing";
        menus[channelMenus + 4] = MENU + "block";
        AE2Theme.useMenuTheme(themeId, ScienceNotLeisure.MODID, NAME, menus);
        return new ModularScreen(ScienceNotLeisure.MODID, this) {

            @Override
            public void onClose() {
                super.onClose();
                closed();
            }
        }.useTheme(themeId);
    }

    public IDrawable well() {
        return AE2PanelPainter.well(this, wellWidth);
    }

    public ListRow controlRow(String key, Widget<?> control) {
        return new ListRow(() -> StatCollector.translateToLocal(key)).width(wellWidth)
            .control(control, CONTROL, SMALL);
    }

    public ListRow header(String key) {
        return ListRow.header(() -> StatCollector.translateToLocal(key))
            .width(wellWidth);
    }

    private IWidget titleRow() {
        return Flow.row()
            .size(CONTENT_WIDTH, LINE)
            .childPadding(8)
            .child(text(StatCollector.translateToLocal(LANG + "title")))
            .child(
                IKey.dynamic(this::summaryLine)
                    .asWidget()
                    .color(muted)
                    .alignment(Alignment.CenterLeft)
                    .expanded()
                    .height(LINE));
    }

    private IWidget previewColumn() {
        return column().size(PREVIEW_WIDTH, BODY_HEIGHT)
            .childPadding(4)
            .child(
                new ParentWidget<>().size(PREVIEW_WIDTH, BODY_HEIGHT - BUTTON - 4)
                    .child(
                        preview.hoverText(this::hoverLine)
                            .sizeRel(1f))
                    .child(
                        notice.left(4)
                            .right(4)
                            .bottom(4)
                            .height(12))
                    .child(statusIcon()))
            .child(
                Flow.row()
                    .size(PREVIEW_WIDTH, BUTTON)
                    .childPadding(2)
                    .child(new IconButton(() -> AE2Icon.REMOVE, () -> preview.stepLayer(-1)))
                    .child(
                        IKey.dynamic(this::layerLabel)
                            .asWidget()
                            .alignment(Alignment.Center)
                            .width(32)
                            .height(BUTTON))
                    .child(new IconButton(() -> AE2Icon.ADD, () -> preview.stepLayer(1)))
                    .child(
                        new AE2Button(
                            () -> StatCollector
                                .translateToLocal(ProjectorClient.LANG + "display." + working.display.name()),
                            this::cycleDisplay).expanded()
                                .height(BUTTON)));
    }

    private IWidget sideColumn() {
        ParentWidget<?> pages = new ParentWidget<>().size(RIGHT_WIDTH, PAGE_HEIGHT);
        pages.child(onPage(machines.build(), MACHINES));
        pages.child(onPage(new ProjectorParameterTab(this).build(), PARAMETERS));
        pages.child(onPage(new ProjectorHatchTab(this).build(), HATCHES));
        pages.child(onPage(new IconRowList(this::materialRows).sizeRel(1f), MATERIALS));
        return column().size(RIGHT_WIDTH, BODY_HEIGHT)
            .childPadding(4)
            .child(
                IKey.dynamic(() -> StatCollector.translateToLocal(LANG + TAB_NAMES[page]))
                    .asWidget()
                    .alignment(Alignment.CenterLeft)
                    .widthRel(1f)
                    .height(LINE))
            .child(pages)
            .child(
                Flow.row()
                    .size(RIGHT_WIDTH, BUTTON)
                    .childPadding(2)
                    .child(
                        new AE2Button(this::projectLabel, this::project).expanded()
                            .height(BUTTON))
                    .child(
                        new AE2Button(() -> StatCollector.translateToLocal(LANG + "build"), ProjectorBuildRequest::send)
                            .available(ProjectorBuildRequest::canBuild)
                            .width(RIGHT_WIDTH / 4)
                            .height(BUTTON))
                    .child(
                        new AE2Button(
                            () -> StatCollector.translateToLocal(LANG + "style." + working.buildStyle.name()),
                            this::cycleBuildStyle).width(RIGHT_WIDTH / 6)
                                .height(BUTTON))
                    .child(
                        new AE2Button(() -> StatCollector.translateToLocal(LANG + "clear"), this::clearPlan)
                            .width(RIGHT_WIDTH / 4)
                            .height(BUTTON)));
    }

    private void buildTabs() {
        PageTabs tabs = new PageTabs(() -> page, index -> page = index);
        for (int i = 0; i < TAB_ICONS.length; i++) {
            int index = i;
            tabs.tab(() -> TAB_ICONS[index], () -> StatCollector.translateToLocal(LANG + TAB_NAMES[index]));
        }
        child(tabs.pos(WIDTH - BODY_RIGHT - AE2Theme.PAGE_TAB_INSET, TABS_TOP));
    }

    private IWidget toolbar() {
        return new VerticalButtonBar()
            .button(
                () -> ClientConfig.projectorOutlinesThroughBlocks ? AE2Icon.VISIBLE : AE2Icon.INVISIBLE,
                () -> StatCollector.translateToLocal(LANG + "outline_through_blocks"),
                this::toggleSeeThrough)
            .button(
                () -> AE2Icon.MAXIMIZE,
                () -> StatCollector.translateToLocal(LANG + "reset_view"),
                preview::resetCamera)
            .button(() -> AE2Icon.ARROW_UP, () -> StatCollector.translateToLocal(LANG + "raise_one"), () -> raise(1))
            .button(() -> AE2Icon.ARROW_DOWN, () -> StatCollector.translateToLocal(LANG + "lower_one"), () -> raise(-1))
            .toggle(
                () -> AE2Icon.DOWNLOAD,
                () -> StatCollector.translateToLocal(LANG + "ground_snap"),
                () -> working.groundSnap,
                this::toggleGroundSnap)
            .button(() -> AE2Icon.SHIFT_BACKWARD, this::turnTooltip, this::turnClick)
            .button(
                () -> AE2Icon.REVERSE,
                () -> StatCollector.translateToLocal(POSE + "mirror"),
                () -> turnMachine(valid -> ProjectorOrientation.mirror(working.facing, valid)))
            .button(
                () -> AE2Icon.FLIP_VERTICAL,
                () -> StatCollector.translateToLocal(POSE + "flip"),
                () -> turnMachine(valid -> ProjectorOrientation.flipVertical(working.facing, valid)))
            .button(
                this::frontIcon,
                () -> ProjectorClient.frontLabel(working.facing.getDirection()),
                () -> turnMachine(
                    valid -> ProjectorOrientation.withFront(
                        working.facing,
                        ProjectorOrientation.nextFront(working.facing.getDirection()),
                        valid)))
            .button(
                () -> AE2Icon.REFRESH,
                () -> StatCollector.translateToLocal(POSE + "rotate"),
                () -> turnMachine(valid -> ProjectorOrientation.nextRotation(working.facing, valid)))
            .toggle(
                () -> AE2Icon.MINIMIZE,
                () -> StatCollector.translateToLocal(LANG + "center_pivot"),
                () -> working.centerPivot,
                () -> working.centerPivot = !working.centerPivot)
            .pos(0, 0);
    }

    private AE2Icon frontIcon() {
        ForgeDirection front = working.facing.getDirection();
        return front == ForgeDirection.UP ? AE2Icon.MOVE_TO_UP
            : front == ForgeDirection.DOWN ? AE2Icon.MOVE_TO_DOWN : AE2Icon.MOVE_TO;
    }

    private void toggleGroundSnap() {
        working.groundSnap = !working.groundSnap;
        if (working.groundSnap) working.verticalOffset = Math.max(0, working.verticalOffset);
    }

    private void turnTooltip(RichTooltip tooltip) {
        tooltip.addLine(StatCollector.translateToLocal(LANG + "turn"));
        tooltip.addLine(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal(LANG + "turn_hint"));
    }

    private boolean turnClick(int mouse) {
        if (mouse != 0 && mouse != 1) return false;
        int quarterTurns = mouse == 0 ? 1 : -1;
        turnMachine(valid -> ProjectorOrientation.rotateY(working.facing, quarterTurns, valid));
        return true;
    }

    private Widget<?> onPage(Widget<?> widget, int index) {
        return widget.setEnabledIf(w -> page == index);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (++ticks % REFRESH_TICKS == 0) refreshInventory(Minecraft.getMinecraft().thePlayer);
    }

    private void closed() {
        lastPage = page;
        lastLayerMachine = working.machine;
        lastLayer = preview.layer();
        preview.release();
        if (configChanged || hatches.configChanged()) ConfigRegistry.save(ClientConfig.class);
        if (working.isPlaced() && working.machine != stored.machine) working.state = ProjectorPlan.State.FOLLOW;
        if (!working.writeToNBT()
            .equals(stored.writeToNBT())) ProjectorClient.send(location, working);
    }

    public ProjectorStructure structure() {
        if (!working.hasMachine()) return shown = null;
        ProjectorStructure current = ProjectorStructures.get(working.settings());
        if (current != null) shown = current;
        else if (shown != null && shown.settings.machine() != working.machine) shown = null;
        return shown;
    }

    private boolean simulating() {
        return working.hasMachine() && ProjectorStructures.get(working.settings()) == null;
    }

    public void selectMachine(int id) {
        if (id == working.machine) return;
        working.selectMachine(id);
        working.facing = ProjectorClient.initialFacing(id);
        hasSelection = false;
        machines.showMachine();
    }

    public void notice(String key) {
        notice.show(StatCollector.translateToLocal(LANG + key), IconRowList.BAD & 0xFFFFFF);
    }

    private void project() {
        if (!working.hasMachine()) {
            notice("no_machine");
            return;
        }
        if (!working.isPlaced() || working.machine != stored.machine) {
            if (working.state == ProjectorPlan.State.IDLE || working.machine != stored.machine) {
                ProjectorStructure structure = structure();
                if (!displayChosen)
                    working.display = structure != null && structure.size > ProjectorClient.BLOCK_PREVIEW_LIMIT
                        ? ProjectorPlan.Display.BOX
                        : ProjectorPlan.Display.DIFFERENCE;
            }
            working.state = ProjectorPlan.State.FOLLOW;
        }
        ProjectorClient.send(location, working);
        stored.readFromNBT(working.writeToNBT());
        getScreen().close();
    }

    private void cycleBuildStyle() {
        StructureAnimationStyle[] values = StructureAnimationStyle.values();
        working.buildStyle = values[(working.buildStyle.ordinal() + 1) % values.length];
    }

    private void cycleDisplay() {
        ProjectorPlan.Display[] values = ProjectorPlan.Display.values();
        working.display = values[(working.display.ordinal() + 1) % values.length];
        displayChosen = true;
    }

    private void clearPlan() {
        working.clear();
        hasSelection = false;
        machines.showMachine();
        ProjectorClient.send(location, working);
        stored.readFromNBT(working.writeToNBT());
    }

    private void toggleSeeThrough() {
        ClientConfig.projectorOutlinesThroughBlocks = !ClientConfig.projectorOutlinesThroughBlocks;
        configChanged = true;
    }

    private void raise(int step) {
        if (working.state == ProjectorPlan.State.LOCKED) working.y = Math.max(0, Math.min(255, working.y + step));
        else working.verticalOffset = Math.max(
            working.groundSnap ? 0 : -ProjectorPlan.MAX_OFFSET,
            Math.min(ProjectorPlan.MAX_OFFSET, working.verticalOffset + step));
    }

    private String projectLabel() {
        return StatCollector
            .translateToLocal(LANG + (working.isPlaced() && working.machine == stored.machine ? "apply" : "project"));
    }

    private void refreshInventory(EntityPlayer player) {
        have = ProjectorClient.inventory(player);
        machines.refreshCarried(player);
    }

    private void turnMachine(Function<Predicate<ExtendedFacing>, ExtendedFacing> next) {
        if (working.hasMachine()) turn(next.apply(ProjectorMachines.validFacings(working.machine)));
    }

    private void turn(ExtendedFacing next) {
        if (working.state == ProjectorPlan.State.ANCHORED) {
            notice("anchored");
        } else if (next == working.facing) {
            notice("invalid_facing");
        } else {
            ProjectorClient.reorient(working, next, structure());
        }
    }

    private List<Row> materialRows() {
        List<Row> rows = new ArrayList<>();
        ProjectorStructure structure = structure();
        if (structure == null) return rows;
        rows.add(
            Row.header(
                StatCollector
                    .translateToLocalFormatted(LANG + "materials_total", structure.materials.size(), structure.size)));
        for (Material material : structure.materials) {
            ItemStack stack = material.stack();
            int owned = have.get(Choice.idOf(stack.getItem(), stack.getItemDamage()));
            rows.add(
                Row.colored(
                    stack,
                    stack.getDisplayName(),
                    Math.min(owned, material.count()) + "/" + material.count(),
                    owned >= material.count() ? IconRowList.GOOD : IconRowList.BAD));
        }
        for (Object line : Minecraft.getMinecraft().fontRenderer
            .listFormattedStringToWidth(StatCollector.translateToLocal(LANG + "materials_ae_note"), wellWidth - 8))
            rows.add(Row.colored(null, (String) line, "", IconRowList.HEADER));
        return rows;
    }

    private int selectedCell() {
        ProjectorStructure structure = structure();
        return structure == null || !hasSelection ? -1 : structure.indexOf(selectedA, selectedB, selectedC);
    }

    private void select(int cell) {
        ProjectorStructure structure = structure();
        if (structure == null || cell < 0) return;
        hasSelection = true;
        selectedA = structure.a[cell];
        selectedB = structure.b[cell];
        selectedC = structure.c[cell];
        Choice choice = structure.choiceAt(cell);
        if (structure.slotAt(cell) != null || choice != null && choice.channel() == null) page = HATCHES;
        else if (choice != null) page = PARAMETERS;
    }

    private String layerLabel() {
        int layer = preview.layer();
        return layer == ProjectorPreviewWidget.ALL_LAYERS ? StatCollector.translateToLocal(LANG + "all_layers")
            : StatCollector.translateToLocalFormatted(LANG + "layer", layer);
    }

    private String summaryLine() {
        if (!working.hasMachine()) return "";
        String name = ProjectorMachines.stackOf(working.machine)
            .getDisplayName();
        ProjectorStructure structure = structure();
        String state;
        if (structure == null || simulating()) {
            ProjectorSimulation running = ProjectorStructures.running(working.settings());
            state = StatCollector.translateToLocalFormatted(LANG + "simulating", running == null ? 0 : running.rounds);
        } else {
            state = StatCollector.translateToLocalFormatted(
                LANG + "summary",
                structure.size,
                structure.maxA - structure.minA + 1,
                structure.maxB - structure.minB + 1,
                structure.maxC - structure.minC + 1);
        }
        return StatCollector.translateToLocalFormatted(ProjectorClient.LANG + "joined", name, state);
    }

    private Widget<?> statusIcon() {
        IDrawable look = (context, x, y, width, height, theme) -> {
            ProjectorStructure structure = finishedStructure();
            if (structure == null || !hasProblem(structure) && !structure.checked) return;
            boolean good = !hasProblem(structure) && structure.formed;
            AE2Draw.drawTintedIcon(good ? AE2Icon.CHECKMARK : AE2Icon.EXCLAMATION, x, y, good ? goodColor : badColor);
        };
        return new Widget<>().size(AE2Theme.ICON_SIZE, AE2Theme.ICON_SIZE)
            .top(STATUS_INSET)
            .right(STATUS_INSET)
            // Sibling backgrounds are all drawn before the preview, which would paint over this one.
            .overlay(look)
            .disableThemeBackground(true)
            .disableHoverThemeBackground(true)
            .tooltipAutoUpdate(true)
            .tooltipBuilder(this::statusTooltip);
    }

    private ProjectorStructure finishedStructure() {
        return simulating() ? null : structure();
    }

    private static boolean hasProblem(ProjectorStructure structure) {
        return !structure.failure.isEmpty() || !structure.limit.isEmpty();
    }

    private void statusTooltip(RichTooltip tooltip) {
        ProjectorStructure structure = finishedStructure();
        if (structure == null) return;
        if (!structure.failure.isEmpty()) tooltip.addLine(
            EnumChatFormatting.RED + StatCollector
                .translateToLocalFormatted(LANG + "failure", StatCollector.translateToLocal(structure.failure)));
        else if (!structure.limit.isEmpty())
            tooltip.addLine(EnumChatFormatting.RED + StatCollector.translateToLocal(LANG + "limit." + structure.limit));
        else if (!structure.checked) return;
        else if (structure.formed)
            tooltip.addLine(EnumChatFormatting.GREEN + StatCollector.translateToLocal(LANG + "formed"));
        else {
            tooltip.addLine(EnumChatFormatting.RED + StatCollector.translateToLocal(LANG + "not_formed"));
            for (String error : structure.errors) tooltip.addLine(EnumChatFormatting.GRAY + error);
        }
    }

    private String hoverLine() {
        ProjectorStructure structure = structure();
        int cell = preview.hovered();
        if (structure == null || cell < 0 || cell >= structure.size) return "";
        return structure.items[cell] == null ? structure.blocks[cell].getLocalizedName()
            : structure.items[cell].getDisplayName();
    }
}
