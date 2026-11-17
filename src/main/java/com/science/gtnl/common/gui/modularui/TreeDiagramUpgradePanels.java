package com.science.gtnl.common.gui.modularui;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizon.gtnhlib.util.data.ItemId;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.science.gtnl.common.circuit.CircuitCatalog;
import com.science.gtnl.common.circuit.CircuitFamily;
import com.science.gtnl.common.gui.GTNLMui2Textures;
import com.science.gtnl.common.gui.modularui.TreeDiagramBoard.Net;
import com.science.gtnl.common.gui.modularui.TreeDiagramBoard.Palette;
import com.science.gtnl.common.gui.modularui.TreeDiagramBoard.Trace;
import com.science.gtnl.common.machine.multiblock.wireless.TreeDiagram;

import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import gregtech.api.enums.GTValues;
import gregtech.common.gui.modularui.synchandler.NBTTagSyncHandler;
import gregtech.common.modularui2.widget.SlotLikeButtonWidget;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class TreeDiagramUpgradePanels extends GTNLControllerUpgradePanels {

    private static final String PAID_KEY = "treeDiagramFamilyPayments";
    public static final int WIDTH = 298;
    public static final int HEIGHT = 196;
    public static final int ROWS = 6;
    public static final int ROW_HEIGHT = 24;
    public static final int LIST_X = 8, LIST_Y = 38, LIST_WIDTH = 199, LIST_HEIGHT = 150;
    public static final int INPUT_X = 218, INPUT_Y = 38;

    private static final Trace UNLOCK_TOP = new Trace(9, 17, 60, 17, 63, 14, 100, 14);
    private static final Trace CU_LEFT = new Trace(4, 40, 4, 120, 6, 122, 6, 186);
    private static final Trace NANITE_GUTTER = new Trace(212, 40, 212, 100, 214, 102, 214, 110);
    private static final Trace FLUID_RIGHT = new Trace(294, 40, 294, 150);
    private static final Trace SUBMIT_FLOW = new Trace(217, 120, 212, 120, 208, 116, 207, 116);
    private static final Trace NANITE_CORNER = new Trace(294, 168, 268, 168, 262, 162, 238, 162);
    private static final Trace CU_CORNER = new Trace(294, 174, 254, 174, 248, 180, 228, 180);
    private static final Trace FLUID_CORNER = new Trace(294, 186, 278, 186);

    private final TreeDiagram machine;
    private final Map<String, IPanelHandler> panels;

    public TreeDiagramUpgradePanels(TreeDiagram machine, Map<String, IPanelHandler> panels) {
        super(machine, machine, panels);
        this.machine = machine;
        this.panels = panels;
    }

    @Override
    public void registerPanels(ModularPanel parent, PanelSyncManager sync) {
        panels.put(
            UPGRADE_CURRENT_PANEL_KEY,
            sync.syncedPanel(
                UPGRADE_CURRENT_PANEL_KEY,
                true,
                (panelSync, handler) -> createUpgradePanel(parent, sync, panelSync, true, 0)));
    }

    @Override
    public void registerSyncValues(PanelSyncManager sync) {
        sync.syncValue(PAID_KEY, new NBTTagSyncHandler(machine::getUnlockDisplayData, ignored -> {}));
    }

    @Override
    public IWidget createUpgradeButton(PanelSyncManager sync) {
        return new ButtonWidget<>().size(18)
            .overlay(GTNLMui2Textures.OVERLAY_BUTTON_ARROW_GREEN_UP)
            .onMousePressed(button -> {
                panels.get(UPGRADE_CURRENT_PANEL_KEY)
                    .openPanel();
                return true;
            })
            .tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang("gtnl.tree_diagram.gui.unlock")));
    }

    @Override
    protected ModularPanel createUpgradePanel(ModularPanel parent, PanelSyncManager rootSync,
        PanelSyncManager panelSync, boolean currentPanel, int displayPage) {
        NBTTagSyncHandler paid = rootSync.findSyncHandler(PAID_KEY, NBTTagSyncHandler.class);
        FamilyView view = new FamilyView(paid, machine.getUpgradeInputSlotHandler());
        Dialog<?> panel = new Dialog<>(UPGRADE_CURRENT_PANEL_KEY, null);
        panel.relative(parent)
            .size(WIDTH, HEIGHT)
            .background(new UnlockBoard(view));
        panel.setDisablePanelsBelow(false)
            .setCloseOnOutOfBoundsClick(false)
            .setDraggable(true);
        ButtonWidget<?> close = new ButtonWidget<>();
        panel.child(
            close.pos(WIDTH - 15, 4)
                .size(11)
                .background(TreeDiagramBoard.closeBackground())
                .hoverBackground(TreeDiagramBoard.closeBackground())
                .onMousePressed(button -> {
                    close.getPanel()
                        .closeIfOpen();
                    return true;
                }));
        Palette palette = TreeDiagramBoard.palette();
        panel.child(
            new TextFieldWidget().value(view.search)
                .setMaxLength(128)
                .autoUpdateOnChange(true)
                .setTextColor(TreeDiagramTheme.textColor(palette.text()))
                .hintText(tr("gtnl.tree_diagram.gui.search"))
                .hintColor(TreeDiagramTheme.textColor(palette.dim()))
                .onUpdateListener(field -> {
                    Palette current = TreeDiagramBoard.palette();
                    field.setTextColor(TreeDiagramTheme.textColor(current.text()))
                        .hintColor(TreeDiagramTheme.textColor(current.dim()));
                })
                .background(TreeDiagramBoard.fieldBackground())
                .hoverBackground(TreeDiagramBoard.fieldBackground())
                .pos(8, 20)
                .size(128, 14));
        panel.child(filterButton(view, false, 140, 28));
        panel.child(filterButton(view, true, 170, 36));
        panel.child(
            new FamilyList(view).pos(LIST_X, LIST_Y)
                .size(LIST_WIDTH, LIST_HEIGHT));
        for (int row = 0; row < ROWS; row++) {
            int index = row;
            panel.child(
                new SlotLikeButtonWidget(() -> view.item(index)).pos(12, LIST_Y + 2 + row * ROW_HEIGHT + 3)
                    .size(18)
                    .background(IDrawable.EMPTY)
                    .onMousePressed(button -> {
                        ItemStack item = view.item(index);
                        if (item == null) return false;
                        if (button == 0) GuiCraftingRecipe.openRecipeGui("item", item);
                        else if (button == 1) GuiUsageRecipe.openRecipeGui("item", item);
                        return true;
                    })
                    .tooltipBuilder(tooltip -> {
                        CircuitFamily family = view.family(index);
                        if (family == null) return;
                        tooltip.addLine(IKey.lang("gtnl.tree_diagram.gui.family_members"));
                        for (ItemId member : family.members()) tooltip.addLine(
                            member.toStack(1)
                                .getDisplayName());
                    })
                    .tooltipAutoUpdate(true));
        }
        panelSync.registerSlotGroup(UPGRADE_INPUT_SLOT_GROUP, 4, SlotGroup.STORAGE_SLOT_PRIO - 1);
        for (int slot = 0; slot < 16; slot++) {
            panel
                .child(
                    new ItemSlot()
                        .slot(
                            new ModularSlot(machine.getUpgradeInputSlotHandler(), slot).ignoreMaxStackSize(true)
                                .slotGroup(UPGRADE_INPUT_SLOT_GROUP))
                        .background(IDrawable.EMPTY)
                        .pos(INPUT_X + slot % 4 * 18, INPUT_Y + slot / 4 * 18));
        }
        IDrawable submit = (context, x, y, width, height, theme) -> {
            TreeDiagramBoard board = new TreeDiagramBoard(x, y);
            board.button(0, 0, width, height, Net.UNLOCK, true);
            board.textCenter(tr("gtnl.tree_diagram.gui.submit"), width / 2, (height - 8) / 2, board.palette.text());
            board.flush();
        };
        panel.child(
            new ButtonWidget<>().pos(INPUT_X, 113)
                .size(72, 16)
                .background(submit)
                .hoverBackground(submit)
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouse -> {
                    if (machine.getBaseMetaTileEntity() == null || !machine.getBaseMetaTileEntity()
                        .isServerSide()) return;
                    machine.tryConsumeItems();
                    paid.notifyUpdate();
                }))
                .tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang("gtnl.tree_diagram.gui.submit_hint"))));
        return panel;
    }

    private static ButtonWidget<?> filterButton(FamilyView view, boolean lockedOnly, int x, int width) {
        IDrawable look = (context, left, top, w, h, theme) -> {
            TreeDiagramBoard board = new TreeDiagramBoard(left, top);
            boolean selected = view.lockedOnly == lockedOnly;
            board.button(0, 0, w, h, Net.UNLOCK, selected);
            board.textCenter(
                tr(lockedOnly ? "gtnl.tree_diagram.gui.locked_only" : "gtnl.tree_diagram.gui.all_families"),
                w / 2,
                (h - 8) / 2,
                selected ? board.palette.text() : board.palette.muted());
            board.flush();
        };
        return new ButtonWidget<>().pos(x, 20)
            .size(width, 14)
            .background(look)
            .hoverBackground(look)
            .onMousePressed(button -> {
                if (view.lockedOnly != lockedOnly) {
                    view.lockedOnly = lockedOnly;
                    view.lastSearch = null;
                }
                return true;
            });
    }

    private static String tr(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(key, args);
    }

    public static String familyName(CircuitFamily family) {
        String key = "gtnl.tree_diagram.family." + family.id();
        if (StatCollector.canTranslate(key)) return StatCollector.translateToLocal(key);
        return family.unlockItem()
            .toStack(1)
            .getDisplayName();
    }

    public static String tierRange(CircuitFamily family) {
        int first = Math.max(
            0,
            family.unlockTier() - family.members()
                .size() + 1);
        return first == family.unlockTier() ? GTValues.VN[first]
            : GTValues.VN[first] + "–" + GTValues.VN[family.unlockTier()];
    }

    public static class FamilyList extends Widget<FamilyList> implements Interactable {

        private final FamilyView view;

        public FamilyList(FamilyView view) {
            this.view = view;
            background(IDrawable.EMPTY);
        }

        @Override
        public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
            view.scroll(scrollDirection.isUp() ? -1 : 1);
            return true;
        }
    }

    public static class UnlockBoard implements IDrawable {

        private final FamilyView view;

        public UnlockBoard(FamilyView view) {
            this.view = view;
        }

        @Override
        public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
            TreeDiagramBoard board = new TreeDiagramBoard(x, y);
            Palette p = board.palette;
            float phase = Minecraft.getSystemTime() % 2400 / 2400f;
            board.panel(0, 0, width, height);
            board.text(tr("gtnl.tree_diagram.gui.unlock"), 9, 7, p.text());
            List<CircuitFamily> all = CircuitCatalog.INSTANCE.families();
            int unlocked = 0;
            for (CircuitFamily family : all) if (view.paid(family) >= family.requiredItems()) unlocked++;
            board.runsRight(
                width - 20,
                7,
                tr("gtnl.tree_diagram.gui.unlocked_label") + " ",
                p.muted(),
                unlocked,
                p.wire(Net.UNLOCK)
                    .bright(),
                " / " + all.size(),
                p.muted());
            board.trace(UNLOCK_TOP, Net.UNLOCK, false);
            board.pad(101, 14, Net.UNLOCK, false);
            board.trace(CU_LEFT, Net.CU, false);
            board.trace(NANITE_GUTTER, Net.NANITE, false);
            board.trace(FLUID_RIGHT, Net.FLUID, false);
            board.pad(294, 151, Net.FLUID, false);
            boolean loaded = view.hasInput();
            if (loaded) board.trace(SUBMIT_FLOW, Net.UNLOCK, true, phase + 0.3f);
            else board.trace(SUBMIT_FLOW, Net.UNLOCK, false);
            board.pad(218, 120, Net.UNLOCK, loaded);
            board.trace(NANITE_CORNER, Net.NANITE, false);
            board.pad(237, 162, Net.NANITE, false);
            board.trace(CU_CORNER, Net.CU, false);
            board.pad(227, 180, Net.CU, false);
            board.trace(FLUID_CORNER, Net.FLUID, false);
            board.pad(277, 186, Net.FLUID, false);
            drawList(board);
            board.text(tr("gtnl.tree_diagram.gui.input"), INPUT_X, 23, p.muted());
            board.textRight(tr("gtnl.tree_diagram.gui.input_slots", 16), 290, 23, p.dim());
            for (int slot = 0; slot < 16; slot++) board.slot(INPUT_X + slot % 4 * 18, INPUT_Y + slot / 4 * 18, 18, 0);
            board.text(tr("gtnl.tree_diagram.gui.unlock_hint_short"), INPUT_X, 135, p.muted());
            board.text(tr("gtnl.tree_diagram.gui.unlock_hint_extra"), INPUT_X, 145, p.muted());
            board.flush();
        }

        private static void drawTitle(TreeDiagramBoard board, CircuitFamily family, int y, int available) {
            Palette p = board.palette;
            String name = familyName(family);
            String tiers = tierRange(family);
            int nameWidth = TreeDiagramBoard.width(name);
            if (nameWidth + 4 + TreeDiagramBoard.width(tiers) <= available) {
                board.text(name, 34, y, p.text());
                board.text(
                    tiers,
                    34 + nameWidth + 4,
                    y,
                    p.wire(Net.CU)
                        .bright());
            } else {
                board.textFit(name, 34, y, available, p.text(), false);
            }
        }

        private void drawList(TreeDiagramBoard board) {
            Palette p = board.palette;
            board.screen(LIST_X, LIST_Y, LIST_WIDTH, LIST_HEIGHT);
            List<CircuitFamily> families = view.filtered();
            for (int row = 0; row < ROWS; row++) {
                int y = LIST_Y + 2 + row * ROW_HEIGHT;
                board.slot(12, y + 3, 18, 0);
                CircuitFamily family = view.family(row);
                if (family != null) {
                    int required = family.requiredItems();
                    int paid = view.paid(family);
                    int rightWidth;
                    int pending = 0;
                    if (paid >= required) {
                        String done = tr("gtnl.tree_diagram.gui.unlocked");
                        rightWidth = TreeDiagramBoard.width(done);
                        board.textRight(done, 194, y + 3, p.ok());
                        board.litBar(34, y + 14, 154, 1, Net.UNLOCK, 0, Net.FLUID);
                    } else {
                        pending = Math.min(view.pending(family), required - paid);
                        String counts = NumberFormatUtil.formatNumber(paid) + " / "
                            + NumberFormatUtil.formatNumber(required);
                        rightWidth = TreeDiagramBoard.width(counts);
                        board.textRight(counts, 194, y + 3, p.muted());
                        if (pending > 0) {
                            String extra = "+" + NumberFormatUtil.formatNumber(pending);
                            board.textRight(extra, 194 - rightWidth - 4, y + 3, p.ok());
                            rightWidth += TreeDiagramBoard.width(extra) + 4;
                        }
                    }
                    drawTitle(board, family, y + 3, 194 - rightWidth - 6 - 34);
                    if (paid < required) {
                        board.litBar(
                            34,
                            y + 14,
                            154,
                            paid / (double) required,
                            Net.UNLOCK,
                            pending / (double) required,
                            Net.FLUID);
                    }
                }
                if (row < ROWS - 1) {
                    for (int dot = 12; dot < 200; dot += 3) board.rect(dot, y + 23, 1, 1, p.separator());
                }
            }
            int track = LIST_HEIGHT - 6;
            board.rect(
                202,
                LIST_Y + 3,
                1,
                track,
                p.wire(Net.GRAY)
                    .dim());
            int total = families.size();
            if (total > ROWS) {
                int thumb = Math.max(8, track * ROWS / total);
                int top = (track - thumb) * view.offset / Math.max(1, total - ROWS);
                board.rect(
                    201,
                    LIST_Y + 3 + top,
                    3,
                    thumb,
                    p.wire(Net.GRAY)
                        .bright());
            }
        }
    }

    public static class FamilyView {

        private final NBTTagSyncHandler paid;
        private final ItemStackHandler input;
        public final StringValue search = new StringValue("");
        public List<CircuitFamily> visible = Collections.emptyList();
        public String lastSearch;
        public NBTTagCompound lastPayments;
        public boolean lockedOnly;
        public int offset;

        public FamilyView(NBTTagSyncHandler paid, ItemStackHandler input) {
            this.paid = paid;
            this.input = input;
        }

        public List<CircuitFamily> filtered() {
            String query = search.getStringValue()
                .trim()
                .toLowerCase(Locale.ROOT);
            NBTTagCompound payments = paid.getValue();
            if (!query.equals(lastSearch) || payments != lastPayments) {
                boolean changedSearch = !query.equals(lastSearch);
                lastSearch = query;
                lastPayments = payments;
                visible = new ObjectArrayList<>();
                for (CircuitFamily family : CircuitCatalog.INSTANCE.families()) {
                    if (lockedOnly && paid(family) >= family.requiredItems()) continue;
                    if (matches(family, query)) visible.add(family);
                }
                offset = changedSearch ? 0 : Math.min(offset, Math.max(0, visible.size() - ROWS));
            }
            return visible;
        }

        private static boolean matches(CircuitFamily family, String query) {
            if (query.isEmpty() || family.id()
                .contains(query)
                || familyName(family).toLowerCase(Locale.ROOT)
                    .contains(query)
                || tierRange(family).toLowerCase(Locale.ROOT)
                    .contains(query))
                return true;
            for (ItemId member : family.members()) {
                if (member.toStack(1)
                    .getDisplayName()
                    .toLowerCase(Locale.ROOT)
                    .contains(query)) return true;
            }
            return false;
        }

        public void scroll(int rows) {
            offset = Math.max(0, Math.min(offset + rows, filtered().size() - ROWS));
        }

        public CircuitFamily family(int row) {
            List<CircuitFamily> families = filtered();
            int index = offset + row;
            return index < families.size() ? families.get(index) : null;
        }

        public ItemStack item(int row) {
            CircuitFamily family = family(row);
            return family == null ? null
                : family.unlockItem()
                    .toStack(1);
        }

        public int paid(CircuitFamily family) {
            NBTTagCompound payments = paid.getValue();
            return payments == null ? 0
                : Math.max(0, Math.min(family.requiredItems(), payments.getInteger(family.id())));
        }

        public int pending(CircuitFamily family) {
            Object2IntOpenHashMap<String> totals = new Object2IntOpenHashMap<>();
            for (int slot = 0; slot < input.getSlots(); slot++) {
                CircuitFamily owner = CircuitCatalog.INSTANCE.unlockFamily(input.getStackInSlot(slot));
                if (owner != null) totals.addTo(owner.id(), input.getStackInSlot(slot).stackSize);
            }
            return totals.getInt(family.id());
        }

        public boolean hasInput() {
            for (int slot = 0; slot < input.getSlots(); slot++) {
                ItemStack stack = input.getStackInSlot(slot);
                if (stack != null && stack.stackSize > 0) return true;
            }
            return false;
        }
    }
}
