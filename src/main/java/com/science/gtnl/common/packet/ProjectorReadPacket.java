package com.science.gtnl.common.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;
import com.gtnllib.network.ServerboundPacket;
import com.science.gtnl.common.projector.ProjectorReading;

import io.netty.buffer.ByteBuf;

public class ProjectorReadPacket extends ServerboundPacket {

    private int location, x, y, z;

    public ProjectorReadPacket() {}

    public ProjectorReadPacket(int location, int x, int y, int z) {
        this.location = location;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    protected void read(ByteBuf buf) {
        location = buf.readInt();
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeInt(location);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        ServerThreadUtil.addScheduledTask(() -> ProjectorReading.request(player, location, x, y, z));
    }
}
