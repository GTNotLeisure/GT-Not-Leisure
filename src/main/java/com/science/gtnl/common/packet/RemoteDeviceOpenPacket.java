package com.science.gtnl.common.packet;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnllib.network.ClientboundPacket;
import com.science.gtnl.client.wireless.RemoteDeviceClient;
import com.science.gtnl.common.wireless.WirelessChannelManager.Address;
import com.science.gtnl.common.wireless.remote.RemoteDeviceMirror;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class RemoteDeviceOpenPacket extends ClientboundPacket {

    private long session;
    private int windowId, screen;
    private String gui = "";
    private Address address;
    private byte[] mirror;

    public RemoteDeviceOpenPacket() {}

    public RemoteDeviceOpenPacket(long session, int windowId, String gui, int screen, Address address, byte[] mirror) {
        this.session = session;
        this.windowId = windowId;
        this.gui = gui;
        this.screen = screen;
        this.address = address;
        this.mirror = mirror;
    }

    @Override
    protected void read(ByteBuf buf) {
        session = buf.readLong();
        windowId = buf.readInt();
        gui = ByteBufUtils.readUTF8String(buf);
        screen = buf.readInt();
        address = new Address(
            buf.readInt(),
            buf.readInt(),
            buf.readInt(),
            buf.readInt(),
            ForgeDirection.getOrientation(buf.readByte()));
        int length = buf.readInt();
        if (length < 0 || length > RemoteDeviceMirror.MAX_BYTES || length > buf.readableBytes())
            throw new IllegalArgumentException("Invalid remote device data length: " + length);
        mirror = length == 0 ? null : new byte[length];
        if (mirror != null) buf.readBytes(mirror);
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeLong(session);
        buf.writeInt(windowId);
        ByteBufUtils.writeUTF8String(buf, gui);
        buf.writeInt(screen);
        buf.writeInt(address.dimension());
        buf.writeInt(address.x());
        buf.writeInt(address.y());
        buf.writeInt(address.z());
        buf.writeByte(
            address.side()
                .ordinal());
        buf.writeInt(mirror == null ? 0 : mirror.length);
        if (mirror != null) buf.writeBytes(mirror);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        RemoteDeviceClient.open(minecraft, session, windowId, gui, screen, address, mirror);
    }
}
