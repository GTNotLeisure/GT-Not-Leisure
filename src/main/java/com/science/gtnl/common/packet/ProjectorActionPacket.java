package com.science.gtnl.common.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;
import com.gtnllib.network.ServerboundPacket;
import com.science.gtnl.common.projector.ProjectorBuilder;
import com.science.gtnl.common.projector.ProjectorDismantler;
import com.science.gtnl.common.projector.ProjectorOrders;

import io.netty.buffer.ByteBuf;

public class ProjectorActionPacket extends ServerboundPacket {

    public static final int UNDO = 0, DISMANTLE = 1, CANCEL = 2;

    private int location, action;

    public ProjectorActionPacket() {}

    public ProjectorActionPacket(int location, int action) {
        this.location = location;
        this.action = action;
    }

    @Override
    protected void read(ByteBuf buf) {
        location = buf.readInt();
        action = buf.readByte();
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeInt(location);
        buf.writeByte(action);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        ServerThreadUtil.addScheduledTask(() -> {
            switch (action) {
                case UNDO -> ProjectorDismantler.undo(player, location);
                case DISMANTLE -> ProjectorBuilder.requestDismantle(player, location);
                case CANCEL -> {
                    boolean stopped = ProjectorBuilder.cancel(player) | ProjectorDismantler.cancel(player)
                        | ProjectorOrders.cancel(player, true);
                    if (!stopped) ProjectorBuilder.refuse(player, "nothing_running");
                }
                default -> {}
            }
        });
    }
}
