package com.science.gtnl.common.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnllib.network.ServerboundPacket;
import com.science.gtnl.common.wireless.remote.RemoteDeviceSessions;

import io.netty.buffer.ByteBuf;

public class RemoteDeviceRequestPacket extends ServerboundPacket {

    public static final int RETURN = 0, FAILED = 1;
    private int kind, windowId;
    private long session;

    public RemoteDeviceRequestPacket() {}

    public RemoteDeviceRequestPacket(int kind, long session, int windowId) {
        this.kind = kind;
        this.session = session;
        this.windowId = windowId;
    }

    @Override
    protected void read(ByteBuf buf) {
        kind = buf.readByte();
        session = buf.readLong();
        windowId = buf.readInt();
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeByte(kind);
        buf.writeLong(session);
        buf.writeInt(windowId);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        RemoteDeviceSessions.request(player, kind, session, windowId);
    }
}
