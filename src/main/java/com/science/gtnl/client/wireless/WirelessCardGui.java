package com.science.gtnl.client.wireless;

import static codechicken.nei.NEIClientUtils.cropText;
import static com.science.gtnl.client.wireless.WirelessCardClientView.locationTarget;
import static com.science.gtnl.common.wireless.WirelessCardActions.CYCLE_VISUALISATION;
import static com.science.gtnl.common.wireless.WirelessCardActions.HIGHLIGHT;
import static com.science.gtnl.common.wireless.WirelessCardActions.OPEN_DETAILS;
import static com.science.gtnl.common.wireless.WirelessCardActions.OPEN_DEVICE;
import static com.science.gtnl.common.wireless.WirelessCardActions.OPEN_RENAME;
import static com.science.gtnl.common.wireless.WirelessCardActions.REFRESH;
import static com.science.gtnl.common.wireless.WirelessCardActions.SET_VISUALISATION;
import static com.science.gtnl.common.wireless.WirelessCardActions.TELEPORT;
import static com.science.gtnl.common.wireless.WirelessCardActions.TOGGLE_AUTO;
import static com.science.gtnl.common.wireless.WirelessCardActions.TOGGLE_GROUPING;
import static com.science.gtnl.common.wireless.WirelessCardActions.TOGGLE_PAUSE;
import static com.science.gtnl.common.wireless.WirelessCardActions.isRowAction;
import static com.science.gtnl.common.wireless.WirelessCardActions.rowIndex;
import static com.science.gtnl.common.wireless.WirelessCardActions.type;
import static net.minecraft.client.gui.Gui.drawRect;

import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.core.mixins.early.minecraft.GuiScreenAccessor;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Platform;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.scroll.ScrollArea;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.menu.MenuPanel;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnllib.gui.theme.AE2Theme;
import com.gtnllib.gui.theme.AE2Theme.ButtonText;
import com.gtnllib.gui.theme.AE2Theme.Colors;
import com.gtnllib.gui.theme.AE2Theme.Variant;
import com.gtnllib.gui.widget.AE2ButtonDrawable;
import com.gtnllib.gui.widget.CycleDropdown;
import com.gtnllib.gui.widget.FadingNotice;
import com.gtnllib.gui.widget.HandleScrollData;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.client.wireless.WirelessCardClientView.DeviceView;
import com.science.gtnl.common.gui.ScreenThemes;
import com.science.gtnl.common.wireless.WirelessCardContainer;
import com.science.gtnl.common.wireless.WirelessCardLayout;
import com.science.gtnl.common.wireless.WirelessCardVisualisation;
import com.science.gtnl.compat.lwjgl3ify.MouseWheelCompat;

import appeng.core.localization.ColorUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.GTValues;
import gtneioreplugin.plugin.block.ModBlocks;
import gtneioreplugin.util.DimensionHelper;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

@SideOnly(Side.CLIENT)
public class WirelessCardGui extends ModularPanel {

    public static final int ROW_HEIGHT = 24, ROW_TOP = WirelessCardLayout.HEADER_HEIGHT;
    public static final String KEY_PREFIX = "gtnl.wireless.gui.", NAME = "wireless_card",
        DIMENSION_MENU = NAME + "_dimension", VISUALISATION_MENU = NAME + "_visualisation";
    private static final int[] CHIP_STATES = { 0, 3, 4, 5, 1, 2 };
    private static final int CHIP_GAP = 3, CHIP_PADDING = 4, DIMENSION_WIDTH = 100, MENU_HEIGHT = 120,
        NOTICE_MILLIS = 3500, ALL_DIMENSIONS = Integer.MIN_VALUE, OPTION_HEIGHT = 14, OPTION_PADDING = 4;
    private static final int SUCCESS_COLOR = 0x55FF55, FAILURE_COLOR = 0xFF5555, PLAIN_HIGHLIGHT = 0x60FFFFFF,
        PLAIN_HIGHLIGHT_TOP = 0x80FFFFFF, PLAIN_HIGHLIGHT_SHADOW = 0x40000000;
    private static final int TREE_ORIGIN = 8, ARROW_DOWN = 49, ARROW_RIGHT = 50;
    private static final int SCROLL_GAP = 7, SCROLL_OVERHANG = 2;
    private static final int EDGE = 10, CONTROL_GAP = 4, CONTROL_HEIGHT = 14, FOOTER_BOTTOM = 7, FOOTER_TEXT_Y = 7,
        HEADER_TOP = 6, HEADER_GAP = 3, LIST_GAP = 4, TITLE_HEIGHT = 10, CARD_HEIGHT = 34, CARD_PAD = 4,
        CARD_TITLE_Y = 4, CARD_BAR_Y = 16, CARD_SUB_Y = 24, BAR_HEIGHT = 4, DIVIDER_WIDTH = 8, DIVIDER_INSET = 3,
        PLAIN_WARNING = 0xFFAA00;
    private static final double WARNING_RATIO = 0.75;
    private static final int BAR_TRACK_ALPHA = 0x4D, DIVIDER_ALPHA = 0x80;
    private static final Set<String> CLOSING_MESSAGES = Set.of("highlight_started", "teleport_done");
    private static final Set<String> FAILURES = Set.of(
        "display.limit",
        "denied",
        "stale",
        "teleport_unavailable",
        "teleport_no_space",
        "teleport_failed",
        "highlight_dimension",
        "remote.no_gui",
        "remote.unsupported",
        "remote.unloaded",
        "remote.stale",
        "remote.denied",
        "remote.card",
        "remote.failed");
    public final Minecraft mc = Minecraft.getMinecraft();
    public final FontRenderer fontRendererObj = mc.fontRenderer;
    public final WirelessCardContainer container;
    public final String themeId = ScreenThemes.wirelessCard();
    public final @Nullable Variant variant = AE2Theme.variant(themeId);
    public final boolean ae2 = variant != null;
    public final Palette palette = variant != null ? Palette.ae2(variant) : Palette.standard();
    // Rows on AE2's light-blue selection keep the light palette's dark text, also on the dark skin.
    private final boolean blueHighlight = variant != null && variant.highlight == AE2Theme.HIGHLIGHT;
    public final Palette highlightPalette = blueHighlight ? Palette.ae2(Variant.LIGHT) : palette;
    public final int highlight = variant == null ? PLAIN_HIGHLIGHT : variant.highlight,
        highlightTop = variant == null ? PLAIN_HIGHLIGHT_TOP : variant.highlightTop,
        highlightShadow = variant == null ? PLAIN_HIGHLIGHT_SHADOW : variant.highlightShadow,
        highlightTree = blueHighlight ? variant.highlightShadow : highlightPalette.tree;
    private final int scrollWidth = AE2Theme.scrollbarWidth(variant), scrollSpace = scrollWidth + SCROLL_GAP;
    public final List<WirelessCardButton> buttons = new ObjectArrayList<>();
    public final TextFieldWidget search;
    private final List<ButtonWidget<?>> chips = new ObjectArrayList<>();
    private final CycleDropdown<Integer> dimension, visualisation;
    private final FadingNotice notice;
    private final IPanelHandler renamePanel;
    private final IPanelHandler devicePanel;
    private DeviceView devicesView, reopenDevices;
    private WirelessCardRenameDialog.Target renameTarget;
    private final ListScrollbar scrollbar = new ListScrollbar();
    private WirelessCardLayout layout = WirelessCardLayout.forScreen(0, 0);
    private String searchText = "", layoutKey = "";
    public int xSize = layout.width(), ySize = layout.height(), visibleRows = layout.rows();

    public WirelessCardGui(WirelessCardContainer container) {
        super(NAME);
        this.container = container;
        var saved = clientView().recall();
        if (saved != null) {
            clientView().restore(saved);
            searchText = saved.search();
        }
        addRowButtons(OPEN_DETAILS, TOGGLE_PAUSE, HIGHLIGHT, TELEPORT, OPEN_DEVICE, OPEN_RENAME);
        child(scrollbar);
        search = textField(64).hintText(tr("search_placeholder"));
        search.autoUpdateOnChange(true)
            .value(new StringValue.Dynamic(() -> searchText, text -> {
                if (searchText.equals(text)) return;
                searchText = text;
                filter(clientView().dimension(), clientView().state());
            }));
        dimension = choice(DIMENSION_MENU, this::dimensionLabel, this::dimensionOption)
            .maxVerticalMenuSize(MENU_HEIGHT);
        dimension.value(
            new IntValue.Dynamic(
                () -> clientView().dimension() == null ? ALL_DIMENSIONS : clientView().dimension(),
                value -> filter(value == ALL_DIMENSIONS ? null : value, clientView().state())));
        visualisation = choice(VISUALISATION_MENU, this::visualisationLabel, this::visualisationOption).directionUp()
            .maxVerticalMenuSize(WirelessCardVisualisation.MODE_COUNT * OPTION_HEIGHT);
        visualisation.value(new IntValue.Dynamic(() -> view().getInteger("visualMode"), this::setVisualisation));
        List<Integer> modes = new ObjectArrayList<>();
        for (int mode = 0; mode < WirelessCardVisualisation.MODE_COUNT; mode++) modes.add(mode);
        visualisation.choices(modes);
        notice = new FadingNotice().duration(NOTICE_MILLIS);
        Flow stats = Flow.row()
            .widthRel(1f)
            .height(CARD_HEIGHT);
        for (Stat stat : Stat.values()) {
            if (stat != Stat.CONTROLLER) stats.child(
                new CardDivider().width(DIVIDER_WIDTH)
                    .heightRel(1f));
            stats.child(
                new StatCard(stat).expanded()
                    .heightRel(1f));
        }
        Flow filters = Flow.row()
            .widthRel(1f)
            .height(CONTROL_HEIGHT)
            .childPadding(CHIP_GAP)
            .collapseDisabledChild();
        for (int index = 0; index < CHIP_STATES.length; index++) filters.child(chip(index).height(CONTROL_HEIGHT));
        child(
            Flow.column()
                .left(EDGE)
                .right(EDGE)
                .top(HEADER_TOP)
                .height(ROW_TOP - LIST_GAP - HEADER_TOP)
                .childPadding(HEADER_GAP)
                .child(
                    new Title().widthRel(1f)
                        .height(TITLE_HEIGHT))
                .child(stats)
                .child(filters)
                .child(
                    Flow.row()
                        .widthRel(1f)
                        .height(CONTROL_HEIGHT)
                        .childPadding(CONTROL_GAP)
                        .collapseDisabledChild()
                        .child(
                            search.expanded()
                                .height(AE2Theme.TEXT_FIELD_HEIGHT))
                        .child(dimension.size(DIMENSION_WIDTH, CONTROL_HEIGHT))));
        child(
            Flow.row()
                .left(EDGE)
                .right(EDGE)
                .bottom(FOOTER_BOTTOM)
                .height(AE2Theme.BUTTON_HEIGHT)
                .childPadding(CONTROL_GAP)
                .collapseDisabledChild()
                .child(
                    new FooterInfo().expanded()
                        .height(AE2Theme.BUTTON_HEIGHT))
                .child(button(TOGGLE_GROUPING).size(mergeWidth(), AE2Theme.SWITCH_HEIGHT))
                .child(button(TOGGLE_AUTO).size(autoWidth(), AE2Theme.SWITCH_HEIGHT))
                .child(visualisation.size(layout.visualisationWidth(), AE2Theme.BUTTON_HEIGHT))
                .child(button(REFRESH).size(AE2Theme.BUTTON_HEIGHT, AE2Theme.BUTTON_HEIGHT)));
        child(notice);
        renamePanel = IPanelHandler.simple(this, (parent, player) -> new WirelessCardRenameDialog(this), true);
        devicePanel = IPanelHandler
            .simple(this, (parent, player) -> new WirelessCardDeviceDialog(this, devicesView), true);
    }

    public WirelessCardClientView clientView() {
        return container.clientView();
    }

    public NBTTagCompound view() {
        return container.display;
    }

    public boolean layoutReady() {
        return container.display.hasKey("session") && view().getInteger("visibleRows") == visibleRows;
    }

    boolean editingText() {
        return search.isFocused();
    }

    private void filter(Integer dimension, int state) {
        clientView().filter(searchText, dimension, state);
    }

    private void setVisualisation(int mode) {
        if (layoutReady()) container.sync.request(SET_VISUALISATION, mode, "");
    }

    public void receive(ChatComponentTranslation message) {
        String key = message.getKey()
            .substring(KEY_PREFIX.length());
        if (CLOSING_MESSAGES.contains(key)) return;
        int color = FAILURES.contains(key) ? FAILURE_COLOR : SUCCESS_COLOR;
        String text = message.getUnformattedText();
        notice.show(text, color);
        ModularScreen screen = ModularScreen.getCurrent();
        if (screen != null && screen.getMainPanel() == this
            && screen.getPanelManager()
                .getTopMostPanel() instanceof WirelessCardDeviceDialog dialog)
            dialog.receive(text, color);
    }

    public void action(int id) {
        if (!layoutReady()) return;
        if (type(id) == OPEN_DETAILS) {
            NBTTagCompound row = row(rowIndex(id));
            boolean shift = GuiScreen.isShiftKeyDown(), group = row.getBoolean("group");
            if (group && !shift) {
                if (clientView().navigate(id)) updateButtons();
                return;
            }
            if (group || shift || !row.getBoolean("singleDevice")) {
                openDevices(row.getString("key"));
                return;
            }
            openDevice(null, row.getLong("targetId"));
            return;
        }
        int action = type(id);
        if (action == HIGHLIGHT || action == TELEPORT) {
            NBTTagCompound target = locationTarget(row(rowIndex(id)));
            if (action == HIGHLIGHT) highlightTarget(target);
            else sendTarget(TELEPORT, target);
            return;
        }
        if (type(id) == OPEN_RENAME) {
            var target = WirelessCardRenameDialog.Target.listed(row(rowIndex(id)));
            if (target != null) openRename(target);
            return;
        }
        if (action == OPEN_DEVICE) RemoteDeviceClient.rememberCard(clientView().save(searchText, null));
        long target = isRowAction(id) ? row(rowIndex(id)).getLong("targetId") : 0;
        container.sync.request(action, target, "");
    }

    void openRename(WirelessCardRenameDialog.Target target) {
        renameTarget = target;
        renamePanel.openPanel();
    }

    WirelessCardRenameDialog.Target renameTarget() {
        return renameTarget;
    }

    void openDevices(String key) {
        if (key != null && !key.isEmpty()) openDevices(new DeviceView(key, Set.of(), 0));
    }

    private void openDevices(DeviceView view) {
        devicesView = view;
        // Each opening reads the group's current membership.
        devicePanel.deleteCachedPanel();
        devicePanel.openPanel();
    }

    void openDevice(DeviceView devices, long target) {
        if (!layoutReady()) return;
        RemoteDeviceClient.rememberCard(clientView().save(searchText, devices));
        container.sync.request(OPEN_DEVICE, target, "");
    }

    void sendTarget(int action, NBTTagCompound target) {
        if (layoutReady()) container.sync.request(action, target.getLong("targetId"), "");
    }

    void highlightTarget(NBTTagCompound target) {
        copy(target);
        if (target.getInteger("dimension") == mc.thePlayer.dimension) sendTarget(HIGHLIGHT, target);
    }

    private NBTTagCompound row(int index) {
        return view().getTagList("rows", 10)
            .getCompoundTagAt(index);
    }

    private void copy(NBTTagCompound row) {
        GuiScreen.setClipboardString(WirelessCardClientView.positionWithSide(row));
        mc.thePlayer.addChatMessage(new ChatComponentTranslation(KEY_PREFIX + "copied"));
    }

    public final void addButton(int id) {
        child(button(id));
    }

    private Widget<?> button(int id) {
        var button = new WirelessCardButton(id, this);
        buttons.add(button);
        return button.widget;
    }

    public final void addRowButtons(int... actions) {
        for (int row = 0; row < WirelessCardLayout.MAX_ROWS; row++) for (int action : actions) addButton(action + row);
    }

    private ButtonWidget<?> chip(int index) {
        ButtonWidget<?> chip = new ButtonWidget<>();
        IDrawable background = AE2Theme
            .button(chip, () -> chipAvailable(index), () -> clientView().state() == CHIP_STATES[index]);
        chip.background(background)
            .hoverBackground(background)
            .overlay((context, x, y, width, height, theme) -> drawChip(index, chip, context, x, y, width, height))
            .onMousePressed(button -> {
                if (button != 0 || !chipAvailable(index) || !layoutReady()) return false;
                filter(clientView().dimension(), CHIP_STATES[index]);
                return true;
            })
            .tooltipBuilder(tooltip -> {
                tooltip.addLine(index == 0 ? tr("chip_all_tip") : tr("chip_tip", chipLabel(index)));
                tooltip.addLine(EnumChatFormatting.GRAY + tr("chip_count", chipCount(index)));
            })
            .tooltipAutoUpdate(true);
        chips.add(chip);
        return chip;
    }

    private static String chipLabel(int index) {
        return tr("filter." + CHIP_STATES[index]);
    }

    private int chipCount(int index) {
        int[] counts = view().getIntArray("stateCounts");
        return CHIP_STATES[index] < counts.length ? counts[CHIP_STATES[index]] : 0;
    }

    private boolean chipAvailable(int index) {
        return clientView().state() == CHIP_STATES[index] || chipCount(index) > 0;
    }

    private int chipWidth(int index) {
        return CHIP_PADDING * 2 + fontRendererObj.getStringWidth(chipLabel(index))
            + 4
            + fontRendererObj.getStringWidth(Integer.toString(chipCount(index)));
    }

    private void drawChip(int index, Widget<?> chip, GuiContext context, int x, int y, int width, int height) {
        boolean available = chipAvailable(index);
        AE2ButtonDrawable.State state = AE2ButtonDrawable.State
            .of(chip, available, clientView().state() == CHIP_STATES[index]);
        ButtonText style = AE2Theme.buttonText(context, state, height);
        String count = Integer.toString(chipCount(index));
        int countWidth = fontRendererObj.getStringWidth(count), textY = y + style.y();
        String label = fontRendererObj
            .trimStringToWidth(chipLabel(index), Math.max(0, width - CHIP_PADDING * 2 - countWidth - 4));
        drawText(label, x + CHIP_PADDING, textY, style.color(), style.shadow());
        drawText(
            count,
            x + width - CHIP_PADDING - countWidth,
            textY,
            available ? palette.chips[index] : style.color(),
            style.shadow());
    }

    private CycleDropdown<Integer> choice(String name, Supplier<String> label, IntFunction<String> option) {
        CycleDropdown<Integer> choice = new CycleDropdown<>(name, Integer.class);
        IDrawable background = AE2Theme.button(choice, () -> true, () -> false);
        return choice.optionToWidget(
            (value, selected) -> selected ? IDrawable.EMPTY.asWidget()
                .size(0, 0) : optionWidget(option.apply(value)))
            .background(background)
            .hoverBackground(background)
            .overlay(
                (context, x, y, width, height, theme) -> drawChoice(label.get(), choice, context, x, y, width, height));
    }

    private TextWidget<?> optionWidget(String text) {
        TextWidget<?> option = IKey.str(text)
            .asWidget();
        option.widthRel(1f)
            .height(OPTION_HEIGHT)
            .paddingLeft(OPTION_PADDING);
        if (variant != null)
            option.color(() -> option.isHovering() ? variant.colors.hoverText() : variant.colors.text());
        return option;
    }

    private void drawChoice(String label, Widget<?> choice, GuiContext context, int x, int y, int width, int height) {
        ButtonText style = AE2Theme.buttonText(context, AE2ButtonDrawable.State.of(choice, true, false), height);
        int textY = y + style.y();
        drawText(
            fontRendererObj.trimStringToWidth(label, Math.max(0, width - 16)),
            x + 4,
            textY,
            style.color(),
            style.shadow());
        int arrowX = x + width - 10, arrowY = textY + 2;
        for (int row = 0; row < 3; row++)
            GuiDraw.drawRect(arrowX + row, arrowY + row, 5 - row * 2, 1, 0xFF000000 | style.color());
    }

    private String dimensionLabel() {
        Integer id = clientView().dimension();
        return id == null ? tr("dimension_all") : dimensionName(id);
    }

    public String dimensionName(int id) {
        String name = clientView().dimensionName(id);
        return name.isEmpty() ? "D" + id : name;
    }

    private String dimensionOption(int value) {
        if (value == ALL_DIMENSIONS) return tr("dimension_all");
        int count = 0;
        for (int[] entry : clientView().dimensions()) if (entry[0] == value) count = entry[1];
        return tr("dimension_option", dimensionName(value), value, count);
    }

    private List<Integer> dimensionChoices() {
        List<Integer> choices = new ObjectArrayList<>();
        choices.add(ALL_DIMENSIONS);
        for (int[] entry : clientView().dimensions()) choices.add(entry[0]);
        return choices;
    }

    private String visualisationLabel() {
        String name = visualisationName(view().getInteger("visualMode"), true);
        return layout.visualisationWidth() < 102 ? name : tr("visualisation_button", name);
    }

    private String visualisationOption(int mode) {
        String name = visualisationName(mode, false);
        return fontRendererObj.getStringWidth(name) <= layout.visualisationWidth() - 8 ? name
            : visualisationName(mode, true);
    }

    private static String visualisationName(int mode, boolean compact) {
        return StatCollector
            .translateToLocal(WirelessCardVisualisation.modeKey(mode) + (compact && mode > 0 ? ".short" : ""));
    }

    @Override
    public boolean isDraggable() {
        return false;
    }

    @Override
    public boolean onKeyPressed(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE && (dimension.isOpen() || visualisation.isOpen())) {
            dimension.closeMenu(false);
            visualisation.closeMenu(false);
            return true;
        }
        if (WirelessCardInput.visualisationKey(key) && !editingText()) {
            action(CYCLE_VISUALISATION);
            return true;
        }
        return super.onKeyPressed(character, key);
    }

    @Override
    public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
        if (super.onMouseScroll(scrollDirection, amount)) return true;
        int x = getContext().getAbsMouseX() - getArea().x;
        int y = getContext().getAbsMouseY() - getArea().y - ROW_TOP;
        if (x < 8 || x >= xSize - 8 || y < 0 || y >= visibleRows * ROW_HEIGHT) return false;
        int ticks = Math.max(1, Math.abs(MouseWheelCompat.normalizeTicks(amount)));
        scrollTo(view().getInteger("scroll") + (scrollDirection.isUp() ? -ticks : ticks));
        return true;
    }

    private void scrollTo(int offset) {
        if (!layoutReady()) return;
        clientView().scrollTo(offset);
        updateButtons();
    }

    public ModularScreen screen() {
        AE2Theme.useMenuTheme(themeId, ScienceNotLeisure.MODID, NAME, DIMENSION_MENU, VISUALISATION_MENU);
        return new ModularScreen(ScienceNotLeisure.MODID, this) {

            @Override
            public void onResize(int width, int height) {
                resizeLayout(width, height);
                super.onResize(width, height);
            }

            @Override
            public boolean onMousePressed(int button) {
                ModularPanel previous = getPanelManager().getTopMostPanel();
                ModularPanel hovered = hoveredPanel();
                if (button == 1 && !(hovered instanceof MenuPanel)) {
                    if (hovered == null) return true;
                    if (hovered.getTopHovering() instanceof TextFieldWidget) super.onMousePressed(button);
                    else if (hovered instanceof WirelessCardRenameDialog dialog) dialog.closeIfOpen();
                    else if (hovered instanceof WirelessCardDeviceDialog dialog) dialog.closeIfOpen();
                    return true;
                }
                if (hovered == WirelessCardGui.this && button > 1
                    && WirelessCardInput.visualisationKey(button - 100)
                    && !(getContext().getFocusedWidget()
                        .getElement() instanceof TextFieldWidget)) {
                    action(CYCLE_VISUALISATION);
                    return true;
                }
                boolean handled = super.onMousePressed(button);
                // The opening button clears focus after its callback returns.
                if (getPanelManager().getTopMostPanel() instanceof WirelessCardRenameDialog dialog
                    && previous != dialog) getContext().focus(dialog.name);
                return handled;
            }

            private ModularPanel hoveredPanel() {
                for (ModularPanel panel : getPanelManager().getOpenPanels()) {
                    if (panel.isBelowMouse()) return panel;
                    if (panel.disablePanelsBelow()) break;
                }
                return null;
            }

            @Override
            public boolean onMouseRelease(int button) {
                return super.onMouseRelease(button) || button == 1;
            }
        }.useTheme(themeId);
    }

    public void resizeLayout(int width, int height) {
        layout = WirelessCardLayout.forScreen(width, height);
        visibleRows = layout.rows();
        xSize = layout.width();
        ySize = layout.height();
        clientView().resize(visibleRows);
        size(xSize, ySize);
        visualisation.deleteMenu();
        layoutControls();
    }

    private void layoutControls() {
        int rowWidth = xSize - scrollSpace;
        scrollbar.pos(xSize - 10 - scrollWidth, ROW_TOP - SCROLL_OVERHANG)
            .size(scrollWidth, visibleRows * ROW_HEIGHT + SCROLL_OVERHANG);
        for (var control : buttons) {
            int id = control.id;
            var button = control.widget;
            if (isRowAction(id)) {
                int y = ROW_TOP + rowIndex(id) * ROW_HEIGHT;
                switch (type(id)) {
                    case OPEN_DETAILS -> button.pos(10, y)
                        .size(rowWidth - 20, ROW_HEIGHT);
                    case TELEPORT -> button.pos(rowWidth - 88, y + 4)
                        .size(16, 16);
                    case HIGHLIGHT -> button.pos(rowWidth - 70, y + 4)
                        .size(16, 16);
                    case OPEN_DEVICE -> button.pos(rowWidth - 52, y + 4)
                        .size(16, 16);
                    case TOGGLE_PAUSE -> button.pos(rowWidth - 28, y + 4)
                        .size(16, 16);
                    case OPEN_RENAME -> button.pos(rowWidth - 106, y + 4)
                        .size(16, 16);
                }
            }
        }
        layoutChips();
        visualisation.width(layout.visualisationWidth());
        notice.pos(10, ySize - 46)
            .size(xSize - 20, 15);
    }

    private void layoutChips() {
        int available = xSize - EDGE * 2 - CHIP_GAP * chips.size(), total = 0;
        int[] widths = new int[chips.size()];
        for (int i = 0; i < widths.length; i++) total += widths[i] = chipWidth(i);
        for (int i = 0; i < widths.length; i++) chips.get(i)
            .width(total <= available ? widths[i] : widths[i] * available / total);
    }

    private int autoWidth() {
        return AE2Theme.switchWidth(tr("auto_connect"));
    }

    private int mergeWidth() {
        return AE2Theme.switchWidth(tr("merge_types"));
    }

    private String layoutKey() {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < chips.size(); i++) key.append(':')
            .append(chipWidth(i));
        return key.toString();
    }

    @Override
    public void onClose() {
        clientView().remember(searchText);
        super.onClose();
    }

    @Override
    public void onUpdate() {
        var returning = RemoteDeviceClient.takeCardState(container.display.getLong("returnSession"));
        if (returning != null) {
            searchText = returning.search();
            clientView().restore(returning);
            clientView().resize(visibleRows);
            reopenDevices = returning.devices();
        }
        super.onUpdate();
        if (reopenDevices != null && layoutReady() && !view().getBoolean("initializing")) {
            if (clientView().deviceGroup(reopenDevices.key()) != null) openDevices(reopenDevices);
            reopenDevices = null;
        }
        String key = layoutKey();
        if (!key.equals(layoutKey)) {
            layoutKey = key;
            layoutControls();
            scheduleResize();
        }
        dimension.choices(dimensionChoices());
        updateButtons();
    }

    private void updateButtons() {
        for (var button : buttons) button.update(view(), layoutReady(), visibleRows);
    }

    public static String tr(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(KEY_PREFIX + key, args);
    }

    public TextFieldWidget textField(int max) {
        return new TextFieldWidget().setTooltipOverride(true)
            .value(new StringValue(""))
            .setMaxLength(max)
            .padding(2, 0);
    }

    void drawText(String text, int x, int y, int color, boolean shadow) {
        drawText(() -> fontRendererObj.drawString(text, x, y, color, shadow));
    }

    void drawText(Runnable text) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            Platform.setupDrawFont();
            text.run();
        } finally {
            GL11.glPopAttrib();
        }
    }

    int stateX() {
        return xSize - scrollSpace - 148;
    }

    int textColor() {
        return palette.text;
    }

    public static String rowName(NBTTagCompound row, ItemStack icon) {
        String name = row.getString("name");
        if (name.isEmpty() && !row.getBoolean("group")) {
            int mode = row.getInteger("displayMode");
            if (mode == 0) name = row.getString("configurationLabel");
            if (name.isEmpty() && mode != 1) name = row.getString("linkedLabel");
        }
        if (name.isEmpty()) name = icon == null ? "" : icon.getDisplayName();
        if (row.getBoolean("group")) {
            int[] counts = row.getIntArray("energyHatches");
            StringBuilder energy = new StringBuilder();
            for (int tier = 0; tier < Math.min(counts.length, GTValues.VN.length); tier++) {
                if (counts[tier] <= 0) continue;
                if (!energy.isEmpty()) energy.append(' ');
                energy.append(GTValues.TIER_COLORS[tier])
                    .append(GTValues.VN[tier])
                    .append('×')
                    .append(counts[tier])
                    .append(EnumChatFormatting.RESET);
            }
            if (!energy.isEmpty()) name += " " + energy;
        }
        return row.getBoolean("shared") ? tr("shared", name) : name;
    }

    public static ItemStack rowIcon(NBTTagCompound row) {
        if (row.getBoolean("dimensionGroup")) return dimensionIcon(row.getString("dimensionKey"));
        if (!row.getBoolean("group")) {
            int mode = row.getInteger("displayMode");
            if (mode == 0 && row.hasKey("configurationIcon", 10))
                return ItemStack.loadItemStackFromNBT(row.getCompoundTag("configurationIcon"));
            if (mode != 1 && row.hasKey("linkedIcon", 10))
                return ItemStack.loadItemStackFromNBT(row.getCompoundTag("linkedIcon"));
        }
        return ItemStack.loadItemStackFromNBT(row.getCompoundTag("icon"));
    }

    private static ItemStack dimensionIcon(String dimension) {
        String display = DimensionHelper.INTERNAL_TO_ABBR.get(dimension);
        Block planet = display == null ? null : ModBlocks.getBlock(display);
        return planet != null ? new ItemStack(planet) : new ItemStack(Items.compass);
    }

    public static String groupContents(NBTTagCompound row) {
        int total = row.getBoolean("category") ? row.getInteger("items") : row.getInteger("clusters");
        int groups = row.getInteger("groupItems");
        if (groups == 0) return tr("group_devices", total);
        String label = tr("group_device_groups", groups);
        return groups == total ? label : label + " / " + tr("group_devices", total - groups);
    }

    private String trim(String text, int width) {
        return fontRendererObj.trimStringToWidth(text, Math.max(0, width));
    }

    private int textSegment(String text, int x, int y, int end, int color) {
        String fitted = trim(text, end - x);
        fontRendererObj.drawString(fitted, x, y, color);
        return x + fontRendererObj.getStringWidth(fitted);
    }

    private void drawMetrics(NBTTagCompound data, int x, int y, int width, Palette ink) {
        int end = x + width;
        x = textSegment(
            tr("online_compact", data.getInteger("online"), data.getInteger("devices")),
            x,
            y,
            end,
            onlineColor(data, ink));
        x = textSegment("  " + tr("missing_compact", data.getInteger("missing")), x, y, end, missingColor(data, ink));
        textSegment("  " + tr("entrances_compact", data.getInteger("entrances")), x, y, end, ink.text);
    }

    private static int onlineColor(NBTTagCompound data, Palette ink) {
        int online = data.getInteger("online"), devices = data.getInteger("devices");
        return data.getBoolean("paused") ? ink.bad
            : !data.getBoolean("stable") ? ink.link : online == devices && devices > 0 ? ink.good : ink.text;
    }

    private static int missingColor(NBTTagCompound data, Palette ink) {
        return !data.getBoolean("stable") ? ink.link : data.getInteger("missing") > 0 ? ink.bad : ink.text;
    }

    private static int usageColor(int used, int total, int normal, Palette ink) {
        if (total <= 0 || used < 0) return normal;
        return used >= total ? ink.bad : used >= total * WARNING_RATIO ? ink.warning : normal;
    }

    private void ratioBar(int x, int y, int width, int height, int value, int total, int fill, int remainder) {
        drawRect(x, y, x + width, y + height, remainder | 0xFF000000);
        if (value < 0 || total <= 0) return;
        int filled = (int) Math.round(width * Math.max(0.0, Math.min(1.0, (double) value / total)));
        if (filled > 0) drawRect(x, y, x + filled, y + height, fill | 0xFF000000);
    }

    void drawDeviceIcon(ItemStack icon, int x, int y) {
        float brightnessX = OpenGlHelper.lastBrightnessX;
        float brightnessY = OpenGlHelper.lastBrightnessY;
        var itemRender = GuiScreenAccessor.getItemRender();
        float renderZ = itemRender.zLevel;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glColor4f(1, 1, 1, 1);
            // Isolate custom item renderers while retaining MUI's item layer and Forge glint cleanup.
            GuiDraw.drawItem(icon, x, y, 16, 16, getContext().getCurrentDrawingZ());
        } finally {
            itemRender.zLevel = renderZ;
            GL11.glPopAttrib();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightnessX, brightnessY);
        }
    }

    void drawRowTitle(NBTTagCompound row, ItemStack icon, int x, int y, int width, Palette ink) {
        String name = rowName(row, icon);
        String title = name.isEmpty() ? trim(locationTarget(row).getString("positionLabel"), width)
            : cropText(fontRendererObj, name, width);
        fontRendererObj.drawString(title, x, y, ink.name);
    }

    private void drawRow(NBTTagCompound row, int y, boolean highlighted) {
        Palette ink = highlighted ? highlightPalette : palette;
        ItemStack icon = ItemStack.loadItemStackFromNBT(row.getCompoundTag("icon"));
        ItemStack displayIcon = rowIcon(row);
        int stateY = y + (ROW_HEIGHT - fontRendererObj.FONT_HEIGHT) / 2;
        fontRendererObj
            .drawString(trim(tr("short_state." + row.getString("state")), 38), stateX(), stateY, ink.state(row));
        int depth = row.getInteger("depth");
        boolean group = row.getBoolean("group");
        WirelessCardTreeRows.drawTree(
            this,
            TREE_ORIGIN,
            y,
            depth,
            group,
            row.getInteger("treeLines"),
            row.getBoolean("treeLast"),
            highlighted);
        int iconX = TREE_ORIGIN + WirelessCardTreeRows.iconX(depth, group),
            textX = iconX + WirelessCardTreeRows.TEXT_GAP, width = stateX() - textX - 4;
        if (group) drawArrow(
            row.getBoolean("expanded"),
            TREE_ORIGIN + WirelessCardTreeRows.arrowX(depth),
            y + WirelessCardTreeRows.ARROW_Y);
        if (displayIcon != null) drawDeviceIcon(displayIcon, iconX, y + 4);
        int missingColor = row.getBoolean("stable") && row.getInteger("missing") > 0 ? ink.bad : ink.track;
        ratioBar(
            stateX(),
            y + 19,
            36,
            2,
            row.getBoolean("stable") ? row.getInteger("online") : -1,
            row.getInteger("devices"),
            ink.good,
            missingColor);
        if (!group) {
            drawRowTitle(row, icon, textX, y + 2, width, ink);
            drawMetrics(row, textX, y + 13, width, ink);
            return;
        }
        int count = row.getBoolean("category") ? row.getInteger("items") : row.getInteger("clusters");
        fontRendererObj.drawString(cropText(fontRendererObj, rowName(row, icon), width), textX, y + 2, ink.name);
        fontRendererObj.drawString(
            trim(tr(row.getBoolean("expanded") ? "group_collapse" : "group_expand", groupContents(row)), width),
            textX,
            y + 13,
            ink.suffix);
        String total = "×" + count;
        fontRendererObj
            .drawString(total, xSize - scrollSpace - 14 - fontRendererObj.getStringWidth(total), stateY, ink.name);
    }

    void drawArrow(boolean expanded, int x, int y) {
        GL11.glColor4f(1, 1, 1, 1);
        AE2Theme.drawLegacyState(variant, expanded ? ARROW_DOWN : ARROW_RIGHT, x, y, WirelessCardTreeRows.ARROW_SIZE);
        Platform.setupDrawFont();
    }

    private void drawEmptyMessage(NBTTagCompound data, int color) {
        List<String> lines = fontRendererObj.listFormattedStringToWidth(
            tr(data.getBoolean("initializing") ? "initializing" : data.getBoolean("filtered") ? "no_matches" : "empty"),
            Math.min(300, xSize - 48));
        int lineHeight = fontRendererObj.FONT_HEIGHT + 3;
        int y = ROW_TOP + (visibleRows * ROW_HEIGHT - lines.size() * lineHeight) / 2;
        for (String line : lines) {
            fontRendererObj.drawString(line, (xSize - fontRendererObj.getStringWidth(line)) / 2, y, color);
            y += lineHeight;
        }
    }

    private void drawBar(int x, int y, int width, int total, int[] values, int[] colors) {
        drawRect(x, y, x + width, y + BAR_HEIGHT, tint(BAR_TRACK_ALPHA));
        if (total <= 0) return;
        int filled = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] <= 0) continue;
            int start = (int) Math.round((double) width * filled / total);
            filled = Math.min(total, filled + values[i]);
            int end = (int) Math.round((double) width * filled / total);
            if (end > start) drawRect(x + start, y, x + end, y + BAR_HEIGHT, colors[i] | 0xFF000000);
        }
    }

    private int hoveredRow(ModularGuiContext context, int count) {
        if (!isBelowMouse()) return -1;
        int x = context.getAbsMouseX() - getArea().x, y = context.getAbsMouseY() - getArea().y - ROW_TOP;
        if (x < 10 || x >= xSize - scrollSpace - 10 || y < 0) return -1;
        int row = y / ROW_HEIGHT;
        return row < count ? row : -1;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            NBTTagCompound data = view();
            NBTTagList rows = data.getTagList("rows", 10);
            int count = Math.min(visibleRows, rows.tagCount()), hovered = hoveredRow(context, count);
            if (variant != null) {
                GL11.glColor4f(1, 1, 1, 1);
                variant.inset.draw(
                    8,
                    ROW_TOP - AE2Theme.INSET_TOP,
                    xSize - 16 - scrollSpace,
                    visibleRows * ROW_HEIGHT + AE2Theme.INSET_TOP + AE2Theme.INSET_EDGE);
            }
            int left = ae2 ? 9 : 8, right = xSize - scrollSpace - left;
            for (int index = 0; index < count; index++) {
                NBTTagCompound entry = rows.getCompoundTagAt(index);
                int y = ROW_TOP + index * ROW_HEIGHT, depth = entry.getInteger("depth");
                WirelessCardTreeRows.drawBackground(
                    this,
                    TREE_ORIGIN,
                    left,
                    right,
                    y,
                    depth,
                    entry.getBoolean("group"),
                    highlighted(rows, index, hovered),
                    !ae2 || index < visibleRows - 1);
            }
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            Platform.setupDrawFont();
            int textColor = textColor();
            for (int index = 0; index < count; index++)
                drawRow(rows.getCompoundTagAt(index), ROW_TOP + index * ROW_HEIGHT, highlighted(rows, index, hovered));
            if (rows.tagCount() == 0) drawEmptyMessage(data, textColor);
        } finally {
            GL11.glPopAttrib();
        }
    }

    int tint(int alpha) {
        return alpha << 24 | palette.line & 0xFFFFFF;
    }

    private static boolean highlighted(NBTTagList rows, int index, int hovered) {
        return index == hovered || rows.getCompoundTagAt(index)
            .getBoolean("expanded");
    }

    private enum Stat {

        CONTROLLER("controller"),
        ENTRANCES("entrances"),
        DEVICES("devices");

        private final String key;

        Stat(String key) {
            this.key = key;
        }
    }

    private record Reading(String value, int valueColor, int total, int[] values, int[] colors, String sub,
        int subColor, List<String> details) {}

    private Reading read(Stat stat, NBTTagCompound data) {
        return switch (stat) {
            case CONTROLLER -> readController(data);
            case ENTRANCES -> readEntrances(data);
            case DEVICES -> readDevices(data);
        };
    }

    private Reading readController(NBTTagCompound data) {
        int[] none = new int[0];
        if (!data.getBoolean("bound"))
            return new Reading("", palette.text, 0, none, none, tr("bind_hint"), palette.link, List.of(tr("unbound")));
        String source = data.getString("sourceLabel");
        List<String> details = new ObjectArrayList<>();
        details.add(tr("source", source));
        if (!data.getBoolean("sourceLoaded")) {
            boolean split = data.getBoolean("sourceSplit");
            String state = tr(split ? "source_split" : "source_waiting");
            details.add(state);
            return new Reading("", palette.text, 0, none, none, state, split ? palette.bad : palette.link, details);
        }
        if (!data.getBoolean("channels")) {
            details.add(tr("unlimited"));
            return new Reading("∞", palette.text, 0, none, none, tr("unlimited"), palette.suffix, details);
        }
        int used = data.getInteger("used"), capacity = data.getInteger("capacity");
        boolean known = data.getBoolean("stable") && used >= 0 && capacity > 0;
        int color = known ? usageColor(used, capacity, palette.good, palette) : palette.link;
        details.add(tr("budget_compact", known ? used : "?", capacity));
        return new Reading(
            (known ? used : "?") + " / " + capacity,
            color,
            known ? capacity : 0,
            new int[] { used },
            new int[] { color },
            source,
            palette.suffix,
            details);
    }

    private Reading readEntrances(NBTTagCompound data) {
        int used = data.getInteger("entrances"), limit = data.getInteger("entranceLimit");
        if (limit <= 0) return new Reading(
            Integer.toString(used),
            palette.text,
            0,
            new int[0],
            new int[0],
            "",
            palette.suffix,
            List.of(tr("entrances", used)));
        String free = tr("module.entrances_free", Math.max(0, limit - used));
        return new Reading(
            used + " / " + limit,
            palette.text,
            limit,
            new int[] { used },
            new int[] { usageColor(used, limit, palette.link, palette) },
            free,
            palette.suffix,
            List.of(tr("entrances", used + " / " + limit), free));
    }

    private Reading readDevices(NBTTagCompound data) {
        int online = data.getInteger("online"), devices = data.getInteger("devices");
        int missing = data.getInteger("missing");
        int offline = Math.max(0, devices - online - missing);
        String missingLine = tr("missing_metric", missing);
        return new Reading(
            online + " / " + devices,
            onlineColor(data, palette),
            devices,
            new int[] { online, offline, missing },
            new int[] { palette.good, palette.suffix, palette.bad },
            missingLine,
            missingColor(data, palette),
            List.of(tr("online_metric", online, devices), tr("module.offline", offline), missingLine));
    }

    private class StatCard extends Widget<StatCard> {

        private final Stat stat;

        private StatCard(Stat stat) {
            this.stat = stat;
            tooltipAutoUpdate(true)
                .tooltipBuilder(tooltip -> { for (String line : read(stat, view()).details()) tooltip.addLine(line); });
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            Reading reading = read(stat, view());
            int width = getArea().width, inner = width - CARD_PAD * 2;
            int valueWidth = fontRendererObj.getStringWidth(reading.value());
            drawText(
                trim(tr("module." + stat.key), inner - valueWidth - CARD_PAD),
                CARD_PAD,
                CARD_TITLE_Y,
                palette.text,
                false);
            drawText(reading.value(), width - CARD_PAD - valueWidth, CARD_TITLE_Y, reading.valueColor(), false);
            drawBar(CARD_PAD, CARD_BAR_Y, inner, reading.total(), reading.values(), reading.colors());
            drawText(trim(reading.sub(), inner), CARD_PAD, CARD_SUB_Y, reading.subColor(), false);
        }
    }

    private class CardDivider extends Widget<CardDivider> {

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            int x = getArea().width / 2;
            drawRect(x, DIVIDER_INSET, x + 1, getArea().height - DIVIDER_INSET, tint(DIVIDER_ALPHA));
        }
    }

    private class Title extends Widget<Title> {

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            drawText(trim(tr("title"), getArea().width), 0, 0, palette.name, false);
        }
    }

    private class FooterInfo extends Widget<FooterInfo> {

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            NBTTagCompound data = view();
            String info = tr(data.getBoolean("filtered") ? "matched" : "loaded", data.getInteger("total"));
            drawText(trim(info, getArea().width), 0, FOOTER_TEXT_Y, textColor(), false);
        }
    }

    private class ListScrollbar extends Widget<ListScrollbar> implements Interactable {

        private final ScrollArea area = new ScrollArea();
        private final HandleScrollData scroll = new HandleScrollData(variant);
        private int dragMouseY, dragOffset;

        private ListScrollbar() {
            area.setScrollDataY(scroll);
            listenGuiAction((IGuiAction.MouseReleased) button -> {
                area.mouseReleased(0, 0);
                return false;
            });
        }

        @Override
        public ScrollArea getArea() {
            return area;
        }

        @Override
        public boolean canHover() {
            return true;
        }

        private void sync() {
            scroll.setScrollSize(view().getInteger("rowCount") * ROW_HEIGHT + SCROLL_OVERHANG);
            scroll.scrollTo(area, view().getInteger("scroll") * ROW_HEIGHT);
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            sync();
            scroll.drawTrack(area);
        }

        @Override
        public Result onMousePressed(int button) {
            if (button != 0 || !layoutReady()) return Result.STOP;
            sync();
            int x = getContext().getAbsMouseX() - area.x, y = getContext().getAbsMouseY() - area.y;
            if (!area.mouseClicked(x, y)) return Result.STOP;
            int length = scroll.getScrollBarLength(area), start = scroll.getScrollBarStart(area, length, false);
            if (y < start || y > start + length) scrollTo(rows(y - length / 2));
            // Row math anchored to the press: the thumb's integer position loses rows on long lists.
            dragMouseY = getContext().getAbsMouseY();
            dragOffset = view().getInteger("scroll");
            return Result.SUCCESS;
        }

        @Override
        public void onMouseDrag(int button, long timeSinceClick) {
            if (button != 0 || !layoutReady() || !scroll.isDragging()) return;
            sync();
            if (scroll.isScrollBarActive(area)) scrollTo(dragOffset + rows(getContext().getAbsMouseY() - dragMouseY));
        }

        private int rows(int pixels) {
            int travel = scroll.getFullVisibleSize(area) - scroll.getScrollBarLength(area);
            return travel <= 0 ? 0 : Math.round((float) pixels * view().getInteger("maxScroll") / travel);
        }

        @Override
        public boolean onMouseRelease(int button) {
            area.mouseReleased(0, 0);
            return true;
        }
    }

    public static class Palette {

        public final int text, name, hint, suffix, track, link, good, bad, warning, line, tree, nest, nestAlpha;
        public final int[] chips;

        public Palette(int text, int name, int hint, int suffix, int track, int link, int good, int bad, int warning,
            int line, int tree, int nest, int nestAlpha, int[] chips) {
            this.text = text;
            this.name = name;
            this.hint = hint;
            this.suffix = suffix;
            this.track = track;
            this.link = link;
            this.good = good;
            this.bad = bad;
            this.warning = warning;
            this.line = line;
            this.tree = tree;
            this.nest = nest;
            this.nestAlpha = nestAlpha;
            this.chips = chips;
        }

        public int state(NBTTagCompound row) {
            if (row.getBoolean("paused") || row.getBoolean("conflicted")) return bad;
            if (row.getBoolean("stable") && row.getInteger("missing") > 0) return bad;
            if (!row.getBoolean("stable") || !row.getString("state")
                .equals("active")) return link;
            // A live entrance is not proof of power or active devices.
            return row.getInteger("devices") > 0 && row.getInteger("online") == row.getInteger("devices") ? good : text;
        }

        public int deviceState(String state) {
            return state.equals("online") ? good : state.equals("missing") ? bad : suffix;
        }

        public static Palette ae2(Variant variant) {
            Colors colors = variant.colors;
            // The dark theme's line color is near black, so nested rows lighten there instead of darkening.
            boolean dark = variant != Variant.LIGHT;
            return new Palette(
                colors.text(),
                colors.text(),
                colors.muted(),
                colors.secondary(),
                colors.line(),
                colors.info(),
                colors.good(),
                colors.bad(),
                colors.warning(),
                colors.line(),
                colors.edge(),
                dark ? colors.edge() : colors.line(),
                dark ? WirelessCardTreeRows.LIGHTEN_NEST_ALPHA : WirelessCardTreeRows.DARKEN_NEST_ALPHA,
                new int[] { colors.text(), colors.good(), colors.info(), colors.bad(), colors.warning(),
                    colors.bad() });
        }

        public static Palette standard() {
            int faint = ColorUtils.craftingTreeTask.getColor();
            return new Palette(
                ColorUtils.guiTextColorGray.getColor(),
                ColorUtils.guiTextColorBlack.getColor(),
                faint,
                faint,
                faint,
                ColorUtils.craftConfirmPercent25.getColor(),
                ColorUtils.craftConfirmPercent50.getColor(),
                ColorUtils.reshuffleStatusFailed.getColor(),
                PLAIN_WARNING,
                ColorUtils.craftingDiagnosticTerminalLine.getColor(),
                faint,
                ColorUtils.craftingDiagnosticTerminalLine.getColor(),
                WirelessCardTreeRows.DARKEN_NEST_ALPHA,
                new int[] { 0xFFFFFF, 0x55FF55, 0x55FFFF, 0xFF5555, PLAIN_WARNING, 0xFF5555 });
        }
    }
}
