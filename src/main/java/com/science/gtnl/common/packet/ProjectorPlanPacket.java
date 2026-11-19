package com.science.gtnl.common.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;
import com.gtnllib.network.ServerboundPacket;
import com.science.gtnl.common.projector.ProjectorServer;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class ProjectorPlanPacket extends ServerboundPacket {

    private int location;
    private NBTTagCompound plan;

    public ProjectorPlanPacket() {}

    public ProjectorPlanPacket(int location, NBTTagCompound plan) {
        this.location = location;
        this.plan = plan;
    }

    @Override
    protected void read(ByteBuf buf) {
        location = buf.readInt();
        plan = ByteBufUtils.readTag(buf);
        if (plan == null) throw new IllegalArgumentException("Missing projector plan");
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeInt(location);
        ByteBufUtils.writeTag(buf, plan);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        ServerThreadUtil.addScheduledTask(() -> ProjectorServer.applyPlan(player, location, plan));
    }
}
