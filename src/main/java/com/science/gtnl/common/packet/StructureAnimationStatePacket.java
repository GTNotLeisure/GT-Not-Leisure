package com.science.gtnl.common.packet;

import net.minecraft.client.Minecraft;

import com.gtnllib.network.ClientboundPacket;
import com.science.gtnl.client.structureanimation.StructureAnimations;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class StructureAnimationStatePacket extends ClientboundPacket {

    public static final int PLACING = 0, PROGRESS = 1, DONE = 2, ABORTED = 3;
    private static final int[] NONE = new int[0];
    private int job, state, processed;
    private int[] skipped = NONE;

    public StructureAnimationStatePacket() {}

    public StructureAnimationStatePacket(int job, int state, int processed) {
        this(job, state, processed, NONE);
    }

    // Skipped cells are indices into the start packet's box, like its front cells.
    public StructureAnimationStatePacket(int job, int state, int processed, int[] skipped) {
        this.job = job;
        this.state = state;
        this.processed = processed;
        this.skipped = skipped;
    }

    @Override
    protected void read(ByteBuf buf) {
        job = buf.readInt();
        state = buf.readByte();
        processed = buf.readInt();
        int count = buf.readInt();
        if (count < 0 || count > StructureAnimationStartPacket.MAX_CELLS || count * 4 > buf.readableBytes())
            throw new IllegalArgumentException("Invalid projector skipped cells");
        skipped = new int[count];
        for (int i = 0; i < count; i++) skipped[i] = buf.readInt();
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeInt(job);
        buf.writeByte(state);
        buf.writeInt(processed);
        buf.writeInt(skipped.length);
        for (int cell : skipped) buf.writeInt(cell);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        runInWorld(minecraft, () -> StructureAnimations.state(job, state, processed, skipped));
    }
}
