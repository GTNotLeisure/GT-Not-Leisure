package com.science.gtnl.common.packet;

import net.minecraft.client.Minecraft;

import com.gtnllib.network.ClientboundPacket;
import com.science.gtnl.client.projector.ProjectorClient;
import com.science.gtnl.common.projector.ProjectorQuote;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class ProjectorQuoteResultPacket extends ClientboundPacket {

    private int[] kinds = new int[0], available = new int[0];

    public ProjectorQuoteResultPacket() {}

    public ProjectorQuoteResultPacket(int[] kinds, int[] available) {
        this.kinds = kinds;
        this.available = available;
    }

    @Override
    protected void read(ByteBuf buf) {
        int size = buf.readInt();
        if (size < 0 || size > ProjectorQuote.MAX_KINDS || size * 8 > buf.readableBytes())
            throw new IllegalArgumentException("Invalid projector quote result");
        kinds = new int[size];
        available = new int[size];
        for (int i = 0; i < size; i++) {
            kinds[i] = buf.readInt();
            available[i] = buf.readInt();
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeInt(kinds.length);
        for (int i = 0; i < kinds.length; i++) {
            buf.writeInt(kinds[i]);
            buf.writeInt(available[i]);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        runInWorld(minecraft, () -> ProjectorClient.quoted(kinds, available));
    }
}
