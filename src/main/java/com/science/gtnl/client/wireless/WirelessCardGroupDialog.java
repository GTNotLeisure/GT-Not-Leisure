package com.science.gtnl.client.wireless;

import static codechicken.nei.NEIClientUtils.cropText;
import static com.science.gtnl.client.wireless.WirelessCardGui.tr;

import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.gtnllib.gui.theme.AE2Theme;
import com.gtnllib.gui.widget.FadingNotice;
import com.science.gtnl.common.wireless.WirelessCardBinding;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class WirelessCardGroupDialog extends Dialog<Void> {

    private final WirelessCardGui owner;
    private final List<NBTTagCompound> devices;
    private final FadingNotice notice = new FadingNotice().duration(5000);

    WirelessCardGroupDialog(WirelessCardGui owner, WirelessCardClientView.GroupDetails group) {
        super("wireless_card_group");
        this.owner = owner;
        devices = group.devices();
        int width = Math.min(440, owner.xSize - 20), height = Math.min(300, owner.ySize - 8);
        size(width, height);
        child(ButtonWidget.panelCloseButton());
        child(
            IKey.str(tr("display.group_title"))
                .asWidget()
                .pos(10, 8)
                .size(width - 40, 14));
        String name = WirelessCardGui.rowName(
            group.header(),
            ItemStack.loadItemStackFromNBT(
                group.header()
                    .getCompoundTag("icon")));
        child(
            IKey.str(cropText(owner.fontRendererObj, name, width - 20))
                .asWidget()
                .pos(10, 25)
                .size(width - 20, 14));
        child(
            IKey.str(tr("display.scope", devices.size()))
                .asWidget()
                .pos(10, 42)
                .size(width - 20, 12));
        int buttonWidth = (width - 28) / 3;
        for (int mode = 0; mode < WirelessCardBinding.DISPLAY_MODES; mode++) child(
            modeButton(mode).pos(10 + mode * (buttonWidth + 4), 58)
                .size(buttonWidth, 20));
        var list = new ListWidget<>().pos(10, 82)
            .size(width - 20, height - 132);
        for (NBTTagCompound device : devices) list.child(new DeviceRow(device, width - 32));
        child(list);
        child(
            notice.pos(10, height - 48)
                .size(width - 20, 18));
        ButtonWidget<?> done = new ButtonWidget<>();
        child(
            done.pos(width - 90, height - 26)
                .size(80, 20)
                .overlay(
                    AE2Theme.label(done, () -> StatCollector.translateToLocal("gui.done"), () -> true, () -> false))
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    closeIfOpen();
                    return true;
                }));
    }

    private ButtonWidget<?> modeButton(int mode) {
        ButtonWidget<?> button = new ButtonWidget<>();
        BooleanSupplier selected = () -> !devices.isEmpty() && devices.stream()
            .allMatch(
                device -> owner.clientView()
                    .displayMode(device) == mode);
        var background = AE2Theme.button(button, () -> !devices.isEmpty(), selected);
        return button.background(background)
            .hoverBackground(background)
            .overlay(AE2Theme.label(button, () -> tr("display.mode." + mode), () -> !devices.isEmpty(), selected))
            .tooltipBuilder(tooltip -> {
                tooltip.addLine(tr("display.apply_all", devices.size(), tr("display.mode." + mode)));
                tooltip.addLine(tr("display.auto_hint"));
                tooltip.addLine(tr("display.fallback_hint"));
            })
            .onMousePressed(pressed -> {
                if (pressed != 0 || devices.isEmpty()) return false;
                if (!owner.clientView()
                    .setDisplayMode(devices, mode))
                    owner.receive(
                        new ChatComponentTranslation(
                            WirelessCardGui.KEY_PREFIX + "display.limit",
                            WirelessCardBinding.MAX_DISPLAY_ENTRIES));
                return true;
            });
    }

    void receive(String text, int color) {
        notice.show(text, color);
    }

    @Override
    public boolean onKeyPressed(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            closeIfOpen();
            return true;
        }
        return super.onKeyPressed(character, key);
    }

    private class DeviceRow extends ButtonWidget<DeviceRow> {

        private final NBTTagCompound device;

        private DeviceRow(NBTTagCompound device, int width) {
            this.device = device;
            size(width, 32);
            onMousePressed(button -> {
                if (button != 0) return false;
                owner.clientView()
                    .cycleDisplayMode(device);
                return true;
            });
            tooltipAutoUpdate(true).tooltipBuilder(tooltip -> {
                ItemStack own = ItemStack.loadItemStackFromNBT(device.getCompoundTag("icon"));
                if (own != null) tooltip.addLine(own.getDisplayName());
                tooltip.addLine(device.getString("targetName"));
                tooltip.addLine(device.getString("positionLabel"));
                tooltip.addLine(
                    tr(
                        "display.mode",
                        tr(
                            "display.mode." + owner.clientView()
                                .displayMode(device))));
                tooltip.addLine(tr("display.cycle_hint"));
            });
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            device.setInteger(
                "displayMode",
                owner.clientView()
                    .displayMode(device));
            ItemStack icon = WirelessCardGui.rowIcon(device);
            if (icon != null) owner.drawDeviceIcon(icon, 3, 8);
            String mode = tr("display.mode." + device.getInteger("displayMode"));
            int modeWidth = owner.fontRendererObj.getStringWidth(mode), textWidth = getArea().width - modeWidth - 34;
            String name = WirelessCardGui
                .rowName(device, ItemStack.loadItemStackFromNBT(device.getCompoundTag("icon")));
            owner.drawText(cropText(owner.fontRendererObj, name, textWidth), 24, 5, owner.palette.name, false);
            owner.drawText(
                cropText(owner.fontRendererObj, device.getString("positionLabel"), textWidth),
                24,
                18,
                owner.palette.suffix,
                false);
            owner.drawText(mode, getArea().width - modeWidth - 4, 11, owner.palette.link, false);
        }
    }
}
