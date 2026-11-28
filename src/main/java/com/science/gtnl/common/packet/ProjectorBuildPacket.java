package com.science.gtnl.common.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;
import com.gtnllib.network.ServerboundPacket;
import com.science.gtnl.common.projector.ProjectorBuilder;

import io.netty.buffer.ByteBuf;

public class ProjectorBuildPacket extends ServerboundPacket {

    private int location;

    public ProjectorBuildPacket() {}

    public ProjectorBuildPacket(int location) {
        this.location = location;
    }

    @Override
    protected void read(ByteBuf buf) {
        location = buf.readInt();
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeInt(location);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        ServerThreadUtil.addScheduledTask(() -> ProjectorBuilder.request(player, location));
    }
}
