package com.science.gtnl.client.projector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.UnaryOperator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
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
import com.science.gtnl.common.projector.ProjectorMachines;
import com.science.gtnl.common.projector.ProjectorOrientation;
import com.science.gtnl.common.projector.ProjectorPlan;
import com.science.gtnl.common.structureanimation.StructureAnimationStyle;
import com.science.gtnl.config.ClientConfig;
import com.science.gtnl.config.ConfigRegistry;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@EventBusSubscriber(side = Side.CLIENT)
public class ProjectorHud {

    public static final String LANG = "gtnl.projector.hud.";
    public static final int WIDTH = 150, PAD = 6, LINE = 12, ROW = 16, FIELD = 14, NUMBER = 92, BUTTON = 16,
        HIDDEN_MOUSE = -10_000, WORLD_EDGE = 30_000_000, MAX_Y = 255, TOOLBAR_BUTTONS = 6,
        BODY = VerticalButtonBar.WIDTH - VerticalButtonBar.OVERLAP,
        TOOLBAR_HEIGHT = TOOLBAR_BUTTONS * VerticalButtonBar.PITCH + 6;
    public static final KeyBinding HOLD = new KeyBinding(
        "key.gtnl.projector_hud",
        Keyboard.KEY_LMENU,
        ProjectorInput.CATEGORY);

    private static ModularScreen floating;
    private static String floatingTheme;
    private static int floatingWidth, floatingHeight;
    private static boolean wasHeld;

    private ProjectorHud() {}

    public static void register() {
        ClientRegistry.registerKeyBinding(HOLD);
    }

    public static boolean shown() {
        Minecraft mc = Minecraft.getMinecraft();
        ProjectorPlan plan = ProjectorClient.plan;
        if (mc.thePlayer == null || plan == null
            || !plan.hasMachine()
            || plan.state == ProjectorPlan.State.IDLE
            || StructureAnimations.ownActive()) return false;
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
        GL11.glPushMatrix();
        try {
            GL11.glColor4f(1, 1, 1, 1);
            floating.drawScreen();
            floating.drawForeground();
        } finally {
            GL11.glPopMatrix();
            // The screen leaves item lighting on for the GUI that normally follows it; the HUD after it must not get
            // it.
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(1, 1, 1, 1);
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
        int inner = WIDTH - PAD * 2, left = BODY + PAD;
        int muted = AE2Look.ofTheme(ScreenThemes.projector())
            .listMuted();
        int y = PAD;
        ModularPanel panel = new ModularPanel(interactive ? "projector_hud" : "projector_hud_floating");
        panel.background(AE2PanelPainter.panelBackground(panel, BODY, 0))
            .disableThemeBackground(true);
        panel.child(toolbar(interactive));
        DragBar drag = new DragBar().pos(BODY, 0)
            .size(WIDTH, PAD + LINE + 2);
        if (interactive) drag.addTooltipLine(StatCollector.translateToLocal(LANG + "drag"));
        panel.child(drag);
        panel.child(
            IKey.dynamic(ProjectorHud::machineName)
                .asWidget()
                .pos(left, y)
                .size(inner, LINE));
        panel.child(
            IKey.dynamic(ProjectorHud::stateLabel)
                .asWidget()
                .alignment(Alignment.CenterRight)
                .pos(left, y)
                .size(inner, LINE));
        y += LINE + 4;
        panel.child(header(LANG + "position", muted, left, y, inner));
        y += LINE;
        String[] axes = { "X", "Y", "Z" };
        for (int axis = 0; axis < 3; axis++) {
            int index = axis;
            panel.child(
                IKey.str(axes[axis])
                    .asWidget()
                    .pos(left, y + 3)
                    .size(inner - NUMBER, LINE));
            NumberEntryWidget entry = new NumberEntryWidget().wheel(true)
                .bounds(index == 1 ? 0 : -WORLD_EDGE, index == 1 ? MAX_Y : WORLD_EDGE)
                .textAlignment(Alignment.Center)
                .follow(() -> coordinate(index))
                .onChange(value -> move(index, (int) value));
            entry.available(ProjectorHud::movable);
            entries.add(entry);
            panel.child(
                entry.pos(left + inner - NUMBER, y)
                    .size(NUMBER, FIELD));
            y += ROW;
        }
        y += 2;
        int half = (inner - 2) / 2;
        panel.child(
            new AE2Button(ProjectorHud::lockLabel, ProjectorHud::toggleLock).available(ProjectorHud::movable)
                .pos(left, y)
                .size(half, BUTTON));
        panel.child(
            new AE2Button(() -> StatCollector.translateToLocal(LANG + "full"), ProjectorClient::open)
                .pos(left + half + 2, y)
                .size(inner - half - 2, BUTTON));
        y += BUTTON + 2;
        panel.child(
            new AE2Button(() -> StatCollector.translateToLocal("gtnl.projector.gui.build"), ProjectorHud::build)
                .available(ProjectorHud::canBuild)
                .pos(left, y)
                .size(half, BUTTON));
        panel.child(
            new AE2Button(ProjectorHud::styleLabel, ProjectorHud::cycleStyle).available(ProjectorHud::editable)
                .pos(left + half + 2, y)
                .size(inner - half - 2, BUTTON));
        y += BUTTON;
        if (!interactive) {
            y += 2;
            panel.child(
                IKey.dynamic(ProjectorHud::hint)
                    .asWidget()
                    .color(muted)
                    .alignment(Alignment.Center)
                    .pos(left, y)
                    .size(inner, LINE - 2));
            y += LINE - 2;
        }
        panel.size(BODY + WIDTH, Math.max(y + PAD, TOOLBAR_HEIGHT));
        place(panel);
        return panel;
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

    private static TextWidget<?> header(String key, int color, int x, int y, int width) {
        return IKey.lang(key)
            .asWidget()
            .color(color)
            .pos(x, y)
            .size(width, LINE);
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
        return stack == null ? "" : stack.getDisplayName();
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

    private static String hint() {
        int code = HOLD.getKeyCode();
        return code == Keyboard.KEY_NONE ? StatCollector.translateToLocal(LANG + "hint_unbound")
            : StatCollector.translateToLocalFormatted(LANG + "hint", GameSettings.getKeyDisplayString(code));
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
        }

        @Override
        public boolean onMouseRelease(int mouseButton) {
            if (!dragging) return false;
            dragging = false;
            ConfigRegistry.save(ClientConfig.class);
            floating = null;
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

        @Override
        public void onUpdate() {
            super.onUpdate();
            if (!shown()) {
                close();
                return;
            }
            if (held()) return;
            for (NumberEntryWidget entry : entries) if (entry.isEditing()) return;
            close();
        }
    }
}
