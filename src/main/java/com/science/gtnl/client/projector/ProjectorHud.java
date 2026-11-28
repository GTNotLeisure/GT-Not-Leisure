package com.science.gtnl.client.projector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.WorldEvent;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.factory.ClientGUI;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widget.sizer.StandardResizer;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnllib.gui.theme.ae2.AE2Icon;
import com.gtnllib.gui.theme.ae2.AE2Look;
import com.gtnllib.gui.theme.ae2.AE2PanelPainter;
import com.gtnllib.gui.widget.AE2Button;
import com.gtnllib.gui.widget.NumberEntryWidget;
import com.gtnllib.gui.widget.VerticalButtonBar;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.client.structureanimation.StructureAnimations;
import com.science.gtnl.common.gui.ScreenThemes;
import com.science.gtnl.common.packet.ProjectorActionPacket;
import com.science.gtnl.common.projector.ProjectorBuilder;
import com.science.gtnl.common.projector.ProjectorMachines;
import com.science.gtnl.common.projector.ProjectorOrientation;
import com.science.gtnl.common.projector.ProjectorPlan;
import com.science.gtnl.common.projector.ProjectorStructure;
import com.science.gtnl.common.structureanimation.StructureAnimationStyle;
import com.science.gtnl.config.ClientConfig;
import com.science.gtnl.config.ConfigRegistry;
import com.science.gtnl.config.MachineConfig;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@EventBusSubscriber(side = Side.CLIENT)
public class ProjectorHud {

    public static final String LANG = "gtnl.projector.hud.", ELLIPSIS = "...";
    public static final int WIDTH = 150, PAD = 6, LINE = 12, FIELD = 14, SMALL = 24, AXIS = 8, GAP = 2, BUTTON = 16,
        SHIFT_STEP = 10, HELP_LINES = 6, HIDDEN_MOUSE = -10_000, WORLD_EDGE = 30_000_000, MAX_Y = 255,
        BODY = VerticalButtonBar.WIDTH - VerticalButtonBar.OVERLAP, INNER = WIDTH - PAD * 2,
        NAME = INNER - SMALL * 2 - GAP, COORDINATE = INNER - AXIS - SMALL * 2 - GAP * 3;
    public static final KeyBinding HOLD = new KeyBinding(
        "key.gtnl.projector_hud",
        Keyboard.KEY_LMENU,
        ProjectorInput.CATEGORY);

    private static ModularScreen floating;
    private static String floatingTheme;
    private static int floatingWidth, floatingHeight;
    private static boolean wasHeld, moved;

    private ProjectorHud() {}

    public static void register() {
        ClientRegistry.registerKeyBinding(HOLD);
    }

    public static boolean shown() {
        Minecraft mc = Minecraft.getMinecraft();
        ProjectorPlan plan = ProjectorClient.plan;
        if (mc.thePlayer == null || plan == null || !plan.hasMachine() || plan.state == ProjectorPlan.State.IDLE)
            return false;
        return !plan.isPlaced() || plan.dimension == mc.thePlayer.dimension;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        boolean held = held();
        if (held && !wasHeld && mc.currentScreen == null && shown()) open();
        wasHeld = held;
        if (floating != null && shown()) floating.onUpdate();
    }

    @SubscribeEvent
    public static void overlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.gameSettings.hideGUI || mc.gameSettings.showDebugInfo || !shown()) return;
        draw(event.resolution, event.partialTicks);
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload event) {
        if (event.world.isRemote) floating = null;
    }

    private static void draw(ScaledResolution resolution, float partialTicks) {
        String theme = ScreenThemes.projector();
        int width = resolution.getScaledWidth(), height = resolution.getScaledHeight();
        if (floating == null || !Objects.equals(theme, floatingTheme)) {
            floating = new ModularScreen(ScienceNotLeisure.MODID, panel(false, new ArrayList<>())).useTheme(theme);
            floating.constructOverlay(new GuiScreen());
            floatingTheme = theme;
            floatingWidth = -1;
        }
        if (width != floatingWidth || height != floatingHeight) {
            floating.onResize(width, height);
            floatingWidth = width;
            floatingHeight = height;
        }
        floating.getContext()
            .updateState(HIDDEN_MOUSE, HIDDEN_MOUSE, partialTicks);
        // A screen leaves lighting, depth and blending set for the GUI that normally follows it, not for the HUD.
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glColor4f(1, 1, 1, 1);
            floating.drawScreen();
            floating.drawForeground();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void open() {
        ClientGUI.open(new Interactive(ScreenThemes.projector(), new ArrayList<>()));
    }

    private static boolean held() {
        int code = HOLD.getKeyCode();
        if (code == Keyboard.KEY_NONE) return false;
        return code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
    }

    private static ModularPanel panel(boolean interactive, List<NumberEntryWidget> entries) {
        int muted = AE2Look.ofTheme(ScreenThemes.projector())
            .listMuted();
        ModularPanel panel = new ModularPanel(interactive ? "projector_hud" : "projector_hud_floating");
        panel.background(AE2PanelPainter.panelBackground(panel, BODY, 0))
            .disableThemeBackground(true)
            .width(BODY + WIDTH)
            .coverChildrenHeight();
        Flow content = Flow.column()
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .pos(BODY, 0)
            .width(WIDTH)
            .coverChildrenHeight()
            .padding(PAD)
            .childPadding(GAP)
            .child(
                row(LINE).child(
                    IKey.dynamic(ProjectorHud::machineName)
                        .asWidget()
                        .size(NAME, LINE))
                    .child(
                        IKey.dynamic(ProjectorHud::stateLabel)
                            .asWidget()
                            .alignment(Alignment.CenterRight)
                            .size(SMALL * 2, LINE)))
            .child(
                row(LINE).child(label(LANG + "position", muted).size(AXIS + GAP + COORDINATE, LINE))
                    .child(
                        label(LANG + "stack_count", muted).alignment(Alignment.Center)
                            .size(SMALL, LINE))
                    .child(
                        label(LANG + "stack_gap", muted).alignment(Alignment.Center)
                            .size(SMALL, LINE)));
        String[] axes = { "X", "Y", "Z" };
        for (int axis = 0; axis < 3; axis++) content.child(axisRow(axis, axes[axis], entries));
        content.child(
            IKey.dynamic(ProjectorHud::stackSummary)
                .asWidget()
                .color(muted)
                .alignment(Alignment.Center)
                .widthRel(1f)
                .height(LINE - 2))
            .child(
                buttons(
                    explained(
                        new AE2Button(ProjectorHud::lockLabel, ProjectorHud::toggleLock)
                            .available(ProjectorHud::movable),
                        null,
                        ProjectorHud::moveBlocked),
                    new AE2Button(() -> StatCollector.translateToLocal(LANG + "full"), ProjectorClient::open)))
            .child(
                buttons(
                    explained(
                        new AE2Button(
                            () -> StatCollector.translateToLocal("gtnl.projector.gui.build"),
                            ProjectorHud::build).available(ProjectorHud::canBuild),
                        null,
                        ProjectorHud::buildBlocked),
                    new AE2Button(ProjectorHud::styleLabel, ProjectorHud::cycleStyle)
                        .available(ProjectorHud::editable)))
            .child(
                row(BUTTON).child(action(ProjectorActionPacket.UNDO, "undo"))
                    .child(action(ProjectorActionPacket.DISMANTLE, "dismantle"))
                    .child(action(ProjectorActionPacket.CANCEL, "cancel")));
        if (!interactive) content.child(
            IKey.dynamic(ProjectorHud::hint)
                .asWidget()
                .color(muted)
                .alignment(Alignment.Center)
                .widthRel(1f)
                .height(LINE - 2));
        DragBar drag = new DragBar().pos(BODY, 0)
            .size(WIDTH, PAD + LINE);
        if (interactive) drag.addTooltipLine(StatCollector.translateToLocal(LANG + "drag"));
        panel.child(toolbar(interactive))
            .child(content)
            .child(drag);
        place(panel);
        return panel;
    }

    private static Flow row(int height) {
        return Flow.row()
            .widthRel(1f)
            .height(height)
            .childPadding(GAP);
    }

    private static AE2Button action(int action, String key) {
        AE2Button button = new AE2Button(
            () -> StatCollector.translateToLocal(LANG + (ProjectorBuildRequest.armed(action) ? "confirm" : key)),
            () -> ProjectorBuildRequest.act(action));
        if (action != ProjectorActionPacket.CANCEL) button.available(ProjectorBuildRequest::canDismantle);
        return explained(
            button,
            LANG + key + ".tooltip",
            action == ProjectorActionPacket.CANCEL ? () -> null : ProjectorHud::dismantleBlocked).expanded()
                .height(BUTTON);
    }

    // A greyed-out button says why it cannot be used right now.
    private static AE2Button explained(AE2Button button, String description, Supplier<String> blocked) {
        button.tooltipAutoUpdate(true)
            .tooltipBuilder(tooltip -> {
                if (description != null) tooltip.addLine(StatCollector.translateToLocal(description));
                String reason = blocked.get();
                if (reason != null) tooltip
                    .addLine(EnumChatFormatting.RED + StatCollector.translateToLocal(LANG + "blocked." + reason));
            });
        return button;
    }

    private static String commonBlocked() {
        ProjectorPlan plan = ProjectorClient.plan;
        if (!MachineConfig.projectorBuildEnabled) return "disabled";
        if (plan == null || !plan.isPlaced()) return "not_placed";
        return StructureAnimations.ownActive() ? "running" : null;
    }

    private static String buildBlocked() {
        String common = commonBlocked();
        if (common != null) return common;
        ProjectorCheck check = ProjectorClient.CHECK;
        if (!check.isActive() || !check.scanned) return "checking";
        return check.missing == 0 && check.wrong == 0 ? "complete" : null;
    }

    private static String dismantleBlocked() {
        return commonBlocked();
    }

    private static String moveBlocked() {
        ProjectorPlan plan = ProjectorClient.plan;
        return plan != null && plan.state == ProjectorPlan.State.ANCHORED ? "anchored" : null;
    }

    private static Flow buttons(AE2Button first, AE2Button second) {
        return row(BUTTON).child(
            first.expanded()
                .height(BUTTON))
            .child(
                second.expanded()
                    .height(BUTTON));
    }

    private static TextWidget<?> label(String key, int color) {
        return IKey.lang(key)
            .asWidget()
            .color(color);
    }

    private static Flow axisRow(int axis, String name, List<NumberEntryWidget> entries) {
        NumberEntryWidget position = entry(entries).bounds(axis == 1 ? 0 : -WORLD_EDGE, axis == 1 ? MAX_Y : WORLD_EDGE)
            .fineStep(() -> positionStep(axis))
            .follow(() -> coordinate(axis))
            .onChange(value -> move(axis, (int) value));
        NumberEntryWidget count = entry(entries).arrows(false)
            .bounds(-ProjectorPlan.MAX_STACK, ProjectorPlan.MAX_STACK)
            .follow(() -> stackCount(axis))
            .onChange(value -> setStack(axis, nextCount(axis, (int) value), stackGap(axis)));
        NumberEntryWidget gap = entry(entries).arrows(false)
            .bounds(-ProjectorPlan.MAX_STACK_GAP, ProjectorPlan.MAX_STACK_GAP)
            .follow(() -> stackGap(axis))
            .onChange(value -> setStack(axis, stackCount(axis), (int) value));
        return row(FIELD).child(
            IKey.str(name)
                .asWidget()
                .size(AXIS, FIELD))
            .child(position.size(COORDINATE, FIELD))
            .child(count.size(SMALL, FIELD))
            .child(gap.size(SMALL, FIELD));
    }

    private static NumberEntryWidget entry(List<NumberEntryWidget> entries) {
        NumberEntryWidget entry = new NumberEntryWidget().wheel(true)
            .textAlignment(Alignment.Center);
        entry.available(ProjectorHud::movable);
        entries.add(entry);
        return entry;
    }

    private static long positionStep(int axis) {
        ProjectorStructure structure = ProjectorClient.structure;
        if (GuiScreen.isCtrlKeyDown() && structure != null) {
            int[] bounds = structure.worldBounds(ProjectorClient.facing);
            return bounds[axis + 3] - bounds[axis] + 1;
        }
        return GuiScreen.isShiftKeyDown() ? SHIFT_STEP : 1;
    }

    private static int stackCount(int axis) {
        ProjectorPlan plan = ProjectorClient.plan;
        return plan == null ? 1 : plan.stackCount[axis];
    }

    private static int stackGap(int axis) {
        ProjectorPlan plan = ProjectorClient.plan;
        return plan == null ? 0 : plan.stackGap[axis];
    }

    // 1 and -1 both mean a single copy, so one step down from 1 goes straight to two copies the other way.
    private static int nextCount(int axis, int value) {
        if (value != 0 && value != -1) return value;
        return value == 0 && stackCount(axis) == 1 ? -2 : 1;
    }

    private static void setStack(int axis, int count, int gap) {
        if (!movable()) return;
        ProjectorPlan check = ProjectorClient.plan.copy();
        if (!check.setStack(axis, count, gap)) {
            ProjectorInput.hint(LANG + "stack_limit");
            return;
        }
        ProjectorStructure structure = ProjectorClient.structure;
        if (structure != null) {
            int[] single = structure.worldBounds(ProjectorClient.facing);
            if (!ProjectorBuilder.fits(structure, single, check.stackOffsets(single))) {
                ProjectorInput.hint(LANG + "stack_too_big");
                return;
            }
        }
        ProjectorInput.edit(copy -> copy.setStack(axis, count, gap));
    }

    private static void clearStack() {
        if (movable() && ProjectorClient.plan.isStacked()) ProjectorInput.edit(ProjectorPlan::clearStack);
    }

    private static String stackSummary() {
        ProjectorPlan plan = ProjectorClient.plan;
        ProjectorStructure structure = ProjectorClient.structure;
        if (plan == null || structure == null) return "";
        if (plan.isStacked() && ProjectorClient.offsets.length == 3)
            return EnumChatFormatting.RED + StatCollector.translateToLocal(LANG + "stack_shrunk");
        int[] bounds = structure.worldBounds(ProjectorClient.facing);
        return StatCollector.translateToLocalFormatted(
            LANG + "stack_summary",
            plan.copies(),
            bounds[3] - bounds[0] + 1,
            bounds[4] - bounds[1] + 1,
            bounds[5] - bounds[2] + 1);
    }

    private static VerticalButtonBar toolbar(boolean interactive) {
        return new VerticalButtonBar().button(() -> AE2Icon.SHIFT_BACKWARD, tooltip -> {
            tooltip.addLine(StatCollector.translateToLocal("gtnl.projector.gui.turn"));
            tooltip
                .addLine(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("gtnl.projector.gui.turn_hint"));
        }, ProjectorHud::turn)
            .button(
                () -> AE2Icon.REVERSE,
                () -> StatCollector.translateToLocal("gtnl.projector.pose.mirror"),
                () -> turn(facing -> ProjectorOrientation.mirror(facing, ProjectorClient.validFacings())))
            .button(
                () -> AE2Icon.FLIP_VERTICAL,
                () -> StatCollector.translateToLocal("gtnl.projector.pose.flip"),
                () -> turn(facing -> ProjectorOrientation.flipVertical(facing, ProjectorClient.validFacings())))
            .button(
                ProjectorHud::frontIcon,
                () -> ProjectorClient.frontLabel(ProjectorClient.facing.getDirection()),
                ProjectorHud::nextFront)
            .button(
                () -> AE2Icon.REFRESH,
                () -> StatCollector.translateToLocal("gtnl.projector.pose.rotate"),
                () -> turn(facing -> ProjectorOrientation.nextRotation(facing, ProjectorClient.validFacings())))
            .toggle(
                () -> AE2Icon.DOWNLOAD,
                () -> StatCollector.translateToLocal("gtnl.projector.gui.ground_snap"),
                () -> ProjectorClient.plan != null && ProjectorClient.plan.groundSnap,
                ProjectorHud::toggleGround)
            .button(
                () -> AE2Icon.CLOSE,
                () -> StatCollector.translateToLocal(LANG + "stack_clear"),
                ProjectorHud::clearStack)
            .button(() -> AE2Icon.HELP, tooltip -> {
                for (int line = 0; line < HELP_LINES; line++)
                    tooltip.addLine(StatCollector.translateToLocal(LANG + "help." + line));
            }, mouse -> false)
            // MUI2 refuses recipe viewer exclusion areas on screens drawn as overlays, which the floating panel is.
            .excludeAreaInRecipeViewer(interactive)
            .pos(0, 0);
    }

    public static void place(ModularPanel panel) {
        float x = (float) ClientConfig.projectorHudX, y = (float) ClientConfig.projectorHudY;
        panel.resizer(StandardResizer::resetPosition)
            .leftRelAnchor(x, x)
            .topRelAnchor(y, y);
    }

    private static boolean editable() {
        return ProjectorClient.plan != null && ProjectorClient.plan.hasMachine()
            && ProjectorClient.plan.state != ProjectorPlan.State.IDLE;
    }

    private static boolean movable() {
        return editable() && ProjectorClient.plan.state != ProjectorPlan.State.ANCHORED;
    }

    // A placed plan is moved by editing it, and the shown position only catches up on the next client tick.
    private static int coordinate(int axis) {
        ProjectorPlan plan = ProjectorClient.plan;
        if (plan != null && plan.isPlaced()) return axis == 0 ? plan.x : axis == 1 ? plan.y : plan.z;
        return axis == 0 ? ProjectorClient.x : axis == 1 ? ProjectorClient.y : ProjectorClient.z;
    }

    private static void move(int axis, int value) {
        if (!movable() || value == coordinate(axis)) return;
        ProjectorPlan plan = ProjectorClient.plan;
        Minecraft mc = Minecraft.getMinecraft();
        ProjectorInput.edit(copy -> {
            if (plan.state == ProjectorPlan.State.FOLLOW) {
                copy.facing = ProjectorClient.facing;
                copy.dimension = mc.thePlayer.dimension;
                copy.x = ProjectorClient.x;
                copy.y = ProjectorClient.y;
                copy.z = ProjectorClient.z;
                copy.state = ProjectorPlan.State.LOCKED;
            }
            if (axis == 0) copy.x = value;
            else if (axis == 1) copy.y = Math.max(0, Math.min(MAX_Y, value));
            else copy.z = value;
        });
    }

    private static boolean turn(int mouse) {
        if (mouse != 0 && mouse != 1) return false;
        int quarterTurns = mouse == 0 ? 1 : -1;
        turn(facing -> ProjectorOrientation.rotateY(facing, quarterTurns, ProjectorClient.validFacings()));
        return true;
    }

    private static void turn(UnaryOperator<ExtendedFacing> change) {
        if (editable()) ProjectorInput.turn(change);
    }

    private static boolean canBuild() {
        ProjectorPlan plan = ProjectorClient.plan;
        return plan != null && plan.hasMachine() && plan.isPlaced() && ProjectorBuildRequest.canBuild();
    }

    private static void build() {
        if (canBuild()) ProjectorBuildRequest.send(ProjectorClient.location);
    }

    private static String styleLabel() {
        ProjectorPlan plan = ProjectorClient.plan;
        StructureAnimationStyle style = plan == null ? StructureAnimationStyle.REVEAL : plan.buildStyle;
        return StatCollector.translateToLocal("gtnl.projector.gui.style." + style.name());
    }

    private static void cycleStyle() {
        if (!editable()) return;
        StructureAnimationStyle[] values = StructureAnimationStyle.values();
        StructureAnimationStyle next = values[(ProjectorClient.plan.buildStyle.ordinal() + 1) % values.length];
        ProjectorInput.edit(copy -> copy.buildStyle = next);
    }

    private static AE2Icon frontIcon() {
        ForgeDirection front = ProjectorClient.facing.getDirection();
        return front == ForgeDirection.UP ? AE2Icon.MOVE_TO_UP
            : front == ForgeDirection.DOWN ? AE2Icon.MOVE_TO_DOWN : AE2Icon.MOVE_TO;
    }

    private static void nextFront() {
        if (!editable()) return;
        ForgeDirection next = ProjectorOrientation.nextFront(ProjectorClient.plan.facing.getDirection());
        ProjectorInput.turn(facing -> ProjectorOrientation.withFront(facing, next, ProjectorClient.validFacings()));
    }

    private static void toggleGround() {
        if (!movable()) return;
        ProjectorInput.edit(copy -> {
            copy.groundSnap = !copy.groundSnap;
            if (copy.groundSnap) copy.verticalOffset = Math.max(0, copy.verticalOffset);
        });
    }

    private static void toggleLock() {
        if (!movable()) return;
        ProjectorClient.lockKey(ProjectorClient.plan.state == ProjectorPlan.State.LOCKED);
    }

    private static String lockLabel() {
        ProjectorPlan plan = ProjectorClient.plan;
        boolean locked = plan != null && plan.state == ProjectorPlan.State.LOCKED;
        return StatCollector.translateToLocal(LANG + (locked ? "follow" : "lock"));
    }

    private static String machineName() {
        ProjectorPlan plan = ProjectorClient.plan;
        ItemStack stack = plan == null || !plan.hasMachine() ? null : ProjectorMachines.stackOf(plan.machine);
        if (stack == null) return "";
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String name = stack.getDisplayName();
        if (font.getStringWidth(name) <= NAME) return name;
        return font.trimStringToWidth(name, NAME - font.getStringWidth(ELLIPSIS)) + ELLIPSIS;
    }

    private static String stateLabel() {
        ProjectorPlan plan = ProjectorClient.plan;
        if (plan == null) return "";
        EnumChatFormatting color = plan.state == ProjectorPlan.State.FOLLOW ? EnumChatFormatting.YELLOW
            : EnumChatFormatting.GREEN;
        return color + StatCollector.translateToLocal(
            LANG + "state."
                + plan.state.name()
                    .toLowerCase(Locale.ROOT));
    }

    // What to do next for the projection as it stands, and the key that brings up the mouse.
    private static String hint() {
        int code = HOLD.getKeyCode();
        String key = code == Keyboard.KEY_NONE ? StatCollector.translateToLocal(LANG + "hint_unbound")
            : StatCollector.translateToLocalFormatted(LANG + "hint", GameSettings.getKeyDisplayString(code));
        ProjectorPlan plan = ProjectorClient.plan;
        if (plan == null) return key;
        ProjectorCheck check = ProjectorClient.CHECK;
        String step;
        if (plan.state == ProjectorPlan.State.FOLLOW) step = "follow";
        else if (plan.state == ProjectorPlan.State.ANCHORED) step = "anchored";
        else if (!check.isActive() || !check.scanned) step = "checking";
        else if (check.missing == 0 && check.wrong == 0) step = "complete";
        else if (check.missing == 0) step = "blocked";
        else step = "build";
        return StatCollector
            .translateToLocalFormatted(LANG + "next", StatCollector.translateToLocal(LANG + "next." + step), key);
    }

    public static class DragBar extends Widget<DragBar> implements Interactable {

        private int grabX, grabY;
        private boolean dragging;

        @Override
        public @NotNull Result onMousePressed(int mouseButton) {
            if (mouseButton != 0) return Result.IGNORE;
            ModularPanel panel = getPanel();
            grabX = getContext().getAbsMouseX() - panel.getArea().x;
            grabY = getContext().getAbsMouseY() - panel.getArea().y;
            dragging = true;
            return Result.SUCCESS;
        }

        @Override
        public void onMouseDrag(int mouseButton, long timeSinceClick) {
            if (!dragging) return;
            ModularPanel panel = getPanel();
            Area screen = panel.getScreen()
                .getScreenArea();
            ClientConfig.projectorHudX = fraction(
                getContext().getAbsMouseX() - grabX,
                screen.width - panel.getArea().width);
            ClientConfig.projectorHudY = fraction(
                getContext().getAbsMouseY() - grabY,
                screen.height - panel.getArea().height);
            place(panel);
            panel.scheduleResize();
            moved = true;
        }

        @Override
        public boolean onMouseRelease(int mouseButton) {
            if (!dragging) return false;
            dragging = false;
            savePosition();
            return true;
        }

        private static double fraction(int position, int room) {
            return room <= 0 ? 0 : Math.max(0, Math.min(1, (double) position / room));
        }
    }

    public static class Interactive extends ModularScreen {

        private final List<NumberEntryWidget> entries;

        public Interactive(String theme, List<NumberEntryWidget> entries) {
            super(ScienceNotLeisure.MODID, panel(true, entries));
            this.entries = entries;
            useTheme(theme);
            drawDarkBackground(false);
            pausesGame(false);
        }

        // MUI2 only tells the widget under the mouse about a release, so a drag that ends off the title bar is saved
        // here.
        @Override
        public void onUpdate() {
            super.onUpdate();
            if (moved && !Mouse.isButtonDown(0)) savePosition();
            if (!shown()) {
                close();
                return;
            }
            if (held()) return;
            for (NumberEntryWidget entry : entries) if (entry.isEditing()) return;
            close();
        }

        @Override
        public void onClose() {
            super.onClose();
            savePosition();
        }
    }

    private static void savePosition() {
        if (!moved) return;
        moved = false;
        ConfigRegistry.save(ClientConfig.class);
        floating = null;
    }
}
