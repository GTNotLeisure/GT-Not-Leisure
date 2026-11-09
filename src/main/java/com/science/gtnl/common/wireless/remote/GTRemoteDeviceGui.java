package com.science.gtnl.common.wireless.remote;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.Nullable;

import com.gtnewhorizons.modularui.api.screen.ModularUIContext;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.internal.wrapper.ModularGui;
import com.gtnewhorizons.modularui.common.internal.wrapper.ModularUIContainer;
import com.science.gtnl.api.mixinHelper.IOutputME;
import com.science.gtnl.mixins.early.gregtech.AccessorCommonMetaTileEntity;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.modularui2.GTGuis;
import gregtech.api.modularui2.ProxiedMteGui;

public class GTRemoteDeviceGui implements IRemoteDeviceGui {

    public static final GTRemoteDeviceGui INSTANCE = new GTRemoteDeviceGui();
    public static final int MODULAR_UI_2 = 0, MODULAR_UI_1 = 1;

    @Override
    public String id() {
        return "gt";
    }

    @Override
    public int mainScreen(Object device, EntityPlayer player) {
        MetaTileEntity machine = machine(device);
        if (machine == null || !opensOwnScreen(machine)) return NO_GUI;
        return usesModularUI2(machine) ? MODULAR_UI_2 : MODULAR_UI_1;
    }

    @Override
    public RemoteDeviceState open(RemoteDeviceSession session, int screen) {
        MetaTileEntity machine = machine(session.host.device());
        if (machine == null) return RemoteDeviceState.STALE;
        RemoteDeviceState access = access(machine, session.player);
        if (access != RemoteDeviceState.OPEN) return access;
        // Native right-click records the recipient of output hatch/bus mode notifications before opening the GUI.
        if (machine instanceof IOutputME output) output.setLastClickedPlayer(session.player);
        if (screen == MODULAR_UI_1) return openModularUI1(session, machine);
        Container previous = session.player.openContainer;
        ProxiedMteGui.open(machine, session.player);
        return RemoteDeviceSessions.attach(session, this, previous, screen) ? RemoteDeviceState.OPEN
            : RemoteDeviceState.FAILED;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public @Nullable Object clientScreen(EntityPlayer player, RemoteDeviceHost mirror, int screen,
        NBTTagCompound data) {
        if (screen != MODULAR_UI_1 || !(mirror.tile() instanceof BaseMetaTileEntity base)) return null;
        ModularUIContainer container = container(player, base, null);
        return container == null ? null : new ModularGui(container);
    }

    public static RemoteDeviceState access(MetaTileEntity machine, EntityPlayerMP player) {
        IGregTechTileEntity base = machine.getBaseMetaTileEntity();
        if (base == null || !base.canAccessData()) return RemoteDeviceState.STALE;
        if (base instanceof BaseMetaTileEntity owned && owned.privateAccess()
            && !player.getUniqueID()
                .equals(owned.getOwnerUuid()))
            return RemoteDeviceState.DENIED;
        return machine.isAccessAllowed(player) ? RemoteDeviceState.OPEN : RemoteDeviceState.DENIED;
    }

    private static RemoteDeviceState openModularUI1(RemoteDeviceSession session, MetaTileEntity machine) {
        if (!(machine.getBaseMetaTileEntity() instanceof BaseMetaTileEntity base)) return RemoteDeviceState.NO_GUI;
        ModularUIContainer container = container(session.player, base, base::markDirty);
        if (container == null) return RemoteDeviceState.NO_GUI;
        return RemoteDeviceSessions.show(session, INSTANCE, container, MODULAR_UI_1);
    }

    private static @Nullable ModularUIContainer container(EntityPlayer player, BaseMetaTileEntity base,
        @Nullable Runnable onWidgetUpdate) {
        UIBuildContext context = new UIBuildContext(player);
        ModularWindow window = base.createWindow(context);
        return window == null ? null : new ModularUIContainer(new ModularUIContext(context, onWidgetUpdate), window);
    }

    private static boolean opensOwnScreen(MetaTileEntity machine) {
        String name = machine.getClass()
            .getName();
        // Other add-on machines may do something else on right click; GTNL's networked hatches all open their screen.
        return name.startsWith("gregtech.") || name.startsWith("com.science.gtnl.");
    }

    private static boolean usesModularUI2(MetaTileEntity machine) {
        return GTGuis.GLOBAL_SWITCH_MUI2 ? machine.hasMui2Gui()
            : ((AccessorCommonMetaTileEntity) machine).gtnl$forceUseMui2();
    }

    public static @Nullable MetaTileEntity machine(Object device) {
        return device instanceof IGregTechTileEntity base && base.getMetaTileEntity() instanceof MetaTileEntity machine
            ? machine
            : null;
    }
}
