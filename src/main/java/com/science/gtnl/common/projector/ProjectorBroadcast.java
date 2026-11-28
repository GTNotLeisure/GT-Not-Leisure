package com.science.gtnl.common.projector;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.Deflater;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Vec3;

import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.packet.StructureAnimationStartPacket;
import com.science.gtnl.common.packet.StructureAnimationStartPacket.Start;
import com.science.gtnl.common.packet.StructureAnimationStatePacket;
import com.science.gtnl.common.projector.ProjectorBuilder.Cell;
import com.science.gtnl.common.structureanimation.StructureAnimationStyle;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

// Starts the animation of a build or a dismantle for the players near it and keeps them told how it goes.
public class ProjectorBroadcast {

    public static final int VIEW_RANGE = 128, MAX_START_BYTES = StructureAnimationStartPacket.MAX_START_BYTES;

    public record Kinds(byte[] deflated, int[] items, int[] damages) {}

    public record Box(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {

        public static Box of(int[] xs, int[] ys, int[] zs) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE,
                maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < xs.length; i++) {
                minX = Math.min(minX, xs[i]);
                minY = Math.min(minY, ys[i]);
                minZ = Math.min(minZ, zs[i]);
                maxX = Math.max(maxX, xs[i]);
                maxY = Math.max(maxY, ys[i]);
                maxZ = Math.max(maxZ, zs[i]);
            }
            return new Box(minX, minY, minZ, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        }

        public int maxX() {
            return minX + sizeX - 1;
        }

        public int maxY() {
            return minY + sizeY - 1;
        }

        public int maxZ() {
            return minZ + sizeZ - 1;
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && y >= minY && z >= minZ && x <= maxX() && y <= maxY() && z <= maxZ();
        }

        public int index(int x, int y, int z) {
            return ((y - minY) * sizeZ + (z - minZ)) * sizeX + (x - minX);
        }

        public boolean fits() {
            return sizeX <= ProjectorBuilder.MAX_SIDE && sizeY <= ProjectorBuilder.MAX_SIDE
                && sizeZ <= ProjectorBuilder.MAX_SIDE
                && (long) sizeX * sizeY * sizeZ <= ProjectorBuilder.MAX_VOLUME;
        }

        public boolean inView(Entity entity) {
            return ProjectorServer.distanceSq(entity, minX, minY, minZ, maxX(), maxY(), maxZ())
                <= VIEW_RANGE * VIEW_RANGE;
        }
    }

    // What a style made of the cells. Header and extra stay null without an animation, and order is null when the
    // style keeps the given order.
    public static class Show {

        public final List<UUID> viewers = new ArrayList<>();
        public final List<EntityPlayerMP> receivers = new ArrayList<>();
        public Start header;
        public byte[] extra;
        public int placingTick;
        public int[] order;
    }

    private ProjectorBroadcast() {}

    public static int[][] coordinates(List<Cell> cells) {
        int[] xs = new int[cells.size()], ys = new int[xs.length], zs = new int[xs.length];
        for (int i = 0; i < xs.length; i++) {
            Cell cell = cells.get(i);
            xs[i] = cell.x();
            ys[i] = cell.y();
            zs[i] = cell.z();
        }
        return new int[][] { xs, ys, zs };
    }

    public static Show prepare(int id, EntityPlayerMP player, List<Cell> cells, int[][] coordinates, Box box,
        Kinds kinds, int originX, int originY, int originZ, int facing, StructureAnimationStyle style,
        boolean reversed) {
        Show show = new Show();
        Vec3 look = player.getLookVec();
        StructureAnimationStyle.Site site = new StructureAnimationStyle.Site(
            player.dimension,
            box.minX(),
            box.minY(),
            box.minZ(),
            box.sizeX(),
            box.sizeY(),
            box.sizeZ(),
            coordinates[0],
            coordinates[1],
            coordinates[2],
            originX,
            originY,
            originZ,
            facing,
            player.posX,
            player.posZ,
            look.xCoord,
            look.zCoord);
        ByteBuf extra = Unpooled.buffer();
        show.placingTick = style.prepare(site, extra);
        if (!reversed) show.order = style.order(site);
        if (style == StructureAnimationStyle.NONE) return show;
        show.extra = new byte[extra.readableBytes()];
        extra.readBytes(show.extra);
        IntArrayList frontCells = new IntArrayList();
        ByteArrayList fronts = new ByteArrayList();
        for (Cell cell : cells) {
            if (cell.front() < 0) continue;
            frontCells.add(box.index(cell.x(), cell.y(), cell.z()));
            fronts.add(cell.front());
        }
        show.header = new Start(
            id,
            player.dimension,
            box.minX(),
            box.minY(),
            box.minZ(),
            box.sizeX(),
            box.sizeY(),
            box.sizeZ(),
            kinds.deflated(),
            kinds.items(),
            kinds.damages(),
            frontCells.toIntArray(),
            fronts.toByteArray(),
            originX,
            originY,
            originZ,
            facing,
            style,
            cells.size(),
            show.placingTick,
            false,
            0,
            reversed);
        for (Object entry : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (!(entry instanceof EntityPlayerMP other) || other.dimension != player.dimension) continue;
            if (other != player && !box.inView(other)) continue;
            show.viewers.add(other.getUniqueID());
            show.receivers.add(other);
        }
        return show;
    }

    public static void begin(Show show, EntityPlayerMP owner) {
        for (EntityPlayerMP receiver : show.receivers)
            sendStart(show.header, show.extra, receiver, receiver == owner, 0);
    }

    public static void sendStart(Start header, byte[] extra, EntityPlayerMP receiver, boolean own, int ticks) {
        ScienceNotLeisure.network.sendTo(new StructureAnimationStartPacket(header.as(own, ticks), extra), receiver);
    }

    public static void send(List<UUID> viewers, StructureAnimationStatePacket packet) {
        for (UUID id : viewers) {
            EntityPlayerMP viewer = ProjectorServer.playerOf(id);
            if (viewer != null) ScienceNotLeisure.network.sendTo(packet, viewer);
        }
    }

    // One code per cell of the box (0 = not built), deflated; the code only tells kinds apart for the outline. Null
    // when it would not fit in the start packet.
    public static Kinds kindsOf(List<Cell> cells, Box box) {
        Long2IntOpenHashMap palette = new Long2IntOpenHashMap();
        IntArrayList items = new IntArrayList(), damages = new IntArrayList();
        short[] grid = new short[box.sizeX() * box.sizeY() * box.sizeZ()];
        for (Cell cell : cells) {
            int item = Item.getIdFromItem(
                cell.stack()
                    .getItem()),
                damage = cell.stack()
                    .getItemDamage() & 0xFFFF;
            int code = palette.computeIfAbsent(((long) item << 16) | damage, k -> {
                items.add(item);
                damages.add(damage);
                return Math.min(items.size(), 0xFFFF);
            });
            grid[box.index(cell.x(), cell.y(), cell.z())] = (short) code;
        }
        byte[] raw = new byte[grid.length * 2];
        for (int i = 0; i < grid.length; i++) {
            raw[i * 2] = (byte) (grid[i] >> 8);
            raw[i * 2 + 1] = (byte) grid[i];
        }
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 32));
        byte[] buffer = new byte[65536];
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer));
        deflater.end();
        byte[] deflated = out.toByteArray();
        return deflated.length > MAX_START_BYTES ? null : new Kinds(deflated, items.toIntArray(), damages.toIntArray());
    }
}
