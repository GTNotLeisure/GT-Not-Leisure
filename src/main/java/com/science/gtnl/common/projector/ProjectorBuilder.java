package com.science.gtnl.common.projector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.world.BlockEvent;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.util.InventoryUtility;
import com.gtnllib.utils.HotbarMessages;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.packet.StructureAnimationStartPacket;
import com.science.gtnl.common.packet.StructureAnimationStartPacket.Start;
import com.science.gtnl.common.packet.StructureAnimationStatePacket;
import com.science.gtnl.common.projector.ProjectorBroadcast.Box;
import com.science.gtnl.common.projector.ProjectorBroadcast.Kinds;
import com.science.gtnl.common.projector.ProjectorBroadcast.Show;
import com.science.gtnl.common.projector.ProjectorServer.Budget;
import com.science.gtnl.common.projector.ProjectorStructure.Choice;
import com.science.gtnl.config.MachineConfig;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.threads.RunnableMachineUpdate;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

public class ProjectorBuilder {

    public record Cell(int cell, int x, int y, int z, ItemStack stack, byte front) {}

    public enum Stage {
        WAITING,
        SIMULATING,
        LOADING,
        SCANNING
    }

    public static final int MAX_CELLS = StructureAnimationStartPacket.MAX_CELLS,
        MAX_SIDE = StructureAnimationStartPacket.MAX_SIDE, HEARTBEAT_TICKS = 20, STALL_TICKS = 200, SHOWN_SHORTAGES = 8,
        SCAN_BATCH = 256, RELEASE_TICKS = 4, BUILT_TICKS = 5, LOADS_PER_TICK = 4, LOAD_TICKS = 200, LEDGER_TICKS = 100,
        DENIED_LIMIT = 256, MAX_VOLUME = 4_000_000;
    public static final int PLACED = 0, EXISTING = 1, SHORT = 2, BLOCKED = 3, REFUSED = 4, FAILED = 5, OCCUPIED = 6,
        UNLOADED = 7, OBSTRUCTED = 8, OUTCOMES = 9;
    public static final int CLEARED = -1;
    private static final Map<UUID, Preparation> PREPARING = new LinkedHashMap<>();
    private static final Map<UUID, Job> JOBS = new HashMap<>();
    private static final Map<UUID, List<Ticket>> TICKETS = new HashMap<>();
    private static int nextJobId;

    public static int nextJobId() {
        return ++nextJobId;
    }

    public static class Preparation {

        public final int dimension;
        public final ProjectorPlan plan;
        public Stage stage = Stage.WAITING;
        public ProjectorSimulation simulation;
        public ProjectorSite site;
        public final IntArrayList candidates = new IntArrayList(), clears = new IntArrayList(),
            turns = new IntArrayList();
        public final LongArrayList chunks = new LongArrayList();
        public final int location;
        public final boolean fromOrder, dismantle;
        public int cursor, existing, occupied, unloaded, ticks, idle, chunkCursor, loading, loadTicks;
        public boolean ended;

        public Preparation(int dimension, ProjectorPlan plan, int location, boolean fromOrder, boolean dismantle) {
            this.dismantle = dismantle;
            this.dimension = dimension;
            this.plan = plan;
            this.location = location;
            this.fromOrder = fromOrder;
        }
    }

    public static class Job {

        public final int id, dimension;
        public final Box box;
        public final UUID owner;
        public final ExtendedFacing facing;
        public final ProjectorSite site;
        public final Cell[] cells;
        public final List<UUID> viewers;
        public final int[] outcomes = new int[OUTCOMES];
        public final IntArrayList skipped = new IntArrayList(), retry = new IntArrayList(),
            controllers = new IntArrayList();
        public final int[] clears, turns;
        public int turnsDone;
        public int next, retried, ticks, denied, controllersPlaced, reportedBuilt = -1, cleared, clearsDone;
        public final int placingTick;
        public final LongOpenHashSet targets, held = new LongOpenHashSet();
        public long[] releasing;
        public int released;
        public Start header;
        public byte[] extra;
        public final ProjectorLedger.Site ledgerSite;
        public final ProjectorLedger.Build record;

        public Job(int id, UUID owner, Preparation preparation, Cell[] cells, Box box, Show show) {
            this.id = id;
            this.owner = owner;
            ledgerSite = ProjectorLedger.Site.of(owner, preparation.plan);
            record = ProjectorLedger.begin(ledgerSite);
            dimension = preparation.dimension;
            site = preparation.site;
            facing = site.facing;
            this.box = box;
            this.cells = cells;
            viewers = show.viewers;
            placingTick = show.placingTick;
            header = show.header;
            extra = show.extra;
            clears = preparation.clears.toIntArray();
            turns = preparation.turns.toIntArray();
            targets = new LongOpenHashSet(cells.length);
            for (Cell cell : cells) targets.add(CoordinatePacker.pack(cell.x(), cell.y(), cell.z()));
        }

        public boolean hiding() {
            return ticks < placingTick;
        }

        public boolean releasedAll() {
            return held.isEmpty() && (releasing == null || released >= releasing.length);
        }

        public boolean placedAll() {
            return turnsDone >= turns.length && clearsDone >= clears.length
                && next >= cells.length
                && retried >= retry.size()
                && controllersPlaced >= controllers.size();
        }

        public int processed() {
            int count = 0;
            for (int outcome : outcomes) count += outcome;
            return count;
        }
    }

    private ProjectorBuilder() {}

    public static void request(EntityPlayerMP player, int location) {
        request(player, location, false);
    }

    public static boolean busy(UUID id) {
        return PREPARING.containsKey(id) || JOBS.containsKey(id);
    }

    public static int running() {
        return JOBS.size() + (PREPARING.isEmpty() ? 0 : 1);
    }

    public static boolean canRequest(UUID id) {
        return !ProjectorServer.busy(id) && ProjectorServer.BUILDS.ready(id);
    }

    public static void request(EntityPlayerMP player, int location, boolean fromOrder) {
        prepare(player, location, fromOrder, false);
    }

    // A dismantle goes through the same simulation, chunk loading and scan as a build, then takes down what matches.
    public static void requestDismantle(EntityPlayerMP player, int location) {
        prepare(player, location, false, true);
    }

    private static void prepare(EntityPlayerMP player, int location, boolean fromOrder, boolean dismantle) {
        if (!MachineConfig.projectorBuildEnabled) {
            ProjectorServer.refuse(player, "disabled");
            return;
        }
        if (!player.isEntityAlive()) return;
        ItemStack projector = ProjectorServer.projectorAt(player, location);
        if (projector == null) return;
        UUID id = player.getUniqueID();
        if (ProjectorServer.busy(id)) {
            ProjectorServer.refuse(player, "busy");
            return;
        }
        if (!ProjectorServer.BUILDS.take(player)) return;
        ProjectorPlan plan = ProjectorPlan.read(projector);
        if (!plan.hasMachine() || !ProjectorMachines.isSupported(plan.machine)
            || !plan.isPlaced()
            || plan.dimension != player.dimension
            || !ProjectorMachines.validFacings(plan.machine)
                .test(plan.facing)) {
            ProjectorServer.refuse(player, "not_placed");
            return;
        }
        if (!dismantle) ProjectorOrders.cancel(player, false);
        PREPARING.put(id, new Preparation(player.dimension, plan, location, fromOrder, dismantle));
        HotbarMessages.send(player, new ChatComponentTranslation(ProjectorServer.LANG + "preparing"));
    }

    // Forge drops its own ticket tables with the worlds, so the tickets are forgotten here rather than released.
    public static void clear() {
        PREPARING.clear();
        ProjectorLedger.touch();
        JOBS.clear();
        TICKETS.clear();
    }

    // Blocks are placed while the animation still plays; their updates reach clients only once it ends, so nobody
    // sees real blocks appear before the animation shows them.
    public static boolean holdUpdate(WorldServer world, int x, int y, int z) {
        if (JOBS.isEmpty()) return false;
        for (Job job : JOBS.values()) {
            if (!job.hiding() || job.dimension != world.provider.dimensionId || !job.box.contains(x, y, z)) continue;
            long position = CoordinatePacker.pack(x, y, z);
            if (!job.targets.contains(position)) continue;
            job.held.add(position);
            return true;
        }
        return false;
    }

    // Spread over a few ticks, chunk by chunk, so clients do not rebuild every chunk of the structure in one frame.
    private static void releaseSome(Job job, boolean all) {
        if (job.releasing == null) {
            Long2ObjectLinkedOpenHashMap<LongArrayList> byChunk = new Long2ObjectLinkedOpenHashMap<>();
            LongIterator held = job.held.iterator();
            while (held.hasNext()) {
                long position = held.nextLong();
                long chunk = ChunkCoordIntPair
                    .chunkXZ2Int(CoordinatePacker.unpackX(position) >> 4, CoordinatePacker.unpackZ(position) >> 4);
                byChunk.computeIfAbsent(chunk, key -> new LongArrayList())
                    .add(position);
            }
            LongArrayList ordered = new LongArrayList(job.held.size());
            for (LongArrayList chunk : byChunk.values()) ordered.addAll(chunk);
            job.releasing = ordered.toLongArray();
            job.held.clear();
        }
        World world = DimensionManager.getWorld(job.dimension);
        int end = all ? job.releasing.length
            : Math.min(job.releasing.length, job.released + (job.releasing.length + RELEASE_TICKS - 1) / RELEASE_TICKS);
        while (job.released < end) {
            long position = job.releasing[job.released++];
            if (world != null) world.markBlockForUpdate(
                CoordinatePacker.unpackX(position),
                CoordinatePacker.unpackY(position),
                CoordinatePacker.unpackZ(position));
        }
    }

    public static void tick(Budget budget) {
        if (!PREPARING.isEmpty()) prepareAll(budget);
        if (!JOBS.isEmpty()) tickJobs(budget);
    }

    // Preparations wait their turn: only the first one in line simulates or scans in a tick.
    private static void prepareAll(Budget budget) {
        long began = System.nanoTime();
        boolean active = false;
        Iterator<Map.Entry<UUID, Preparation>> iterator = PREPARING.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Preparation> entry = iterator.next();
            Preparation preparation = entry.getValue();
            EntityPlayerMP owner = ProjectorServer.playerOf(entry.getKey());
            if (!ProjectorServer.present(owner, preparation.dimension)) {
                iterator.remove();
                continue;
            }
            preparation.ticks++;
            try {
                if (preparation.ticks % HEARTBEAT_TICKS == 0)
                    HotbarMessages.send(owner, new ChatComponentTranslation(ProjectorServer.LANG + "preparing"));
                if (active) continue;
                active = true;
                boolean progressed = step(owner, preparation, budget.share());
                if (preparation.ended) {
                    iterator.remove();
                    continue;
                }
                if (!progressed) {
                    if (++preparation.idle <= STALL_TICKS) continue;
                    iterator.remove();
                    ProjectorServer.refuse(owner, "stalled");
                    continue;
                }
                preparation.idle = 0;
                if (preparation.stage != Stage.SCANNING || preparation.cursor < preparation.site.total()) continue;
                iterator.remove();
                try {
                    if (preparation.dismantle) ProjectorDismantler.fromSite(owner, preparation);
                    else admit(owner, preparation);
                } catch (RuntimeException e) {
                    ScienceNotLeisure.LOG
                        .warn("Projector build preparation for {} failed", owner.getCommandSenderName(), e);
                    ProjectorServer.refuse(owner, "error");
                }
            } catch (RuntimeException e) {
                ScienceNotLeisure.LOG
                    .warn("Projector build preparation for {} failed", owner.getCommandSenderName(), e);
                iterator.remove();
                ProjectorServer.refuse(owner, "error");
            }
        }
        budget.spend(System.nanoTime() - began);
    }

    private static boolean step(EntityPlayerMP owner, Preparation preparation, long budget) {
        long deadline = System.nanoTime() + budget;
        switch (preparation.stage) {
            case WAITING -> {
                ProjectorStructure known = ProjectorSimulation.shared(preparation.plan.settings());
                if (known != null) {
                    simulated(owner, preparation, known);
                    return true;
                }
                preparation.simulation = new ProjectorSimulation(
                    preparation.plan.settings(),
                    ProjectorSimulation.SERVER_BUDGET);
                preparation.stage = Stage.SIMULATING;
                return true;
            }
            case SIMULATING -> {
                int rounds = preparation.simulation.rounds;
                if (!preparation.simulation.step(Math.max(1, deadline - System.nanoTime())))
                    return preparation.simulation.rounds != rounds;
                ProjectorStructure structure = preparation.simulation.result();
                preparation.simulation = null;
                ProjectorSimulation.share(structure);
                simulated(owner, preparation, structure);
                return true;
            }
            case LOADING -> {
                return loadChunks(owner, preparation);
            }
            case SCANNING -> {
                ProjectorSite site = preparation.site;
                World world = owner.worldObj;
                int size = site.total(), before = preparation.cursor;
                do {
                    int end = Math.min(size, preparation.cursor + SCAN_BATCH);
                    for (; preparation.cursor < end; preparation.cursor++) scan(world, preparation, preparation.cursor);
                } while (preparation.cursor < size && System.nanoTime() < deadline);
                return preparation.cursor != before;
            }
            default -> {
                return false;
            }
        }
    }

    // Only stacked copies are held to the build limits here; a single structure keeps being checked when it starts.
    public static boolean fits(ProjectorStructure structure, int[] single, int[] offsets) {
        if (offsets.length <= 3) return true;
        int[] all = ProjectorPlan.stackedBounds(single, offsets);
        long sizeX = all[3] - all[0] + 1, sizeY = all[4] - all[1] + 1, sizeZ = all[5] - all[2] + 1;
        return (long) structure.size * (offsets.length / 3) <= MAX_CELLS && sizeX <= MAX_SIDE
            && sizeY <= MAX_SIDE
            && sizeZ <= MAX_SIDE
            && sizeX * sizeY * sizeZ <= MAX_VOLUME;
    }

    private static void simulated(EntityPlayerMP owner, Preparation preparation, ProjectorStructure structure) {
        preparation.stage = Stage.SCANNING;
        ProjectorPlan plan = preparation.plan;
        int[] single = structure.worldBounds(plan.facing);
        int[] offsets = plan.stackOffsets(single);
        if (!structure.failure.isEmpty() || structure.isEmpty()) {
            ProjectorServer.refuse(owner, "failed");
            preparation.ended = true;
            return;
        }
        if (!fits(structure, single, offsets)) {
            ProjectorServer.refuse(owner, "too_big");
            preparation.ended = true;
            return;
        }
        preparation.site = new ProjectorSite(structure, plan.facing, plan.x, plan.y, plan.z, offsets);
        int range = MachineConfig.projectorBuildRange;
        int[] bounds = ProjectorPlan.stackedBounds(single, offsets);
        if (range > 0 && ProjectorServer.distanceSq(
            owner,
            plan.x + bounds[0],
            plan.y + bounds[1],
            plan.z + bounds[2],
            plan.x + bounds[3],
            plan.y + bounds[4],
            plan.z + bounds[5]) > (double) range * range) {
            ProjectorServer.refuse(owner, "too_far", range);
            preparation.ended = true;
            return;
        }
        IChunkProvider provider = owner.worldObj.getChunkProvider();
        for (int chunkX = (plan.x + bounds[0]) >> 4; chunkX <= (plan.x + bounds[3]) >> 4; chunkX++) {
            for (int chunkZ = (plan.z + bounds[2]) >> 4; chunkZ <= (plan.z + bounds[5]) >> 4; chunkZ++) {
                if (!provider.chunkExists(chunkX, chunkZ))
                    preparation.chunks.add(ChunkCoordIntPair.chunkXZ2Int(chunkX, chunkZ));
            }
        }
        if (!preparation.chunks.isEmpty()) preparation.stage = Stage.LOADING;
    }

    public static void registerChunkLoading() {
        ForgeChunkManager.setForcedChunkLoadingCallback(
            ScienceNotLeisure.instance,
            (tickets, world) -> { for (Ticket ticket : tickets) ForgeChunkManager.releaseTicket(ticket); });
    }

    // Chunks of the site nobody keeps loaded are loaded off the main thread a few at a time and held by tickets until
    // the build ends; a chunk that never loads in time is left to count as unloaded.
    private static boolean loadChunks(EntityPlayerMP owner, Preparation preparation) {
        WorldServer world = (WorldServer) owner.worldObj;
        int started = 0;
        while (preparation.chunkCursor < preparation.chunks.size() && started < LOADS_PER_TICK) {
            long packed = preparation.chunks.getLong(preparation.chunkCursor++);
            int chunkX = (int) packed, chunkZ = (int) (packed >> 32);
            Ticket ticket = ticketFor(owner);
            if (ticket != null) ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(chunkX, chunkZ));
            preparation.loading++;
            started++;
            world.theChunkProviderServer.loadChunk(chunkX, chunkZ, () -> preparation.loading--);
        }
        boolean done = preparation.chunkCursor >= preparation.chunks.size() && preparation.loading <= 0;
        if (done || ++preparation.loadTicks > LOAD_TICKS) preparation.stage = Stage.SCANNING;
        return true;
    }

    private static Ticket ticketFor(EntityPlayerMP owner) {
        List<Ticket> tickets = TICKETS.computeIfAbsent(owner.getUniqueID(), id -> new ArrayList<>());
        if (!tickets.isEmpty()) {
            Ticket last = tickets.get(tickets.size() - 1);
            if (last.getChunkList()
                .size() < last.getChunkListDepth()) return last;
        }
        Ticket ticket = ForgeChunkManager.requestTicket(ScienceNotLeisure.instance, owner.worldObj, Type.NORMAL);
        if (ticket != null) tickets.add(ticket);
        return ticket;
    }

    // Runs every tick, so a player's tickets go as soon as none of their builds or dismantles is left.
    public static void releaseChunks() {
        if (TICKETS.isEmpty()) return;
        Iterator<Map.Entry<UUID, List<Ticket>>> iterator = TICKETS.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, List<Ticket>> entry = iterator.next();
            if (ProjectorServer.busy(entry.getKey())) continue;
            for (Ticket ticket : entry.getValue()) ForgeChunkManager.releaseTicket(ticket);
            iterator.remove();
        }
    }

    // A wrong block where one is to be placed is cleared just before placing; one where only air may be is cleared on
    // its own.
    private static void scan(World world, Preparation preparation, int cell) {
        ProjectorSite site = preparation.site;
        if (site.isShadowed(cell)) return;
        if (preparation.dismantle) {
            if (site.holdsStructure(world, cell)) preparation.candidates.add(cell);
            return;
        }
        ItemStack stack = site.isBlock(cell) ? site.structure.items[site.local(cell)] : null;
        byte state = site.compute(world, cell);
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) {
            if (state == ProjectorSite.WRONG) preparation.clears.add(cell);
            return;
        }
        switch (state) {
            case ProjectorSite.CORRECT -> preparation.existing++;
            case ProjectorSite.MISSING -> preparation.candidates.add(cell);
            case ProjectorSite.WRONG -> {
                if (site.facesWrong(world, cell)) {
                    preparation.turns.add(cell);
                    break;
                }
                preparation.candidates.add(cell);
                preparation.occupied++;
            }
            case ProjectorSite.UNLOADED -> preparation.unloaded++;
            default -> {}
        }
    }

    private static void admit(EntityPlayerMP owner, Preparation preparation) {
        ProjectorSite site = preparation.site;
        ItemStack[] items = site.structure.items;
        int[] order = preparation.candidates.toIntArray();
        if (!fitsAnimation(site, order)) {
            ProjectorServer.refuse(owner, "too_big");
            return;
        }
        IntArrays.quickSort(order, (int first, int second) -> {
            int byY = Integer.compare(site.worldY[first], site.worldY[second]);
            if (byY != 0) return byY;
            int byZ = Integer.compare(site.worldZ[first], site.worldZ[second]);
            return byZ != 0 ? byZ : Integer.compare(site.worldX[first], site.worldX[second]);
        });
        Int2IntLinkedOpenHashMap wanted = new Int2IntLinkedOpenHashMap();
        for (int cell : order) wanted.addTo(kindOf(items[site.local(cell)]), 1);
        boolean free = owner.capabilities.isCreativeMode;
        Int2IntOpenHashMap available = new Int2IntOpenHashMap();
        Int2ObjectOpenHashMap<ItemStack> stacks = new Int2ObjectOpenHashMap<>();
        for (int cell : order) stacks.putIfAbsent(kindOf(items[site.local(cell)]), items[site.local(cell)]);
        for (Int2IntLinkedOpenHashMap.Entry entry : wanted.int2IntEntrySet()) {
            int kind = entry.getIntKey(), count = entry.getIntValue();
            available.put(
                kind,
                free ? count
                    : InventoryUtility.takeFromInventory(owner, ProjectorBlocks.single(stacks.get(kind), count), true));
        }
        IntArrayList admitted = new IntArrayList();
        Int2IntLinkedOpenHashMap shortage = new Int2IntLinkedOpenHashMap();
        for (int cell : order) {
            int kind = kindOf(items[site.local(cell)]);
            if (available.get(kind) > 0) {
                available.addTo(kind, -1);
                admitted.add(cell);
            } else {
                shortage.addTo(kind, 1);
            }
        }
        int shortCells = 0;
        List<ItemStack> missing = new ArrayList<>();
        for (Int2IntLinkedOpenHashMap.Entry entry : shortage.int2IntEntrySet()) {
            shortCells += entry.getIntValue();
            missing.add(ProjectorBlocks.single(stacks.get(entry.getIntKey()), entry.getIntValue()));
        }
        owner.addChatMessage(
            new ChatComponentTranslation(
                ProjectorServer.LANG + "summary",
                admitted.size(),
                preparation.existing,
                shortCells,
                preparation.occupied + preparation.clears.size(),
                preparation.unloaded));
        for (int i = 0; i < missing.size() && i < SHOWN_SHORTAGES; i++) {
            ItemStack stack = missing.get(i);
            owner.addChatMessage(
                new ChatComponentTranslation(
                    ProjectorServer.LANG + "short_item",
                    stack.func_151000_E(),
                    stack.stackSize));
        }
        if (missing.size() > SHOWN_SHORTAGES) owner.addChatMessage(
            new ChatComponentTranslation(ProjectorServer.LANG + "short_more", missing.size() - SHOWN_SHORTAGES));
        if (!missing.isEmpty()) owner.addChatMessage(new ChatComponentTranslation(ProjectorServer.LANG + "short_note"));
        // An order waits for the crafts and builds everything at once; a build that an order started does not order
        // again, so materials the network fails to make end in a partial build instead of an endless wait.
        if (!missing.isEmpty() && !preparation.fromOrder && ProjectorOrders.place(owner, preparation.location, missing))
            return;
        if (admitted.isEmpty()) {
            if (preparation.clears.isEmpty() && preparation.turns.isEmpty()) ProjectorServer.refuse(owner, "nothing");
            else clearOnly(owner, preparation);
            return;
        }
        start(owner, preparation, admitted);
    }

    // Checked before any order goes out, so a structure too big to animate is not crafted for first.
    private static boolean fitsAnimation(ProjectorSite site, int[] cells) {
        if (cells.length == 0) return true;
        if (cells.length > MAX_CELLS) return false;
        int[] xs = new int[cells.length], ys = new int[cells.length], zs = new int[cells.length];
        for (int i = 0; i < cells.length; i++) {
            xs[i] = site.worldX[cells[i]];
            ys[i] = site.worldY[cells[i]];
            zs[i] = site.worldZ[cells[i]];
        }
        return Box.of(xs, ys, zs)
            .fits();
    }

    // Nothing left to place, so the obstacles go at once without a job or an animation.
    private static void clearOnly(EntityPlayerMP owner, Preparation preparation) {
        ProjectorSite site = preparation.site;
        for (int i = 0; i < preparation.turns.size(); i++) turn(owner, site, preparation.turns.getInt(i));
        int cleared = 0, kept = 0;
        for (int i = 0; i < preparation.clears.size(); i++) {
            int cell = preparation.clears.getInt(i);
            if (ProjectorBlocks.clear(owner, site.worldX[cell], site.worldY[cell], site.worldZ[cell]) == CLEARED)
                cleared++;
            else kept++;
        }
        HotbarMessages.send(owner, new ChatComponentTranslation(ProjectorServer.LANG + "cleared", cleared, kept));
    }

    private static void turn(EntityPlayerMP owner, ProjectorSite site, int index) {
        World world = owner.worldObj;
        int x = site.worldX[index], y = site.worldY[index], z = site.worldZ[index];
        if (!site.facesWrong(world, index) || !world.canMineBlock(owner, x, y, z)
            || !(world.getTileEntity(x, y, z) instanceof IGregTechTileEntity tile)) return;
        ProjectorBlocks.face(tile, site.facing);
    }

    private static int clearSafely(EntityPlayerMP player, Job job, int cell) {
        ProjectorSite site = job.site;
        int x = site.worldX[cell], y = site.worldY[cell], z = site.worldZ[cell];
        try {
            if (site.compute(player.worldObj, cell) != ProjectorSite.WRONG) return EXISTING;
            return ProjectorBlocks.clear(player, x, y, z);
        } catch (RuntimeException e) {
            ScienceNotLeisure.LOG.warn("Projector build {} could not clear {} {} {}", job.id, x, y, z, e);
            return FAILED;
        }
    }

    private static int kindOf(ItemStack stack) {
        return Choice.idOf(stack.getItem(), stack.getItemDamage());
    }

    private static void start(EntityPlayerMP player, Preparation preparation, IntArrayList admitted) {
        ProjectorSite site = preparation.site;
        List<Cell> sorted = new ArrayList<>(admitted.size());
        for (int i = 0; i < admitted.size(); i++) {
            int cell = admitted.getInt(i);
            sorted.add(
                new Cell(
                    cell,
                    site.worldX[cell],
                    site.worldY[cell],
                    site.worldZ[cell],
                    site.structure.items[site.local(cell)],
                    site.frontOf(cell)));
        }
        int[][] coordinates = ProjectorBroadcast.coordinates(sorted);
        Box box = Box.of(coordinates[0], coordinates[1], coordinates[2]);
        Kinds kinds = sorted.size() > MAX_CELLS || !box.fits() ? null : ProjectorBroadcast.kindsOf(sorted, box);
        if (kinds == null) {
            ProjectorServer.refuse(player, "too_big");
            return;
        }
        int id = nextJobId();
        Show show = ProjectorBroadcast.prepare(
            id,
            player,
            sorted,
            coordinates,
            box,
            kinds,
            site.originX,
            site.originY,
            site.originZ,
            site.facing.getIndex(),
            preparation.plan.buildStyle,
            false);
        Cell[] cells = sorted.toArray(new Cell[0]);
        if (show.order != null) for (int i = 0; i < show.order.length; i++) cells[i] = sorted.get(show.order[i]);
        JOBS.put(player.getUniqueID(), new Job(id, player.getUniqueID(), preparation, cells, box, show));
        if (show.header != null) ProjectorBroadcast.begin(show, player);
    }

    private static void finish(Job job, int state) {
        releaseSome(job, true);
        ProjectorLedger.end(job.ledgerSite, job.record);
        World world = DimensionManager.getWorld(job.dimension);
        ProjectorSite site = job.site;
        for (int copy = 0; world != null && copy < site.copies(); copy++) {
            int x = site.originX + site.offsets[copy * 3], y = site.originY + site.offsets[copy * 3 + 1],
                z = site.originZ + site.offsets[copy * 3 + 2];
            if (world.blockExists(x, y, z)) GregTechAPI.causeMachineUpdate(world, x, y, z);
        }
        ProjectorBroadcast.send(
            job.viewers,
            new StructureAnimationStatePacket(
                job.id,
                state,
                job.next,
                state == StructureAnimationStatePacket.DONE ? job.skipped.toIntArray() : new int[0]));
        EntityPlayerMP owner = ProjectorServer.playerOf(job.owner);
        if (owner == null || job.processed() == 0) return;
        int[] outcomes = job.outcomes;
        owner.addChatMessage(
            new ChatComponentTranslation(
                ProjectorServer.LANG + "result",
                outcomes[PLACED],
                outcomes[SHORT],
                outcomes[BLOCKED],
                outcomes[REFUSED],
                outcomes[FAILED],
                outcomes[OCCUPIED],
                outcomes[UNLOADED],
                outcomes[EXISTING],
                outcomes[OBSTRUCTED],
                job.cleared));
    }

    // Players who come into range after the build started still get the animation, from where it stands now.
    private static void admitViewers(Job job, EntityPlayerMP owner) {
        if (job.header == null) return;
        for (Object entry : owner.worldObj.playerEntities) {
            if (!(entry instanceof EntityPlayerMP other) || job.viewers.contains(other.getUniqueID())
                || !job.box.inView(other)) continue;
            job.viewers.add(other.getUniqueID());
            ProjectorBroadcast.sendStart(job.header, job.extra, other, false, job.ticks);
            if (job.ticks < job.placingTick) continue;
            ScienceNotLeisure.network
                .sendTo(new StructureAnimationStatePacket(job.id, StructureAnimationStatePacket.PLACING, 0), other);
        }
    }

    private static void tickJobs(Budget budget) {
        Iterator<Map.Entry<UUID, Job>> iterator = JOBS.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Job> entry = iterator.next();
            Job job = entry.getValue();
            EntityPlayerMP owner = ProjectorServer.playerOf(entry.getKey());
            if (!ProjectorServer.present(owner, job.dimension)) {
                iterator.remove();
                try {
                    finish(job, StructureAnimationStatePacket.ABORTED);
                } catch (RuntimeException e) {
                    ScienceNotLeisure.LOG.warn("Projector build {} could not report its end", job.id, e);
                }
                continue;
            }
            try {
                job.ticks++;
                if (job.ticks % HEARTBEAT_TICKS == 0) admitViewers(job, owner);
                if (job.ticks % LEDGER_TICKS == 0) ProjectorLedger.touch();
                boolean hiding = job.hiding(), released = !hiding && job.releasedAll();
                if (hiding) {
                    // Keeps clients waiting through a long animation from timing the job out.
                    if (job.ticks % HEARTBEAT_TICKS == 0) ProjectorBroadcast.send(
                        job.viewers,
                        new StructureAnimationStatePacket(job.id, StructureAnimationStatePacket.PROGRESS, 0));
                } else {
                    if (job.ticks == job.placingTick) ProjectorBroadcast.send(
                        job.viewers,
                        new StructureAnimationStatePacket(job.id, StructureAnimationStatePacket.PLACING, 0));
                    releaseSome(job, false);
                }
                // Blocks marked last tick went out to the players during this tick, so a cell counts as sent only once
                // every held block was marked before this tick; the job therefore ends one tick after its last block.
                int sent = released ? job.next : 0;
                long share = budget.share();
                reportBuilt(job, owner);
                if (job.placedAll()) {
                    if (hiding) continue;
                    if (!released) {
                        ProjectorBroadcast.send(
                            job.viewers,
                            new StructureAnimationStatePacket(job.id, StructureAnimationStatePacket.PROGRESS, sent));
                        continue;
                    }
                    iterator.remove();
                    end(job, owner, StructureAnimationStatePacket.DONE, "done", job.outcomes[PLACED]);
                    continue;
                }
                budget.spend(placeSome(owner, job, share));
                if (job.denied >= DENIED_LIMIT) {
                    iterator.remove();
                    end(job, owner, StructureAnimationStatePacket.ABORTED, "denied");
                    continue;
                }
                if (hiding) continue;
                ProjectorBroadcast.send(
                    job.viewers,
                    new StructureAnimationStatePacket(job.id, StructureAnimationStatePacket.PROGRESS, sent));
            } catch (RuntimeException e) {
                ScienceNotLeisure.LOG.warn("Projector build {} stopped", job.id, e);
                iterator.remove();
                end(job, owner, StructureAnimationStatePacket.ABORTED, "error");
            }
        }
    }

    private static void reportBuilt(Job job, EntityPlayerMP owner) {
        int built = job.processed();
        if (built == job.reportedBuilt || job.ticks % BUILT_TICKS != 0 && !job.placedAll()) return;
        job.reportedBuilt = built;
        // Without an animation the client has no progress bar to feed, so the count goes above the hotbar instead.
        if (job.header == null) HotbarMessages
            .send(owner, new ChatComponentTranslation(ProjectorServer.LANG + "progress", built, job.cells.length));
        else ScienceNotLeisure.network
            .sendTo(new StructureAnimationStatePacket(job.id, StructureAnimationStatePacket.BUILT, built), owner);
    }

    // What is already placed stays; the rest of the build is dropped.
    public static boolean cancel(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        if (PREPARING.remove(id) != null) return true;
        Job job = JOBS.remove(id);
        if (job == null) return false;
        end(job, player, StructureAnimationStatePacket.ABORTED, "cancelled", job.outcomes[PLACED]);
        return true;
    }

    // The job has already left the table, so nothing thrown while reporting its end may reach the tick loop.
    private static void end(Job job, EntityPlayerMP owner, int state, String message, Object... args) {
        try {
            finish(job, state);
            if (state == StructureAnimationStatePacket.DONE)
                HotbarMessages.send(owner, new ChatComponentTranslation(ProjectorServer.LANG + message, args));
            else ProjectorServer.refuse(owner, message, args);
        } catch (RuntimeException e) {
            ScienceNotLeisure.LOG.warn("Projector build {} could not report its end", job.id, e);
        }
    }

    // GT walks every connected machine block once per placed casing at the end of the tick; stop that while placing
    // and update the controller once when the job ends. The controller itself goes last, so the machine does not try
    // to form before the rest is there.
    private static long placeSome(EntityPlayerMP owner, Job job, long budget) {
        long began = System.nanoTime(), deadline = began + budget;
        boolean updates = RunnableMachineUpdate.isCurrentThreadEnabled();
        RunnableMachineUpdate.setCurrentThreadEnabled(false);
        try {
            while (!job.placedAll() && System.nanoTime() < deadline) {
                if (job.turnsDone < job.turns.length) {
                    turn(owner, job.site, job.turns[job.turnsDone++]);
                    continue;
                }
                if (job.clearsDone < job.clears.length) {
                    int cell = job.clears[job.clearsDone++];
                    if (clearSafely(owner, job, cell) == CLEARED) job.cleared++;
                    continue;
                }
                int index;
                boolean first = job.next < job.cells.length;
                if (first) {
                    index = job.next++;
                    Cell cell = job.cells[index];
                    if (job.site.isController(cell.x(), cell.y(), cell.z())) {
                        job.controllers.add(index);
                        continue;
                    }
                } else if (job.hiding()) {
                    // Retries and the controller wait for the animation to end; a player standing in the site may
                    // have walked away by then.
                    break;
                } else if (job.retried < job.retry.size()) {
                    index = job.retry.getInt(job.retried++);
                } else {
                    index = job.controllers.getInt(job.controllersPlaced++);
                }
                Cell cell = job.cells[index];
                int outcome = placeSafely(owner, job, cell);
                // A block that needs support may only fit once its neighbours are there, so it gets one more try after
                // everything else.
                if (outcome == OBSTRUCTED && first) {
                    job.retry.add(index);
                    continue;
                }
                if (outcome == PLACED) job.denied = 0;
                else if (outcome == REFUSED || outcome == BLOCKED) job.denied++;
                job.outcomes[outcome]++;
                if (outcome != PLACED) job.skipped.add(job.box.index(cell.x(), cell.y(), cell.z()));
                if (job.denied >= DENIED_LIMIT) break;
            }
        } finally {
            RunnableMachineUpdate.setCurrentThreadEnabled(updates);
        }
        return System.nanoTime() - began;
    }

    private static int placeSafely(EntityPlayerMP player, Job job, Cell cell) {
        try {
            return place(player, job, cell);
        } catch (RuntimeException e) {
            ScienceNotLeisure.LOG.warn(
                "Projector build {} could not place {} at {} {} {}",
                job.id,
                cell.stack(),
                cell.x(),
                cell.y(),
                cell.z(),
                e);
            return FAILED;
        }
    }

    // A block stays only once its item has been taken from the player, and the item is only taken for a block that
    // stays: every way out before the final take puts the captured blocks back. Whatever stands in the way only goes
    // once the item is known to be there.
    private static int place(EntityPlayerMP player, Job job, Cell cell) {
        World world = player.worldObj;
        int x = cell.x(), y = cell.y(), z = cell.z();
        byte state = job.site.compute(world, cell.cell());
        if (state == ProjectorSite.CORRECT) return EXISTING;
        if (state == ProjectorSite.UNLOADED) return UNLOADED;
        ItemBlock item = (ItemBlock) cell.stack()
            .getItem();
        int damage = cell.stack()
            .getItemDamage();
        ItemStack stack = ProjectorBlocks.single(cell.stack(), 1);
        boolean free = player.capabilities.isCreativeMode;
        if (!free && InventoryUtility.takeFromInventory(player, ProjectorBlocks.single(stack, 1), true) < 1)
            return SHORT;
        if (state != ProjectorSite.MISSING) {
            int cleared = ProjectorBlocks.clear(player, x, y, z);
            if (cleared != CLEARED) return cleared;
            job.cleared++;
        }
        Block block = item.field_150939_a;
        if (!world.canMineBlock(player, x, y, z) || !player.canPlayerEdit(x, y, z, ForgeDirection.UP.ordinal(), stack))
            return BLOCKED;
        if (!world.canPlaceEntityOnSide(block, x, y, z, false, ForgeDirection.UP.ordinal(), null, stack))
            return OBSTRUCTED;
        List<BlockSnapshot> snapshots = new ArrayList<>();
        boolean keep = false;
        try {
            boolean placed;
            world.captureBlockSnapshots = true;
            try {
                placed = item.placeBlockAt(
                    stack,
                    player,
                    world,
                    x,
                    y,
                    z,
                    ForgeDirection.UP.ordinal(),
                    0.5f,
                    0.5f,
                    0.5f,
                    item.getMetadata(damage));
            } finally {
                world.captureBlockSnapshots = false;
                snapshots.addAll(world.capturedBlockSnapshots);
                world.capturedBlockSnapshots.clear();
            }
            if (!placed || world.getBlock(x, y, z) != block) return FAILED;
            if (world.getTileEntity(x, y, z) instanceof IGregTechTileEntity tile) {
                if (tile.getMetaTileID() != damage) return FAILED;
                orient(job, cell, tile);
            }
            BlockEvent.PlaceEvent event = snapshots.size() > 1
                ? ForgeEventFactory.onPlayerMultiBlockPlace(player, snapshots, ForgeDirection.UP)
                : snapshots.size() == 1
                    ? ForgeEventFactory.onPlayerBlockPlace(player, snapshots.get(0), ForgeDirection.UP)
                    : null;
            if (event != null && event.isCanceled()) return REFUSED;
            if (!free && InventoryUtility.takeFromInventory(player, ProjectorBlocks.single(stack, 1), false) < 1)
                return SHORT;
            keep = true;
            job.record.add(
                x,
                y,
                z,
                Item.getIdFromItem(item),
                damage,
                Block.getIdFromBlock(block),
                world.getBlockMetadata(x, y, z),
                world.getTileEntity(x, y, z) instanceof IGregTechTileEntity placedTile ? placedTile.getMetaTileID()
                    : -1);
            for (BlockSnapshot snapshot : snapshots) {
                Block placedBlock = world.getBlock(snapshot.x, snapshot.y, snapshot.z);
                int meta = world.getBlockMetadata(snapshot.x, snapshot.y, snapshot.z);
                if (!placedBlock.hasTileEntity(meta))
                    placedBlock.onBlockAdded(world, snapshot.x, snapshot.y, snapshot.z);
                world.markAndNotifyBlock(
                    snapshot.x,
                    snapshot.y,
                    snapshot.z,
                    null,
                    snapshot.getReplacedBlock(),
                    placedBlock,
                    snapshot.flag);
            }
            return PLACED;
        } finally {
            if (!keep) ProjectorBlocks.restore(world, snapshots);
        }
    }

    private static void orient(Job job, Cell cell, IGregTechTileEntity tile) {
        if (job.site.isController(cell.x(), cell.y(), cell.z())) ProjectorBlocks.face(tile, job.facing);
        else if (cell.front() >= 0) tile.setFrontFacing(ForgeDirection.getOrientation(cell.front()));
    }
}
