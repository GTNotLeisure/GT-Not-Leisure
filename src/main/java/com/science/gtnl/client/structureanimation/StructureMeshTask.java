package com.science.gtnl.client.structureanimation;

import java.util.zip.DataFormatException;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.IAlignment;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnllib.client.fx.world.FxBlockMesh;
import com.gtnllib.client.fx.world.FxCameraShake;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.packet.StructureAnimationStartPacket.Start;
import com.science.gtnl.common.projector.ProjectorSimulation;
import com.science.gtnl.common.projector.ProjectorSimulationWorld;
import com.science.gtnl.config.ClientConfig;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * A build animation that draws the structure from a private copy of the world and hands it to the real blocks section
 * by section once the server has placed them. Subclasses give the timeline, the transforms and the effects.
 */
@SideOnly(Side.CLIENT)
public abstract class StructureMeshTask extends StructureAnimationTask {

    public static final int HANDOFF_WAIT = 2, HANDOFF_REBUILD = 6, HANDOFF_FALLBACK = 40;
    public static final double SHAKE_REACH = 48;

    public final int offsetX, offsetY, offsetZ;
    public final double centerX, centerZ, radius;
    public final LongOpenHashSet handed = new LongOpenHashSet();
    public final Long2IntOpenHashMap sectionLast = new Long2IntOpenHashMap(), matchedCells = new Long2IntOpenHashMap();
    public final Long2ObjectOpenHashMap<IntArrayList> sectionCells = new Long2ObjectOpenHashMap<>();
    public final Long2LongOpenHashMap coveredAt = new Long2LongOpenHashMap(), matchedAt = new Long2LongOpenHashMap();
    public final Int2ByteOpenHashMap fronts = new Int2ByteOpenHashMap();
    public final int[] wx, wy, wz, code;
    public int[] placeOrder;
    public int placedInCopy;
    public boolean forwarded;
    public ProjectorSimulationWorld copy;
    public ProjectorSimulation.Actor actor;

    protected StructureMeshTask(Start start) {
        super(start);
        short[] kinds = kinds(start);
        offsetX = start.originX() & ~15;
        offsetY = start.originY() & ~15;
        offsetZ = start.originZ() & ~15;
        centerX = start.originX() + start.sizeX() / 2.0;
        centerZ = start.originZ() + start.sizeZ() / 2.0;
        radius = Math.hypot(start.sizeX(), start.sizeZ()) / 2 + 0.25;
        for (int i = 0; i < start.frontCells().length; i++) fronts.put(start.frontCells()[i], start.fronts()[i]);
        sectionLast.defaultReturnValue(-1);
        int count = 0;
        for (short kind : kinds) if (kind != 0) count++;
        wx = new int[count];
        wy = new int[count];
        wz = new int[count];
        code = new int[count];
        int n = 0;
        for (int y = 0; y < start.sizeY(); y++)
            for (int z = 0; z < start.sizeZ(); z++) for (int x = 0; x < start.sizeX(); x++) {
                int kind = kinds[(y * start.sizeZ() + z) * start.sizeX() + x] & 0xFFFF;
                if (kind == 0) continue;
                wx[n] = start.originX() + x;
                wy[n] = start.originY() + y;
                wz[n] = start.originZ() + z;
                code[n] = kind;
                long section = sectionKey(wx[n] - offsetX, wy[n] - offsetY, wz[n] - offsetZ);
                sectionLast.put(section, Math.max(sectionLast.get(section), n));
                sectionCells.computeIfAbsent(section, k -> new IntArrayList())
                    .add(n);
                n++;
            }
        copy = new ProjectorSimulationWorld();
        actor = new ProjectorSimulation.Actor(copy);
    }

    public static long sectionKey(int x, int y, int z) {
        return ((long) (x >> 4) & 0x1FFFFF) << 42 | ((long) (y >> 4) & 0x1FFFFF) << 21 | ((long) (z >> 4) & 0x1FFFFF);
    }

    private static short[] kinds(Start start) {
        try {
            return StructureRevealShape.inflate(start.kinds(), start.sizeX() * start.sizeY() * start.sizeZ());
        } catch (DataFormatException failure) {
            throw new IllegalArgumentException("Projector build shape could not be read", failure);
        }
    }

    /** Advances the timeline and effects; called every client tick after the copy has grown. */
    protected abstract void animate(Minecraft minecraft, double age);

    /** Called after the cell with this index has been placed into the copy, in placement order. */
    protected abstract void placedInCopy(int placed);

    /** Whether this section of the copy has reached its final place, so it may give way to the real blocks. */
    protected abstract boolean settled(long section);

    /**
     * Played backwards, forgets the moments the fast-forward remembered once the clock has run back before them, so
     * the timeline shows them as not yet happened.
     */
    protected void rewind() {}

    /** Whether effects that must finish before the task goes away are still running. */
    protected abstract boolean busy();

    /** Frees meshes, sounds and anything else the subclass holds. */
    protected abstract void releaseOwn();

    @Override
    public void release() {
        releaseOwn();
        copy = null;
        actor = null;
    }

    @Override
    public boolean finished() {
        if (reversed) return reverseDone();
        boolean allHanded = handed.size() >= sectionLast.size();
        return doneTick >= 0 && (allHanded || now() - doneTick > HANDOFF_FALLBACK + HANDOFF_REBUILD) && !busy();
    }

    // Played backwards, the timeline first runs forwards once without effects, so every moment the styles remember
    // (an impact, a lock) is set before the clock turns round.
    @Override
    protected void tick(Minecraft minecraft) {
        if (reversed && !forwarded) {
            forwarded = true;
            for (forwarding = 0; forwarding <= length(); forwarding++) animate(minecraft, forwarding);
            forwarding = -1;
        }
        if (reversed) rewind();
        buildCopy();
        animate(minecraft, age(0));
        if (!reversed) handoff(minecraft.theWorld);
    }

    // Skipped cells never get a real block, so their sections hand over without waiting for them.
    @Override
    public void drop(int[] boxCells) {
        LongOpenHashSet dropped = new LongOpenHashSet(boxCells.length);
        for (int boxCell : boxCells) dropped.add(boxCell);
        for (Long2ObjectOpenHashMap.Entry<IntArrayList> entry : sectionCells.long2ObjectEntrySet()) {
            IntArrayList cells = entry.getValue();
            int kept = 0;
            for (int c = 0; c < cells.size(); c++) {
                int i = cells.getInt(c);
                long boxCell = ((long) (wy[i] - start.originY()) * start.sizeZ() + (wz[i] - start.originZ()))
                    * start.sizeX() + (wx[i] - start.originX());
                if (!dropped.contains(boxCell)) cells.set(kept++, i);
            }
            if (kept == cells.size()) continue;
            cells.size(kept);
            matchedCells.put(entry.getLongKey(), 0);
        }
    }

    public FxBlockMesh mesh(int[] cells) {
        int[] xs = new int[cells.length], ys = new int[cells.length], zs = new int[cells.length];
        for (int c = 0; c < cells.length; c++) {
            int i = cells[c];
            xs[c] = wx[i] - offsetX;
            ys[c] = wy[i] - offsetY;
            zs[c] = wz[i] - offsetZ;
        }
        FxBlockMesh mesh = new FxBlockMesh();
        mesh.prepare(mesh, copy, xs, ys, zs);
        mesh.show(null);
        return mesh;
    }

    public void drawMesh(FxBlockMesh mesh) {
        mesh.compile(StructureAnimations.compileBudget(), this::placed);
        mesh.draw(this::visible);
    }

    public boolean visible(FxBlockMesh.Section section) {
        return !handed.contains(sectionKey(section.x, section.y, section.z));
    }

    // Mesh cells are listed in placement order, so a part may be compiled once its last cell is in the copy.
    public boolean placed(FxBlockMesh.Section section) {
        return placeOrder != null || section.cells.getInt(section.cells.size() - 1) < placedInCopy;
    }

    private void buildCopy() {
        if (copy == null || placedInCopy >= code.length) return;
        ProjectorSimulation.locked(() -> {
            long deadline = System.nanoTime() + StructureAnimations.PLACE_BUDGET_NANOS;
            while (placedInCopy < code.length && System.nanoTime() < deadline) {
                int i = placeOrder == null ? placedInCopy : placeOrder[placedInCopy];
                placedInCopy++;
                placeCell(i);
                placedInCopy(placedInCopy);
            }
        });
    }

    private void placeCell(int i) {
        int kind = code[i] - 1;
        if (kind < 0 || kind >= start.items().length) return;
        if (!(Item.getItemById(start.items()[kind]) instanceof ItemBlock item)) return;
        int x = wx[i] - offsetX, y = wy[i] - offsetY, z = wz[i] - offsetZ;
        ItemStack stack = new ItemStack(item, 1, start.damages()[kind]);
        try {
            item.placeBlockAt(
                stack,
                actor,
                copy,
                x,
                y,
                z,
                ForgeDirection.UP.ordinal(),
                0.5f,
                0.5f,
                0.5f,
                item.getMetadata(stack.getItemDamage()));
        } catch (RuntimeException failure) {
            ScienceNotLeisure.LOG.debug("Could not place {} into the animation copy", stack, failure);
            return;
        }
        if (!(copy.getTileEntity(x, y, z) instanceof IGregTechTileEntity tile)) return;
        if (wx[i] == start.controllerX() && wy[i] == start.controllerY() && wz[i] == start.controllerZ()) {
            ExtendedFacing facing = ExtendedFacing.byIndex(start.facing());
            tile.setFrontFacing(facing.getDirection());
            if (tile.getMetaTileEntity() instanceof IAlignment alignment) alignment.setExtendedFacing(facing);
        } else {
            int cell = ((wy[i] - start.originY()) * start.sizeZ() + (wz[i] - start.originZ())) * start.sizeX()
                + (wx[i] - start.originX());
            if (fronts.containsKey(cell)) tile.setFrontFacing(ForgeDirection.getOrientation(fronts.get(cell)));
        }
    }

    // A section gives way once it has settled, its last cell has been placed, the block changes have had time to
    // arrive, and the real blocks are there. The server places the controller and retried cells last, so the fallback
    // for sections that never match only applies once it has reported the end.
    private void handoff(World world) {
        if (world == null) return;
        for (Long2IntOpenHashMap.Entry entry : sectionLast.long2IntEntrySet()) {
            long section = entry.getLongKey();
            if (handed.contains(section) || processed <= entry.getIntValue() || !settled(section)) continue;
            long covered = coveredAt.containsKey(section) ? coveredAt.get(section) : now();
            coveredAt.put(section, covered);
            if (now() - covered < HANDOFF_WAIT) continue;
            if (!matchedAt.containsKey(section) && sectionMatches(world, section)) matchedAt.put(section, now());
            boolean rebuilt = matchedAt.containsKey(section) && now() - matchedAt.get(section) >= HANDOFF_REBUILD;
            if (rebuilt || doneTick >= 0 && now() - Math.max(covered, doneTick) >= HANDOFF_FALLBACK)
                handed.add(section);
        }
    }

    private boolean sectionMatches(World world, long section) {
        IntArrayList cells = sectionCells.get(section);
        if (cells == null) return true;
        // Resumes at the first cell that did not match last time instead of scanning the section again every tick.
        for (int c = matchedCells.get(section); c < cells.size(); c++) {
            int i = cells.getInt(c);
            int kind = code[i] - 1;
            if (kind < 0 || kind >= start.items().length) continue;
            if (!(Item.getItemById(start.items()[kind]) instanceof ItemBlock item)) continue;
            if (world.getBlock(wx[i], wy[i], wz[i]) != item.field_150939_a) {
                matchedCells.put(section, c);
                return false;
            }
        }
        return true;
    }

    public void sound(double x, double y, double z, String name, float volume, float pitch) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null || muted()) return;
        float reach = (float) Math.max(1, radius / 16);
        minecraft.theWorld.playSound(x, y, z, name, volume * reach, pitch, false);
    }

    public void shake(FxCameraShake.Source source, float strength, float rumble) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || !ClientConfig.structureLandingShake || muted()) return;
        double dx = gap(minecraft.thePlayer.posX, start.originX(), start.originX() + start.sizeX());
        double dy = gap(minecraft.thePlayer.posY, start.originY(), start.originY() + start.sizeY());
        double dz = gap(minecraft.thePlayer.posZ, start.originZ(), start.originZ() + start.sizeZ());
        double falloff = 1 - Math.sqrt(dx * dx + dy * dy + dz * dz) / (radius + SHAKE_REACH);
        if (falloff <= 0) return;
        source.kick(strength * (float) falloff);
        source.rumble(strength * (float) falloff * rumble);
    }

    public static double gap(double value, double min, double max) {
        return value < min ? min - value : value > max ? value - max : 0;
    }

    public static int particles(int desired) {
        int setting = Minecraft.getMinecraft().gameSettings.particleSetting;
        return Math.max(1, desired / (setting == 2 ? 8 : setting == 1 ? 2 : 1));
    }
}
