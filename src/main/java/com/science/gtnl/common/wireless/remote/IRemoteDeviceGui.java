package com.science.gtnl.common.wireless.remote;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.Nullable;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public interface IRemoteDeviceGui {

    int NO_GUI = -1;

    /** Identifier written into open packets; the client resolves the same adapter by it. */
    String id();

    /**
     * Returns the screen the device's own right click opens, or {@link #NO_GUI} when this adapter does not handle the
     * device. The device is the part on its side, or the tile entity for whole-block devices.
     */
    int mainScreen(Object device, EntityPlayer player);

    /**
     * Opens one screen of the session's device on the server thread and attaches the resulting container to the
     * session through {@link RemoteDeviceSessions#show} or {@link RemoteDeviceSessions#attach}. Returns the reason
     * when the device refuses the player.
     */
    RemoteDeviceState open(RemoteDeviceSession session, int screen);

    /**
     * Builds the client screen for a mirrored host from the data {@link #describe} added. Only adapters that open
     * through {@link RemoteDeviceSessions#show} are asked; returns null when the mirror cannot show the screen.
     */
    @SideOnly(Side.CLIENT)
    @Nullable
    Object clientScreen(EntityPlayer player, RemoteDeviceHost mirror, int screen, NBTTagCompound data);

    /** Adds device state the screen needs but the block's own client data does not carry. Runs on the server. */
    default void describe(Object device, NBTTagCompound data) {}
}
