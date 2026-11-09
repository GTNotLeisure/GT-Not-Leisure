package com.science.gtnl.common.wireless.remote;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;

import org.jetbrains.annotations.Nullable;

public class RemoteDeviceGuis {

    // Extension devices subclass AE2 parts; their adapters must come before the generic AE2 one.
    private static final List<IRemoteDeviceGui> ADAPTERS = List.of(
        GTNLRemoteDeviceGui.INSTANCE,
        AE2FCRemoteDeviceGui.INSTANCE,
        GTRemoteDeviceGui.INSTANCE,
        AE2RemoteDeviceGui.INSTANCE);

    public record Match(IRemoteDeviceGui gui, int screen) {}

    private RemoteDeviceGuis() {}

    public static @Nullable Match find(Object device, EntityPlayer player) {
        for (IRemoteDeviceGui gui : ADAPTERS) {
            int screen = gui.mainScreen(device, player);
            if (screen != IRemoteDeviceGui.NO_GUI) return new Match(gui, screen);
        }
        return null;
    }

    public static RemoteDeviceState withoutScreen(Object device) {
        // Every AE2 device with a screen of its own is mapped, so an unmatched one that opens like AE2 has none.
        return AE2RemoteDeviceGui.opensLikeAE2(device) ? RemoteDeviceState.NO_GUI : RemoteDeviceState.UNSUPPORTED;
    }

    public static @Nullable IRemoteDeviceGui byId(String id) {
        for (IRemoteDeviceGui gui : ADAPTERS) if (gui.id()
            .equals(id)) return gui;
        return null;
    }
}
