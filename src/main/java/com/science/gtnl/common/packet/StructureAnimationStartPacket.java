package com.science.gtnl.common.packet;

import net.minecraft.client.Minecraft;

import com.gtnllib.network.ClientboundPacket;
import com.science.gtnl.client.structureanimation.StructureAnimations;
import com.science.gtnl.common.structureanimation.StructureAnimationStyle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class StructureAnimationStartPacket extends ClientboundPacket {

    public static final int MAX_KINDS = 0xFFFF, MAX_EXTRA = 1024, MAX_CELLS = 200_000, MAX_SIDE = 256,
        MAX_START_BYTES = 1 << 20;

    public record Start(int job, int dimension, int originX, int originY, int originZ, int sizeX, int sizeY, int sizeZ,
        byte[] kinds, int[] items, int[] damages, int[] frontCells, byte[] fronts, int controllerX, int controllerY,
        int controllerZ, int facing, StructureAnimationStyle style, int cells, int placingTick, boolean own,
        int elapsed, boolean reversed) {

        // The same build for another receiver: whether it is theirs, and how many ticks ago it started.
        public Start as(boolean own, int elapsed) {
            return new Start(
                job,
                dimension,
                originX,
                originY,
                originZ,
                sizeX,
                sizeY,
                sizeZ,
                kinds,
                items,
                damages,
                frontCells,
                fronts,
                controllerX,
                controllerY,
                controllerZ,
                facing,
                style,
                cells,
                placingTick,
                own,
                elapsed,
                reversed);
        }
    }

    private Start start;
    private byte[] extra;

    public StructureAnimationStartPacket() {}

    public StructureAnimationStartPacket(Start start, byte[] extra) {
        this.start = start;
        this.extra = extra;
    }

    @Override
    protected void read(ByteBuf buf) {
        int job = buf.readInt(), dimension = buf.readInt(), originX = buf.readInt(), originY = buf.readInt(),
            originZ = buf.readInt();
        int sizeX = buf.readShort(), sizeY = buf.readShort(), sizeZ = buf.readShort();
        int length = buf.readInt();
        if (sizeX <= 0 || sizeY <= 0
            || sizeZ <= 0
            || sizeX > MAX_SIDE
            || sizeY > MAX_SIDE
            || sizeZ > MAX_SIDE
            || length < 0
            || length > MAX_START_BYTES
            || length > buf.readableBytes()) throw new IllegalArgumentException("Invalid projector build start");
        byte[] kinds = new byte[length];
        buf.readBytes(kinds);
        int kindCount = buf.readInt();
        if (kindCount < 0 || kindCount > MAX_KINDS) throw new IllegalArgumentException("Invalid projector kinds");
        int[] items = new int[kindCount], damages = new int[kindCount];
        for (int i = 0; i < kindCount; i++) {
            items[i] = buf.readInt();
            damages[i] = buf.readUnsignedShort();
        }
        int frontCount = buf.readInt();
        if (frontCount < 0 || frontCount > MAX_CELLS) throw new IllegalArgumentException("Invalid projector fronts");
        int[] frontCells = new int[frontCount];
        byte[] fronts = new byte[frontCount];
        for (int i = 0; i < frontCount; i++) {
            frontCells[i] = buf.readInt();
            fronts[i] = buf.readByte();
        }
        int controllerX = buf.readInt(), controllerY = buf.readInt(), controllerZ = buf.readInt(),
            facing = buf.readInt();
        int style = buf.readByte();
        int cells = buf.readInt(), placingTick = buf.readInt();
        if (cells < 0 || cells > MAX_CELLS || placingTick < 0)
            throw new IllegalArgumentException("Invalid projector build length");
        boolean own = buf.readBoolean();
        int elapsed = Math.max(0, buf.readInt());
        boolean reversed = buf.readBoolean();
        int extraLength = buf.readShort();
        if (style < 0 || style >= StructureAnimationStyle.values().length
            || extraLength < 0
            || extraLength > MAX_EXTRA
            || extraLength > buf.readableBytes()) throw new IllegalArgumentException("Invalid projector build style");
        extra = new byte[extraLength];
        buf.readBytes(extra);
        start = new Start(
            job,
            dimension,
            originX,
            originY,
            originZ,
            sizeX,
            sizeY,
            sizeZ,
            kinds,
            items,
            damages,
            frontCells,
            fronts,
            controllerX,
            controllerY,
            controllerZ,
            facing,
            StructureAnimationStyle.values()[style],
            cells,
            placingTick,
            own,
            elapsed,
            reversed);
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeInt(start.job());
        buf.writeInt(start.dimension());
        buf.writeInt(start.originX());
        buf.writeInt(start.originY());
        buf.writeInt(start.originZ());
        buf.writeShort(start.sizeX());
        buf.writeShort(start.sizeY());
        buf.writeShort(start.sizeZ());
        buf.writeInt(start.kinds().length);
        buf.writeBytes(start.kinds());
        buf.writeInt(start.items().length);
        for (int i = 0; i < start.items().length; i++) {
            buf.writeInt(start.items()[i]);
            buf.writeShort(start.damages()[i]);
        }
        buf.writeInt(start.frontCells().length);
        for (int i = 0; i < start.frontCells().length; i++) {
            buf.writeInt(start.frontCells()[i]);
            buf.writeByte(start.fronts()[i]);
        }
        buf.writeInt(start.controllerX());
        buf.writeInt(start.controllerY());
        buf.writeInt(start.controllerZ());
        buf.writeInt(start.facing());
        buf.writeByte(
            start.style()
                .ordinal());
        buf.writeInt(start.cells());
        buf.writeInt(start.placingTick());
        buf.writeBoolean(start.own());
        buf.writeInt(start.elapsed());
        buf.writeBoolean(start.reversed());
        buf.writeShort(extra.length);
        buf.writeBytes(extra);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        Start received = start;
        byte[] payload = extra;
        runInWorld(minecraft, () -> StructureAnimations.start(received, payload));
    }
}
