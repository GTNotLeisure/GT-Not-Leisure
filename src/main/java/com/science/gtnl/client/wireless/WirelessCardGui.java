package com.science.gtnl.client.wireless;

import static codechicken.nei.NEIClientUtils.cropText;
import static com.science.gtnl.common.wireless.WirelessCardActions.BACK;
import static com.science.gtnl.common.wireless.WirelessCardActions.CLOSE_DETAILS;
import static com.science.gtnl.common.wireless.WirelessCardActions.COPY_POSITION;
import static com.science.gtnl.common.wireless.WirelessCardActions.CYCLE_VISUALISATION;
import static com.science.gtnl.common.wireless.WirelessCardActions.HIGHLIGHT;
import static com.science.gtnl.common.wireless.WirelessCardActions.NEXT_PAGE;
import static com.science.gtnl.common.wireless.WirelessCardActions.OPEN_DETAILS;
import static com.science.gtnl.common.wireless.WirelessCardActions.OPEN_ENTRANCES;
import static com.science.gtnl.common.wireless.WirelessCardActions.PREVIOUS_PAGE;
import static com.science.gtnl.common.wireless.WirelessCardActions.REFRESH;
import static com.science.gtnl.common.wireless.WirelessCardActions.RENAME;
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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
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
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.menu.MenuPanel;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnllib.gui.theme.AE2Theme;
import com.gtnllib.gui.theme.AE2Theme.ButtonText;
import com.gtnllib.gui.theme.AE2Theme.Colors;
import com.gtnllib.gui.theme.AE2Theme.Variant;
import com.gtnllib.gui.widget.AE2ButtonDrawable;
import com.gtnllib.gui.widget.Breadcrumb;
import com.gtnllib.gui.widget.CycleDropdown;
import com.gtnllib.gui.widget.FadingNotice;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.gui.ScreenThemes;
import com.science.gtnl.common.wireless.WirelessCardContainer;
import com.science.gtnl.common.wireless.WirelessCardLayout;
import com.science.gtnl.common.wireless.WirelessCardVisualisation;

import appeng.core.localization.ColorUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

@SideOnly(Side.CLIENT)
public class WirelessCardGui extends ModularPanel {

    public static final int ROW_HEIGHT = 24, CONTENT_X = 34, ROW_TOP = 82;
    public static final String KEY_PREFIX = "gtnl.wireless.gui.", NAME = "wireless_card",
        DIMENSION_MENU = NAME + "_dimension", VISUALISATION_MENU = NAME + "_visualisation";
    private static final int[] CHIP_STATES = { 0, 3, 4, 5, 1, 2 };
    private static final int CHIP_GAP = 3, CHIP_PADDING = 4, DIMENSION_WIDTH = 100, MENU_HEIGHT = 120,
        NOTICE_MILLIS = 3500, ALL_DIMENSIONS = Integer.MIN_VALUE, OPTION_HEIGHT = 14, OPTION_PADDING = 4;
    private static final int SUCCESS_COLOR = 0x55FF55, FAILURE_COLOR = 0xFF5555, PLAIN_HIGHLIGHT = 0x60FFFFFF,
        PLAIN_HIGHLIGHT_TOP = 0x80FFFFFF, PLAIN_HIGHLIGHT_SHADOW = 0x40000000, AE2_HIGHLIGHT_TOP = 0xFFDAFFFF;
    private static final int TREE_X = 15, INDENT = 12, ARROW_X = 8, CHILD_ARROW_X = 20, ICON_X = 24, CHILD_ICON_X = 28,
        ICON_SPACE = 18, ARROW_SIZE = 16, ARROW_INSET = 3, ARROW_DOWN = 49, ARROW_RIGHT = 50, DEVICE_TEXT_END = 92;
    private static final Set<String> FAILURES = Set
        .of("denied", "stale", "teleport_unavailable", "teleport_no_space", "teleport_failed", "highlight_dimension");
    public final Minecraft mc = Minecraft.getMinecraft();
    public final FontRenderer fontRendererObj = mc.fontRenderer;
    public final WirelessCardContainer container;
    public final String themeId = ScreenThemes.wirelessCard();
    public final @Nullable Variant variant = AE2Theme.variant(themeId);
    public final boolean ae2 = variant != null;
    public final Palette palette = variant != null ? Palette.ae2(variant) : Palette.standard();
    public final Palette highlightPalette = variant != null ? Palette.ae2(Variant.LIGHT) : palette;
    public final int highlight = variant != null ? AE2Theme.HIGHLIGHT : PLAIN_HIGHLIGHT,
        highlightTop = variant != null ? AE2_HIGHLIGHT_TOP : PLAIN_HIGHLIGHT_TOP,
        highlightShadow = variant != null ? AE2Theme.HIGHLIGHT_SHADOW : PLAIN_HIGHLIGHT_SHADOW;
    public final List<WirelessCardButton> buttons = new ObjectArrayList<>();
    public final TextFieldWidget search;
    private final List<ButtonWidget<?>> chips = new ObjectArrayList<>();
    private final CycleDropdown<Integer> dimension, visualisation;
    private final Breadcrumb breadcrumb;
    private final FadingNotice notice;
    private final IPanelHandler renamePanel;
    private NBTTagCompound overview = new NBTTagCompound();
    private WirelessCardLayout layout = WirelessCardLayout.forScreen(0, 0);
    private boolean closingDetail;
    private String searchText = "", layoutKey = "";
    public int xSize = 320, ySize = 232, visibleRows = WirelessCardLayout.MIN_ROWS;

    public WirelessCardGui(WirelessCardContainer container) {
        super(NAME);
        this.container = container;
        addRowButtons(OPEN_DETAILS, TOGGLE_PAUSE, COPY_POSITION, HIGHLIGHT, TELEPORT, OPEN_ENTRANCES);
        for (int id : new int[] { PREVIOUS_PAGE, NEXT_PAGE, REFRESH, BACK, RENAME, TOGGLE_AUTO, TOGGLE_GROUPING })
            addButton(id);
        search = textField(64).hintText(tr("search_placeholder"));
        search.autoUpdateOnChange(true)
            .value(new StringValue.Dynamic(() -> searchText, text -> {
                if (searchText.equals(text)) return;
                searchText = text;
                filter(clientView().dimension(), clientView().state());
            }));
        child(search);
        for (int index = 0; index < CHIP_STATES.length; index++) child(chip(index));
        breadcrumb = new Breadcrumb().segments(this::crumbs)
            .onSelect(this::jump)
            .colors(palette.text, palette.link, palette.hint);
        dimension = choice(DIMENSION_MENU, this::dimensionLabel, this::dimensionOption)
            .maxVerticalMenuSize(MENU_HEIGHT);
        dimension.value(
            new IntValue.Dynamic(
                () -> clientView().dimension() == null ? ALL_DIMENSIONS : clientView().dimension(),
                value -> filter(value == ALL_DIMENSIONS ? null : value, clientView().state())));
        visualisation = choice(VISUALISATION_MENU, this::visualisationLabel, this::visualisationOption).directionUp()
            .maxVerticalMenuSize(MENU_HEIGHT);
        visualisation.value(new IntValue.Dynamic(() -> view().getInteger("visualMode"), this::setVisualisation));
        List<Integer> modes = new ObjectArrayList<>();
        for (int mode = 0; mode < WirelessCardVisualisation.MODE_COUNT; mode++) modes.add(mode);
        visualisation.choices(modes);
        notice = new FadingNotice().duration(NOTICE_MILLIS);
        child(breadcrumb).child(dimension)
            .child(visualisation)
            .child(notice);
        renamePanel = IPanelHandler.simple(this, (parent, player) -> new WirelessCardRenameDialog(this), true);
    }

    public WirelessCardClientView clientView() {
        return container.clientView();
    }

    public NBTTagCompound view() {
        // While the server leaves the entrance list, keep showing the level it is returning to.
        return closingDetail ? overview : container.display;
    }

    public boolean layoutReady() {
        return !closingDetail && container.display.hasKey("session") && view().getInteger("pageSize") == visibleRows;
    }

    boolean editingText() {
        return search.isFocused();
    }

    private int depth() {
        return view().getBoolean("detail") ? 1 : 0;
    }

    private List<String> crumbs() {
        List<String> labels = new ObjectArrayList<>();
        labels.add(tr("title"));
        if (view().getBoolean("detail"))
            labels.add(tr(view().getBoolean("devices") ? "devices_title" : "detail_title"));
        return labels;
    }

    private void jump(int target) {
        if (target >= depth() || !layoutReady()) return;
        closingDetail = true;
        container.sync.request(CLOSE_DETAILS, 0, "", 0);
    }

    private void back() {
        jump(depth() - 1);
    }

    private void filter(Integer dimension, int state) {
        clientView().filter(searchText, dimension, state);
    }

    private void setVisualisation(int mode) {
        if (layoutReady()) container.sync.request(SET_VISUALISATION, mode, "", 0);
    }

    public void receive(ChatComponentTranslation message) {
        String key = message.getKey()
            .substring(KEY_PREFIX.length());
        notice.show(message.getUnformattedText(), FAILURES.contains(key) ? FAILURE_COLOR : SUCCESS_COLOR);
    }

    public void action(int id) {
        if (!layoutReady()) return;
        if (id == BACK) {
            back();
            return;
        }
        if (id == RENAME) {
            if (view().getBoolean("renameable")) renamePanel.openPanel();
            return;
        }
        if (type(id) == COPY_POSITION) {
            copy(rowIndex(id));
            return;
        }
        if (clientView().navigate(id)) return;
        long target = isRowAction(id) ? row(rowIndex(id)).getLong("targetId") : 0;
        container.sync.request(type(id), target, "", 0);
    }

    private NBTTagCompound row(int index) {
        return view().getTagList("rows", 10)
            .getCompoundTagAt(index);
    }

    private void copy(int index) {
        if (index >= visibleRows) return;
        NBTTagCompound row = row(index);
        String side = row.getString("side");
        GuiScreen.setClipboardString(
            row.getString("position") + (row.getBoolean("group") || side.isEmpty() ? "" : " / " + side));
        var message = new ChatComponentTranslation(KEY_PREFIX + "copied");
        mc.thePlayer.addChatMessage(message);
        receive(message);
    }

    public final void addButton(int id) {
        var button = new WirelessCardButton(id, this);
        buttons.add(button);
        child(button.widget);
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
        boolean leave = key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_BACK && !editingText();
        if (leave && depth() > 0) {
            if (editingText()) getContext().removeFocus();
            back();
            return true;
        }
        if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) && view().getBoolean("detail")) {
            action(RENAME);
            return true;
        }
        return super.onKeyPressed(character, key);
    }

    @Override
    public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
        if (super.onMouseScroll(scrollDirection, amount)) return true;
        int y = getContext().getAbsMouseY() - getArea().y - ROW_TOP;
        if (y < 0 || y >= visibleRows * ROW_HEIGHT) return false;
        action(scrollDirection.isUp() ? PREVIOUS_PAGE : NEXT_PAGE);
        return true;
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
                    else if (hovered == WirelessCardGui.this) back();
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
        NBTTagCompound data = view();
        boolean root = !data.getBoolean("detail");
        for (var control : buttons) {
            int id = control.id;
            var button = control.widget;
            if (isRowAction(id)) {
                int y = ROW_TOP + rowIndex(id) * ROW_HEIGHT;
                switch (type(id)) {
                    case OPEN_DETAILS -> button.pos(10, y)
                        .size(xSize - 20, ROW_HEIGHT);
                    case TELEPORT -> button.pos(xSize - 88, y + 4)
                        .size(16, 16);
                    case HIGHLIGHT -> button.pos(xSize - 70, y + 4)
                        .size(16, 16);
                    case COPY_POSITION -> button.pos(xSize - 52, y + 4)
                        .size(16, 16);
                    case TOGGLE_PAUSE -> button.pos(xSize - 28, y + 4)
                        .size(16, 16);
                    case OPEN_ENTRANCES -> button.pos(xSize - 106, y + 4)
                        .size(16, 16);
                }
            } else switch (id) {
                case TOGGLE_AUTO -> button.pos(xSize - 10 - autoWidth(), 12)
                    .size(autoWidth(), AE2Theme.SWITCH_HEIGHT);
                case RENAME -> button.pos(xSize - 76, 47)
                    .size(64, 14);
                case TOGGLE_GROUPING -> button.pos(xSize - 10 - mergeWidth(), 65)
                    .size(mergeWidth(), AE2Theme.SWITCH_HEIGHT);
                case PREVIOUS_PAGE -> button.pos(10, ySize - 27)
                    .size(18, 20);
                case NEXT_PAGE -> button.pos(76, ySize - 27)
                    .size(18, 20);
                case BACK -> button.pos(100, ySize - 27)
                    .size(40, 20);
                case REFRESH -> button.pos(xSize - 30, ySize - 27)
                    .size(20, 20);
            }
        }
        breadcrumb.pos(10, 9)
            .size(xSize - 140, 12);
        layoutChips();
        int hint = Math.min(fontRendererObj.getStringWidth(tr("back_hint")), (xSize - 20) / 2);
        int searchWidth = root ? xSize - 28 - DIMENSION_WIDTH - mergeWidth() : xSize - 26 - hint;
        search.pos(10, 65)
            .size(searchWidth, 12);
        dimension.pos(14 + searchWidth, 64)
            .size(DIMENSION_WIDTH, 14);
        visualisation.pos(layout.visualisationX(), ySize - 27)
            .size(layout.visualisationWidth(), 20);
        notice.pos(10, ySize - 46)
            .size(xSize - 20, 15);
    }

    private void layoutChips() {
        int available = xSize - 20 - CHIP_GAP * (chips.size() - 1), total = 0;
        int[] widths = new int[chips.size()];
        for (int i = 0; i < widths.length; i++) total += widths[i] = chipWidth(i);
        int x = 10;
        for (int i = 0; i < widths.length; i++) {
            int width = total <= available ? widths[i] : widths[i] * available / total;
            chips.get(i)
                .pos(x, 47)
                .size(width, 14);
            x += width + CHIP_GAP;
        }
    }

    private int autoWidth() {
        return Math.max(AE2Theme.switchWidth(tr("auto_on")), AE2Theme.switchWidth(tr("auto_off")));
    }

    private int mergeWidth() {
        return AE2Theme.switchWidth(tr("merge_types"));
    }

    private String layoutKey(NBTTagCompound data) {
        StringBuilder key = new StringBuilder(data.getBoolean("detail") ? "detail" : "root");
        for (int i = 0; i < chips.size(); i++) key.append(':')
            .append(chipWidth(i));
        return key.toString();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (closingDetail && !container.display.getBoolean("detail")) closingDetail = false;
        NBTTagCompound data = view();
        boolean detail = data.getBoolean("detail"), root = !detail;
        if (!detail) overview = data;
        String key = layoutKey(data);
        if (!key.equals(layoutKey)) {
            layoutKey = key;
            layoutControls();
            scheduleResize();
        }
        if (detail && editingText()) getContext().removeFocus();
        search.setEnabled(!detail);
        for (var chip : chips) chip.setEnabled(!detail);
        if (!root && dimension.isOpen()) dimension.closeMenu(false);
        dimension.setEnabled(root);
        dimension.choices(dimensionChoices());
        for (var button : buttons) button.update(data, layoutReady(), visibleRows);
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
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            Platform.setupDrawFont();
            fontRendererObj.drawString(text, x, y, color, shadow);
        } finally {
            GL11.glPopAttrib();
        }
    }

    int stateX() {
        return xSize - 148;
    }

    int contentWidth() {
        return stateX() - CONTENT_X - 4;
    }

    static String entranceUsage(NBTTagCompound data, NBTTagCompound row) {
        if (!row.getBoolean("runtime")) return tr("no_runtime");
        if (!data.getBoolean("channels")) return tr("usage_disabled");
        return row.getInteger("usedChannels") < 0 ? tr("usage_waiting") : tr("usage", row.getInteger("usedChannels"));
    }

    int textColor() {
        return palette.text;
    }

    int goodColor() {
        return palette.good;
    }

    int badColor() {
        return palette.bad;
    }

    int waitingColor() {
        return palette.link;
    }

    static String budgetText(NBTTagCompound data) {
        return !data.getBoolean("bound") ? tr("bind_hint")
            : !data.getBoolean("sourceLoaded") ? tr(data.getBoolean("sourceSplit") ? "source_split" : "source_waiting")
                : !data.getBoolean("channels") ? tr("unlimited")
                    : tr(
                        "budget_compact",
                        data.getInteger("used") < 0 ? "?" : data.getInteger("used"),
                        data.getInteger("capacity"));
    }

    public static String rowName(NBTTagCompound row, ItemStack icon) {
        String name = row.getString("name");
        if (name.isEmpty()) name = icon == null ? "" : icon.getDisplayName();
        return row.getBoolean("shared") ? tr("shared", name) : name;
    }

    private String trim(String text, int width) {
        return fontRendererObj.trimStringToWidth(text, Math.max(0, width));
    }

    private int textSegment(String text, int x, int y, int end, int color) {
        String fitted = trim(text, end - x);
        fontRendererObj.drawString(fitted, x, y, color);
        return x + fontRendererObj.getStringWidth(fitted);
    }

    private void drawMetrics(NBTTagCompound data, int x, int y, int width, boolean compact, Palette ink) {
        int end = x + width;
        String entrances = "  " + tr(
            compact ? "entrances_compact" : "entrances",
            compact ? data.getInteger("entrances")
                : data.getInteger("entrances") + " / " + data.getInteger("entranceLimit"));
        int metricsEnd = compact ? end : Math.max(x, end - fontRendererObj.getStringWidth(entrances));
        int online = data.getInteger("online"), devices = data.getInteger("devices");
        int onlineColor = data.getBoolean("paused") ? ink.bad
            : !data.getBoolean("stable") ? ink.link : online == devices && devices > 0 ? ink.good : ink.text;
        x = textSegment(
            tr(compact ? "online_compact" : "online_metric", online, devices),
            x,
            y,
            metricsEnd,
            onlineColor);
        int missingColor = !data.getBoolean("stable") ? ink.link : data.getInteger("missing") > 0 ? ink.bad : ink.text;
        x = textSegment(
            "  " + tr(
                compact ? "missing_compact" : "missing_metric",
                data.getBoolean("stable") ? data.getInteger("missing") : "?"),
            x,
            y,
            metricsEnd,
            missingColor);
        textSegment(entrances, x, y, end, ink.text);
    }

    private void ratioBar(int x, int y, int width, int height, int value, int total, int fill, int remainder) {
        drawRect(x, y, x + width, y + height, remainder | 0xFF000000);
        if (value < 0 || total <= 0) return;
        int filled = (int) Math.round(width * Math.max(0.0, Math.min(1.0, (double) value / total)));
        if (filled > 0) drawRect(x, y, x + filled, y + height, fill | 0xFF000000);
    }

    private void drawDeviceIcon(ItemStack icon, int x, int y) {
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

    private void drawRowTitle(NBTTagCompound row, ItemStack icon, int x, int y, int width, Palette ink) {
        String name = rowName(row, icon), position = row.getString("positionLabel");
        if (name.isEmpty()) {
            fontRendererObj.drawString(trim(position, width), x, y, ink.name);
            return;
        }
        // Keep the device identifiable first; the row tooltip retains the full name and coordinates.
        String title = cropText(fontRendererObj, name, width);
        int positionWidth = width - fontRendererObj.getStringWidth(title) - fontRendererObj.getStringWidth(" ()");
        String suffix = !position.isEmpty() && positionWidth >= fontRendererObj.getStringWidth("...")
            ? " (" + cropText(fontRendererObj, position, positionWidth) + ")"
            : "";
        fontRendererObj.drawString(title, x, y, ink.name);
        fontRendererObj.drawString(suffix, x + fontRendererObj.getStringWidth(title), y, ink.suffix);
    }

    private void drawRow(NBTTagCompound data, NBTTagCompound row, int y, boolean highlighted) {
        Palette ink = highlighted ? highlightPalette : palette;
        ItemStack icon = ItemStack.loadItemStackFromNBT(row.getCompoundTag("icon"));
        int stateY = y + (ROW_HEIGHT - fontRendererObj.FONT_HEIGHT) / 2;
        if (!row.getBoolean("device")) fontRendererObj
            .drawString(trim(tr("short_state." + row.getString("state")), 38), stateX(), stateY, ink.state(row));
        if (row.getBoolean("device")) {
            if (icon != null) drawDeviceIcon(icon, 12, y + 4);
            int width = xSize - DEVICE_TEXT_END - CONTENT_X;
            drawRowTitle(row, icon, CONTENT_X, y + 2, width, ink);
            String state = row.getString("deviceState");
            int color = state.equals("online") ? ink.good : state.equals("missing") ? ink.bad : ink.suffix;
            fontRendererObj.drawString(trim(tr("device_state." + state), width), CONTENT_X, y + 13, color);
            return;
        }
        if (data.getBoolean("detail")) {
            if (icon != null) drawDeviceIcon(icon, 12, y + 4);
            fontRendererObj
                .drawString(trim(row.getString("positionLabel"), contentWidth()), CONTENT_X, y + 2, ink.name);
            fontRendererObj
                .drawString(trim(entranceUsage(data, row), contentWidth()), CONTENT_X, y + 13, ink.state(row));
            return;
        }
        int depth = row.getInteger("depth");
        boolean group = row.getBoolean("group");
        drawTree(row, depth, group, y, ink.tree);
        int iconX = iconX(depth, group), textX = iconX + ICON_SPACE, width = stateX() - textX - 4;
        if (group) drawArrow(row.getBoolean("expanded"), arrowX(depth), y + 4);
        if (icon != null) drawDeviceIcon(icon, iconX, y + 4);
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
            drawMetrics(row, textX, y + 13, width, true, ink);
            return;
        }
        int count = row.getBoolean("category") ? row.getInteger("items") : row.getInteger("clusters");
        fontRendererObj.drawString(cropText(fontRendererObj, rowName(row, icon), width), textX, y + 2, ink.name);
        fontRendererObj.drawString(
            trim(tr(row.getBoolean("expanded") ? "group_collapse" : "group_expand", count), width),
            textX,
            y + 13,
            ink.suffix);
        String total = "×" + count;
        fontRendererObj.drawString(total, xSize - 14 - fontRendererObj.getStringWidth(total), stateY, ink.name);
    }

    private void drawTree(NBTTagCompound row, int depth, boolean group, int y, int color) {
        if (depth == 0) return;
        int lines = row.getInteger("treeLines"), opaque = 0xFF000000 | color;
        for (int level = 0; level < depth - 1; level++)
            if ((lines & 1 << level) != 0) drawRect(lineX(level), y, lineX(level) + 1, y + ROW_HEIGHT, opaque);
        int x = lineX(depth - 1), middle = y + ROW_HEIGHT / 2;
        drawRect(x, y, x + 1, row.getBoolean("treeLast") ? middle + 1 : y + ROW_HEIGHT, opaque);
        drawRect(x + 1, middle, group ? arrowX(depth) + ARROW_INSET : iconX(depth, false) - 2, middle + 1, opaque);
    }

    private void drawArrow(boolean expanded, int x, int y) {
        GL11.glColor4f(1, 1, 1, 1);
        AE2Theme.drawLegacyState(variant, expanded ? ARROW_DOWN : ARROW_RIGHT, x, y, ARROW_SIZE);
        Platform.setupDrawFont();
    }

    private static int lineX(int level) {
        return TREE_X + level * INDENT;
    }

    private static int arrowX(int depth) {
        return depth == 0 ? ARROW_X : CHILD_ARROW_X + (depth - 1) * INDENT;
    }

    private static int iconX(int depth, boolean group) {
        if (depth == 0) return ICON_X;
        return group ? arrowX(depth) + ARROW_SIZE : CHILD_ICON_X + (depth - 1) * INDENT;
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

    private void drawHeader(NBTTagCompound data, int textColor) {
        boolean budgetKnown = data.getBoolean("sourceLoaded") && data.getBoolean("channels")
            && data.getBoolean("stable")
            && data.getInteger("used") >= 0
            && data.getInteger("capacity") > 0;
        int budgetColor = !budgetKnown ? textColor
            : data.getInteger("used") >= data.getInteger("capacity") ? badColor() : goodColor();
        String budget = trim(budgetText(data), xSize / 2);
        int budgetWidth = fontRendererObj.getStringWidth(budget);
        fontRendererObj.drawString(budget, xSize - 76 - budgetWidth, 25, budgetColor);
        ratioBar(
            xSize - 72,
            26,
            60,
            6,
            budgetKnown ? data.getInteger("used") : -1,
            data.getInteger("capacity"),
            budgetColor,
            palette.track);
        String source = data.getBoolean("bound") ? tr("source", data.getString("sourceLabel")) : tr("unbound");
        fontRendererObj.drawString(trim(source, xSize - 92 - budgetWidth), 10, 25, textColor);
        drawMetrics(data, 10, 37, xSize - 20, false, palette);
        String hint = trim(tr("back_hint"), (xSize - 20) / 2);
        int hintColor = palette.hint;
        if (data.getBoolean("detail")) {
            fontRendererObj.drawString(trim(data.getString("name"), xSize - 96), 10, 51, palette.name);
            fontRendererObj.drawString(hint, 10, 67, hintColor);
        }
    }

    private void drawFooter(NBTTagCompound data, int textColor) {
        int y = ySize - 20;
        String page = trim((data.getInteger("page") + 1) + "/" + Math.max(1, data.getInteger("pages")), 40);
        fontRendererObj.drawString(page, 52 - fontRendererObj.getStringWidth(page) / 2, y, textColor);
        int infoX = depth() > 0 ? 146 : 100;
        String info = data.getBoolean("detail")
            ? tr(data.getBoolean("devices") ? "device_count" : "entrance_count", data.getInteger("rowCount"))
            : tr(data.getBoolean("filtered") ? "matched" : "loaded", data.getInteger("total"));
        fontRendererObj.drawString(trim(info, layout.visualisationX() - infoX - 4), infoX, y, textColor);
    }

    private int hoveredRow(ModularGuiContext context, int count) {
        if (!isBelowMouse()) return -1;
        int x = context.getAbsMouseX() - getArea().x, y = context.getAbsMouseY() - getArea().y - ROW_TOP;
        if (x < 10 || x >= xSize - 10 || y < 0) return -1;
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
                    xSize - 16,
                    visibleRows * ROW_HEIGHT + AE2Theme.INSET_TOP + AE2Theme.INSET_EDGE);
            }
            for (int row = 0; row < count; row++) {
                int y = ROW_TOP + row * ROW_HEIGHT;
                if (highlighted(rows, row, hovered)) drawHighlight(y);
                if (!ae2) drawRect(8, y + ROW_HEIGHT - 1, xSize - 8, y + ROW_HEIGHT, palette.line);
                else if (row < visibleRows - 1)
                    drawRect(9, y + ROW_HEIGHT - 1, xSize - 9, y + ROW_HEIGHT, palette.line);
            }
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            Platform.setupDrawFont();
            int textColor = textColor();
            drawHeader(data, textColor);
            for (int index = 0; index < count; index++) drawRow(
                data,
                rows.getCompoundTagAt(index),
                ROW_TOP + index * ROW_HEIGHT,
                highlighted(rows, index, hovered));
            if (rows.tagCount() == 0) drawEmptyMessage(data, textColor);
            drawFooter(data, textColor);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private void drawHighlight(int y) {
        int bottom = y + ROW_HEIGHT - 1;
        drawRect(9, y, xSize - 9, bottom, highlight);
        drawRect(9, y, xSize - 9, y + 1, highlightTop);
        drawRect(9, bottom - 1, xSize - 9, bottom, highlightShadow);
    }

    private static boolean highlighted(NBTTagList rows, int index, int hovered) {
        return index == hovered || rows.getCompoundTagAt(index)
            .getBoolean("expanded");
    }

    public static class Palette {

        public final int text, name, hint, suffix, track, link, good, bad, line, tree;
        public final int[] chips;

        public Palette(int text, int name, int hint, int suffix, int track, int link, int good, int bad, int line,
            int tree, int[] chips) {
            this.text = text;
            this.name = name;
            this.hint = hint;
            this.suffix = suffix;
            this.track = track;
            this.link = link;
            this.good = good;
            this.bad = bad;
            this.line = line;
            this.tree = tree;
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

        public static Palette ae2(Variant variant) {
            Colors colors = variant.colors;
            return new Palette(
                colors.text(),
                colors.text(),
                colors.muted(),
                colors.secondary(),
                colors.line(),
                colors.info(),
                colors.good(),
                colors.bad(),
                colors.line(),
                variant == Variant.DARK ? colors.edge() : colors.line(),
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
                ColorUtils.craftingDiagnosticTerminalLine.getColor(),
                faint,
                new int[] { 0xFFFFFF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFFAA00, 0xFF5555 });
        }
    }
}
