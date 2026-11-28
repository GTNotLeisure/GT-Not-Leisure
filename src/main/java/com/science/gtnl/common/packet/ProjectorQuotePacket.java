package com.science.gtnl.common.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;
import com.gtnllib.network.ServerboundPacket;
import com.science.gtnl.common.projector.ProjectorQuote;

import io.netty.buffer.ByteBuf;

public class ProjectorQuotePacket extends ServerboundPacket {

    private int[] items = new int[0], damages = new int[0], counts = new int[0];

    public ProjectorQuotePacket() {}

    public ProjectorQuotePacket(int[] items, int[] damages, int[] counts) {
        this.items = items;
        this.damages = damages;
        this.counts = counts;
    }

    @Override
    protected void read(ByteBuf buf) {
        int size = buf.readInt();
        if (size < 0 || size > ProjectorQuote.MAX_KINDS || size * 12 > buf.readableBytes())
            throw new IllegalArgumentException("Invalid projector quote");
        items = new int[size];
        damages = new int[size];
        counts = new int[size];
        for (int i = 0; i < size; i++) {
            items[i] = buf.readInt();
            damages[i] = buf.readInt();
            counts[i] = buf.readInt();
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeInt(items.length);
        for (int i = 0; i < items.length; i++) {
            buf.writeInt(items[i]);
            buf.writeInt(damages[i]);
            buf.writeInt(counts[i]);
        }
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        ServerThreadUtil.addScheduledTask(() -> ProjectorQuote.answer(player, items, damages, counts));
    }
}
