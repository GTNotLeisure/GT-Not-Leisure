package com.science.gtnl.common.projector;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.Deflater;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fluids.IFluidHandler;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import com.gtnewhorizon.gtnhlib.eventbus.Phase;
import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.gtnewhorizon.structurelib.alignment.IAlignment;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.util.InventoryUtility;
import com.gtnllib.utils.HotbarMessages;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.packet.StructureAnimationStartPacket;
import com.science.gtnl.common.packet.StructureAnimationStartPacket.Start;
import com.science.gtnl.common.packet.StructureAnimationStatePacket;
import com.science.gtnl.common.projector.ProjectorStructure.Choice;
import com.science.gtnl.common.structureanimation.StructureAnimationStyle;
import com.science.gtnl.config.MachineConfig;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.threads.RunnableMachineUpdate;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

@EventBusSubscriber(phase = Phase.PRE)
public class ProjectorBuilder {

    public record Cell(int cell, int x, int y, int z, ItemStack stack, byte front) {}

    public record Kinds(byte[] deflated, int[] items, int[] damages) {}

    public enum Stage {
        WAITING,
        SIMULATING,
        LOADING,
        SCANNING
    }

    public static final int MAX_CELLS = StructureAnimationStartPacket.MAX_CELLS,
        MAX_SIDE = StructureAnimationStartPacket.MAX_SIDE, VIEW_RANGE = 128,
        MAX_START_BYTES = StructureAnimationStartPacket.MAX_START_BYTES, HEARTBEAT_TICKS = 20, STALL_TICKS = 200,
        COOLDOWN_TICKS = 40, SHOWN_SHORTAGES = 8, SCAN_BATCH = 256, RELEASE_TICKS = 4, BUILT_TICKS = 5,
        LOADS_PER_TICK = 4, LOAD_TICKS = 200, LEDGER_TICKS = 100;
    public static final int PLACED = 0, EXISTING = 1, SHORT = 2, BLOCKED = 3, REFUSED = 4, FAILED = 5, OCCUPIED = 6,
        UNLOADED = 7, OBSTRUCTED = 8, OUTCOMES = 9, CLEARED = -1, DENIED_LIMIT = 256, MAX_VOLUME = 4_000_000;
    public static final String LANG = "gtnl.projector.build.";
    private static final Map<UUID, Preparation> PREPARING = new LinkedHashMap<>();
    private static final Map<UUID, Job> JOBS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_REQUEST = new HashMap<>();
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
        public final int minX, minY, minZ, sizeX, sizeY, sizeZ;
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
        public int next, retried, ticks, placingTicks, denied, controllersPlaced, reportedBuilt = -1, cleared,
            clearsDone;
        public long placingNanos;
        public final long[] phases = new long[5];
        public final int placingTick;
        public final LongOpenHashSet targets, held = new LongOpenHashSet();
        public long[] releasing;
        public int released;
        public Start header;
        public byte[] extra;
        public final ProjectorLedger.Site ledgerSite;
        public final ProjectorLedger.Build record;

        public Job(int id, UUID owner, Preparation preparation, Cell[] cells, int[] box, List<UUID> viewers,
            int placingTick) {
            this.id = id;
            this.owner = owner;
            ledgerSite = ProjectorLedger.Site.of(owner, preparation.plan);
            record = ProjectorLedger.begin(ledgerSite);
            dimension = preparation.dimension;
            site = preparation.site;
            facing = site.facing;
            minX = box[0];
            minY = box[1];
            minZ = box[2];
            sizeX = box[3];
            sizeY = box[4];
            sizeZ = box[5];
            this.cells = cells;
            this.viewers = viewers;
            this.placingTick = placingTick;
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

        public int boxIndex(Cell cell) {
            return ((cell.y() - minY) * sizeZ + (cell.z() - minZ)) * sizeX + (cell.x() - minX);
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

    public static boolean canRequest(UUID id) {
        Integer last = LAST_REQUEST.get(id);
        return !busy(id) && !ProjectorDismantler.busy(id)
            && (last == null || MinecraftServer.getServer()
                .getTickCounter() - last >= COOLDOWN_TICKS);
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
            refuse(player, "disabled");
            return;
        }
        if (!player.isEntityAlive()) return;
        ItemStack projector = ProjectorServer.projectorAt(player, location);
        if (projector == null) return;
        UUID id = player.getUniqueID();
        if (busy(id) || ProjectorDismantler.busy(id)) {
            refuse(player, "busy");
            return;
        }
        int now = MinecraftServer.getServer()
            .getTickCounter();
        Integer last = LAST_REQUEST.get(id);
        if (last != null && now - last < COOLDOWN_TICKS) {
            refuse(player, "too_soon");
            return;
        }
        LAST_REQUEST.put(id, now);
        ProjectorPlan plan = ProjectorPlan.read(projector);
        if (!plan.hasMachine() || !ProjectorMachines.isSupported(plan.machine)
            || !plan.isPlaced()
            || plan.dimension != player.dimension
            || !ProjectorMachines.validFacings(plan.machine)
                .test(plan.facing)) {
            refuse(player, "not_placed");
            return;
        }
        if (!dismantle) ProjectorOrders.cancel(player, false);
        PREPARING.put(id, new Preparation(player.dimension, plan, location, fromOrder, dismantle));
        HotbarMessages.send(player, new ChatComponentTranslation(LANG + "preparing"));
    }

    public static void clear() {
        ProjectorSimulation.clearShared();
        PREPARING.clear();
        ProjectorLedger.touch();
        JOBS.clear();
        LAST_REQUEST.clear();
        releaseChunks();
        ProjectorDismantler.clear();
        ProjectorOrders.clear();
        ProjectorReading.clear();
    }

    // Blocks are placed while the animation still plays; their updates reach clients only once it ends, so nobody
    // sees real blocks appear before the animation shows them.
    public static boolean holdUpdate(WorldServer world, int x, int y, int z) {
        if (JOBS.isEmpty()) return false;
        for (Job job : JOBS.values()) {
            if (!job.hiding() || job.dimension != world.provider.dimensionId) continue;
            if (x < job.minX || y < job.minY || z < job.minZ) continue;
            if (x >= job.minX + job.sizeX || y >= job.minY + job.sizeY || z >= job.minZ + job.sizeZ) continue;
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
                LongArrayList chunk = byChunk.get(chunkOf(position));
                if (chunk == null) byChunk.put(chunkOf(position), chunk = new LongArrayList());
                chunk.add(position);
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

    private static long chunkOf(long position) {
        return (long) (CoordinatePacker.unpackX(position) >> 4) << 32
            | (CoordinatePacker.unpackZ(position) >> 4) & 0xFFFFFFFFL;
    }

    public static void refuse(EntityPlayerMP player, String reason, Object... args) {
        player.addChatMessage(new ChatComponentTranslation(LANG + reason, args));
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!LAST_REQUEST.isEmpty()) {
            int now = MinecraftServer.getServer()
                .getTickCounter();
            LAST_REQUEST.values()
                .removeIf(last -> now - last >= COOLDOWN_TICKS);
        }
        if (PREPARING.isEmpty() && JOBS.isEmpty()) return;
        // All jobs and the one preparation share one budget per tick, so several builders cost the server no more than
        // one; a job that needs less than its share leaves the rest to the jobs after it.
        int sharing = JOBS.size();
        long remaining = MachineConfig.projectorPlaceBudgetMillis * 1_000_000L;
        if (!PREPARING.isEmpty()) remaining -= prepareAll(remaining / (sharing + 1));
        tickJobs(remaining, sharing);
        releaseChunks();
    }

    private static long prepareAll(long share) {
        long began = System.nanoTime();
        boolean active = false;
        Iterator<Map.Entry<UUID, Preparation>> iterator = PREPARING.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Preparation> entry = iterator.next();
            Preparation preparation = entry.getValue();
            EntityPlayerMP owner = playerOf(entry.getKey());
            if (owner == null || !owner.isEntityAlive() || owner.dimension != preparation.dimension) {
                iterator.remove();
                continue;
            }
            preparation.ticks++;
            try {
                if (preparation.ticks % HEARTBEAT_TICKS == 0)
                    HotbarMessages.send(owner, new ChatComponentTranslation(LANG + "preparing"));
                if (active) continue;
                active = true;
                boolean progressed = step(owner, preparation, share);
                if (preparation.ended) {
                    iterator.remove();
                    continue;
                }
                if (!progressed) {
                    if (++preparation.idle <= STALL_TICKS) continue;
                    iterator.remove();
                    refuse(owner, "stalled");
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
                    refuse(owner, "error");
                }
            } catch (RuntimeException e) {
                ScienceNotLeisure.LOG
                    .warn("Projector build preparation for {} failed", owner.getCommandSenderName(), e);
                iterator.remove();
                refuse(owner, "error");
            }
        }
        return System.nanoTime() - began;
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
            refuse(owner, "failed");
            preparation.ended = true;
            return;
        }
        if (!fits(structure, single, offsets)) {
            refuse(owner, "too_big");
            preparation.ended = true;
            return;
        }
        preparation.site = new ProjectorSite(structure, plan.facing, plan.x, plan.y, plan.z, offsets);
        int range = MachineConfig.projectorBuildRange;
        int[] bounds = ProjectorPlan.stackedBounds(single, offsets);
        if (range > 0 && distanceSq(
            owner,
            plan.x + bounds[0],
            plan.y + bounds[1],
            plan.z + bounds[2],
            plan.x + bounds[3],
            plan.y + bounds[4],
            plan.z + bounds[5]) > (double) range * range) {
            refuse(owner, "too_far", range);
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

    private static void releaseChunks() {
        if (TICKETS.isEmpty()) return;
        Iterator<Map.Entry<UUID, List<Ticket>>> iterator = TICKETS.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, List<Ticket>> entry = iterator.next();
            if (PREPARING.containsKey(entry.getKey()) || JOBS.containsKey(entry.getKey())
                || ProjectorDismantler.busy(entry.getKey())) continue;
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
                free ? count : InventoryUtility.takeFromInventory(owner, single(stacks.get(kind), count), true));
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
            missing.add(single(stacks.get(entry.getIntKey()), entry.getIntValue()));
        }
        owner.addChatMessage(
            new ChatComponentTranslation(
                LANG + "summary",
                admitted.size(),
                preparation.existing,
                shortCells,
                preparation.occupied + preparation.clears.size(),
                preparation.unloaded));
        for (int i = 0; i < missing.size() && i < SHOWN_SHORTAGES; i++) {
            ItemStack stack = missing.get(i);
            owner.addChatMessage(
                new ChatComponentTranslation(LANG + "short_item", stack.func_151000_E(), stack.stackSize));
        }
        if (missing.size() > SHOWN_SHORTAGES)
            owner.addChatMessage(new ChatComponentTranslation(LANG + "short_more", missing.size() - SHOWN_SHORTAGES));
        if (!missing.isEmpty()) owner.addChatMessage(new ChatComponentTranslation(LANG + "short_note"));
        // An order waits for the crafts and builds everything at once; a build that an order started does not order
        // again, so materials the network fails to make end in a partial build instead of an endless wait.
        if (!missing.isEmpty() && !preparation.fromOrder && ProjectorOrders.place(owner, preparation.location, missing))
            return;
        if (admitted.isEmpty()) {
            if (preparation.clears.isEmpty() && preparation.turns.isEmpty()) refuse(owner, "nothing");
            else clearOnly(owner, preparation);
            return;
        }
        start(owner, preparation, admitted);
    }

    // Nothing left to place, so the obstacles go at once without a job or an animation.
    private static void clearOnly(EntityPlayerMP owner, Preparation preparation) {
        ProjectorSite site = preparation.site;
        for (int i = 0; i < preparation.turns.size(); i++) turn(owner, site, preparation.turns.getInt(i));
        int cleared = 0, kept = 0;
        for (int i = 0; i < preparation.clears.size(); i++) {
            int cell = preparation.clears.getInt(i);
            if (clear(owner, site.worldX[cell], site.worldY[cell], site.worldZ[cell]) == CLEARED) cleared++;
            else kept++;
        }
        HotbarMessages.send(owner, new ChatComponentTranslation(LANG + "cleared", cleared, kept));
    }

    private static void turn(EntityPlayerMP owner, ProjectorSite site, int index) {
        World world = owner.worldObj;
        int x = site.worldX[index], y = site.worldY[index], z = site.worldZ[index];
        if (!site.facesWrong(world, index) || !world.canMineBlock(owner, x, y, z)
            || !(world.getTileEntity(x, y, z) instanceof IGregTechTileEntity tile)) return;
        tile.setFrontFacing(site.facing.getDirection());
        if (tile.getMetaTileEntity() instanceof IAlignment alignment) alignment.setExtendedFacing(site.facing);
    }

    private static int clearSafely(EntityPlayerMP player, Job job, int cell) {
        ProjectorSite site = job.site;
        int x = site.worldX[cell], y = site.worldY[cell], z = site.worldZ[cell];
        try {
            if (site.compute(player.worldObj, cell) != ProjectorSite.WRONG) return EXISTING;
            return clear(player, x, y, z);
        } catch (RuntimeException e) {
            ScienceNotLeisure.LOG.warn("Projector build {} could not clear {} {} {}", job.id, x, y, z, e);
            return FAILED;
        }
    }

    // Blocks in the way that hold items, fluids or a machine are left alone.
    private static int clear(EntityPlayerMP player, int x, int y, int z) {
        return breakAsPlayer(player, x, y, z, true, null, drop -> give(player, drop));
    }

    public static void give(EntityPlayerMP player, ItemStack drop) {
        if (!player.inventory.addItemStackToInventory(drop)) player.dropPlayerItemWithRandomChoice(drop, false);
    }

    // Breaks a block as the player would, so protection mods can refuse it; its drops, and anything it spills while
    // breaking, go to the sink instead of the ground. A block without a tile entity gives back the item it was placed
    // from when that is known, as glass or ice would otherwise drop nothing.
    public static int breakAsPlayer(EntityPlayerMP player, int x, int y, int z, boolean protect, ItemStack placedFrom,
        Consumer<ItemStack> sink) {
        World world = player.worldObj;
        Block block = world.getBlock(x, y, z);
        if (block.isAir(world, x, y, z)) return CLEARED;
        if (block.getBlockHardness(world, x, y, z) < 0 || protect && isProtected(world.getTileEntity(x, y, z)))
            return OCCUPIED;
        if (!world.canMineBlock(player, x, y, z) || !player.canPlayerEdit(x, y, z, ForgeDirection.UP.ordinal(), null))
            return BLOCKED;
        BlockEvent.BreakEvent event = ForgeHooks
            .onBlockBreakEvent(world, player.theItemInWorldManager.getGameType(), player, x, y, z);
        if (event.isCanceled()) return REFUSED;
        int meta = world.getBlockMetadata(x, y, z);
        List<ItemStack> drops = new ArrayList<>();
        if (!player.capabilities.isCreativeMode) {
            if (placedFrom != null && !block.hasTileEntity(meta)) drops.add(placedFrom.copy());
            else drops.addAll(block.getDrops(world, x, y, z, meta, 0));
        }
        block.onBlockHarvested(world, x, y, z, meta, player);
        if (!block.removedByPlayer(world, player, x, y, z, false)) return FAILED;
        block.onBlockDestroyedByPlayer(world, x, y, z, meta);
        for (Object entity : world.getEntitiesWithinAABB(
            EntityItem.class,
            AxisAlignedBB.getBoundingBox(x - 0.5, y - 0.5, z - 0.5, x + 1.5, y + 1.5, z + 1.5))) {
            EntityItem spilled = (EntityItem) entity;
            if (spilled.isDead || spilled.age > 0) continue;
            drops.add(spilled.getEntityItem());
            spilled.setDead();
        }
        for (ItemStack drop : drops) {
            if (drop != null && drop.stackSize > 0) sink.accept(drop);
        }
        return CLEARED;
    }

    private static boolean isProtected(TileEntity tile) {
        return tile instanceof IInventory || tile instanceof IFluidHandler || tile instanceof IGregTechTileEntity;
    }

    private static int kindOf(ItemStack stack) {
        return Choice.idOf(stack.getItem(), stack.getItemDamage());
    }

    // A plain copy without NBT, so InventoryUtility only takes items that carry no data either.
    private static ItemStack single(ItemStack stack, int count) {
        return new ItemStack(stack.getItem(), count, stack.getItemDamage());
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
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE,
            maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Cell cell : sorted) {
            minX = Math.min(minX, cell.x());
            minY = Math.min(minY, cell.y());
            minZ = Math.min(minZ, cell.z());
            maxX = Math.max(maxX, cell.x());
            maxY = Math.max(maxY, cell.y());
            maxZ = Math.max(maxZ, cell.z());
        }
        int sizeX = maxX - minX + 1, sizeY = maxY - minY + 1, sizeZ = maxZ - minZ + 1;
        if (sorted.size() > MAX_CELLS || sizeX > MAX_SIDE
            || sizeY > MAX_SIDE
            || sizeZ > MAX_SIDE
            || (long) sizeX * sizeY * sizeZ > MAX_VOLUME) {
            refuse(player, "too_big");
            return;
        }
        Kinds kinds = encodeKinds(sorted, minX, minY, minZ, sizeX, sizeY, sizeZ);
        if (kinds.deflated().length > MAX_START_BYTES) {
            refuse(player, "too_big");
            return;
        }
        StructureAnimationStyle style = preparation.plan.buildStyle;
        List<UUID> viewers = new ArrayList<>();
        List<EntityPlayerMP> receivers = new ArrayList<>();
        for (Object entry : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (style == StructureAnimationStyle.NONE) break;
            if (!(entry instanceof EntityPlayerMP other) || other.dimension != preparation.dimension) continue;
            if (other != player && distanceSq(other, minX, minY, minZ, maxX, maxY, maxZ) > VIEW_RANGE * VIEW_RANGE)
                continue;
            viewers.add(other.getUniqueID());
            receivers.add(other);
        }
        int[] xs = new int[sorted.size()], ys = new int[sorted.size()], zs = new int[sorted.size()];
        for (int i = 0; i < xs.length; i++) {
            Cell cell = sorted.get(i);
            xs[i] = cell.x();
            ys[i] = cell.y();
            zs[i] = cell.z();
        }
        Vec3 look = player.getLookVec();
        StructureAnimationStyle.Site animationSite = new StructureAnimationStyle.Site(
            preparation.dimension,
            minX,
            minY,
            minZ,
            sizeX,
            sizeY,
            sizeZ,
            xs,
            ys,
            zs,
            site.originX,
            site.originY,
            site.originZ,
            site.facing.getIndex(),
            player.posX,
            player.posZ,
            look.xCoord,
            look.zCoord);
        ByteBuf extra = Unpooled.buffer();
        int placingTick = style.prepare(animationSite, extra);
        Cell[] cells = sorted.toArray(new Cell[0]);
        int[] order = style.order(animationSite);
        if (order != null) for (int i = 0; i < order.length; i++) cells[i] = sorted.get(order[i]);
        Job job = new Job(
            nextJobId(),
            player.getUniqueID(),
            preparation,
            cells,
            new int[] { minX, minY, minZ, sizeX, sizeY, sizeZ },
            viewers,
            placingTick);
        JOBS.put(player.getUniqueID(), job);
        if (style == StructureAnimationStyle.NONE) return;
        byte[] extraBytes = new byte[extra.readableBytes()];
        extra.readBytes(extraBytes);
        IntArrayList frontCells = new IntArrayList();
        ByteArrayList fronts = new ByteArrayList();
        for (Cell cell : sorted) {
            if (cell.front() < 0) continue;
            frontCells.add(job.boxIndex(cell));
            fronts.add(cell.front());
        }
        job.extra = extraBytes;
        job.header = new Start(
            job.id,
            preparation.dimension,
            minX,
            minY,
            minZ,
            sizeX,
            sizeY,
            sizeZ,
            kinds.deflated(),
            kinds.items(),
            kinds.damages(),
            frontCells.toIntArray(),
            fronts.toByteArray(),
            site.originX,
            site.originY,
            site.originZ,
            site.facing.getIndex(),
            style,
            job.cells.length,
            job.placingTick,
            false,
            0,
            false);
        for (EntityPlayerMP receiver : receivers) sendStart(job, receiver, receiver == player);
    }

    // One code per cell of the bounding box (0 = not built), deflated; the code only tells kinds apart for the outline.
    public static Kinds encodeKinds(List<Cell> cells, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
        Long2IntOpenHashMap palette = new Long2IntOpenHashMap();
        IntArrayList items = new IntArrayList(), damages = new IntArrayList();
        short[] grid = new short[sizeX * sizeY * sizeZ];
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
            grid[((cell.y() - minY) * sizeZ + (cell.z() - minZ)) * sizeX + (cell.x() - minX)] = (short) code;
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
        return new Kinds(out.toByteArray(), items.toIntArray(), damages.toIntArray());
    }

    public static double distanceSq(Entity entity, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        double dx = Math.max(0, Math.max(minX - entity.posX, entity.posX - maxX - 1));
        double dy = Math.max(0, Math.max(minY - entity.posY, entity.posY - maxY - 1));
        double dz = Math.max(0, Math.max(minZ - entity.posZ, entity.posZ - maxZ - 1));
        return dx * dx + dy * dy + dz * dz;
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
        int processed = job.processed();
        if (processed > 0) ScienceNotLeisure.LOG.debug(
            "Projector build {}: {} of {} cells in {} ms over {} ticks, {} us per cell",
            job.id,
            processed,
            job.cells.length,
            job.placingNanos / 1_000_000,
            job.placingTicks,
            job.placingNanos / 1000 / processed);
        if (processed > 0) ScienceNotLeisure.LOG.debug(
            "Projector build {} per cell in us: checks {}, set block {}, place event {}, take {}, notify {}",
            job.id,
            job.phases[0] / 1000 / processed,
            job.phases[1] / 1000 / processed,
            job.phases[2] / 1000 / processed,
            job.phases[3] / 1000 / processed,
            job.phases[4] / 1000 / processed);
        send(
            job,
            new StructureAnimationStatePacket(
                job.id,
                state,
                job.next,
                state == StructureAnimationStatePacket.DONE ? job.skipped.toIntArray() : new int[0]));
        EntityPlayerMP owner = playerOf(job.owner);
        if (owner == null || processed == 0) return;
        int[] outcomes = job.outcomes;
        owner.addChatMessage(
            new ChatComponentTranslation(
                LANG + "result",
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

    private static void sendStart(Job job, EntityPlayerMP receiver, boolean own) {
        ScienceNotLeisure.network
            .sendTo(new StructureAnimationStartPacket(job.header.as(own, job.ticks), job.extra), receiver);
    }

    // Players who come into range after the build started still get the animation, from where it stands now.
    private static void admitViewers(Job job, EntityPlayerMP owner) {
        if (job.header == null) return;
        Start site = job.header;
        for (Object entry : owner.worldObj.playerEntities) {
            if (!(entry instanceof EntityPlayerMP other) || job.viewers.contains(other.getUniqueID())) continue;
            if (distanceSq(
                other,
                site.originX(),
                site.originY(),
                site.originZ(),
                site.originX() + site.sizeX() - 1,
                site.originY() + site.sizeY() - 1,
                site.originZ() + site.sizeZ() - 1) > VIEW_RANGE * VIEW_RANGE) continue;
            job.viewers.add(other.getUniqueID());
            sendStart(job, other, false);
            if (job.ticks < job.placingTick) continue;
            ScienceNotLeisure.network
                .sendTo(new StructureAnimationStatePacket(job.id, StructureAnimationStatePacket.PLACING, 0), other);
        }
    }

    private static void send(Job job, StructureAnimationStatePacket packet) {
        for (UUID id : job.viewers) {
            EntityPlayerMP viewer = playerOf(id);
            if (viewer != null) ScienceNotLeisure.network.sendTo(packet, viewer);
        }
    }

    private static void tickJobs(long remaining, int placing) {
        Iterator<Map.Entry<UUID, Job>> iterator = JOBS.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Job> entry = iterator.next();
            Job job = entry.getValue();
            EntityPlayerMP owner = playerOf(entry.getKey());
            if (owner == null || !owner.isEntityAlive() || owner.dimension != job.dimension) {
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
                    if (job.ticks % HEARTBEAT_TICKS == 0)
                        send(job, new StructureAnimationStatePacket(job.id, StructureAnimationStatePacket.PROGRESS, 0));
                } else {
                    if (job.ticks == job.placingTick)
                        send(job, new StructureAnimationStatePacket(job.id, StructureAnimationStatePacket.PLACING, 0));
                    releaseSome(job, false);
                }
                // Blocks marked last tick went out to the players during this tick, so a cell counts as sent only once
                // every held block was marked before this tick; the job therefore ends one tick after its last block.
                int sent = released ? job.next : 0;
                long share = remaining / Math.max(1, placing--);
                reportBuilt(job, owner);
                if (job.placedAll()) {
                    if (hiding) continue;
                    if (!released) {
                        send(
                            job,
                            new StructureAnimationStatePacket(job.id, StructureAnimationStatePacket.PROGRESS, sent));
                        continue;
                    }
                    iterator.remove();
                    end(job, owner, StructureAnimationStatePacket.DONE, "done", job.outcomes[PLACED]);
                    continue;
                }
                remaining -= placeSome(owner, job, share);
                if (job.denied >= DENIED_LIMIT) {
                    iterator.remove();
                    end(job, owner, StructureAnimationStatePacket.ABORTED, "denied");
                    continue;
                }
                if (hiding) continue;
                send(job, new StructureAnimationStatePacket(job.id, StructureAnimationStatePacket.PROGRESS, sent));
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
        if (job.header == null)
            HotbarMessages.send(owner, new ChatComponentTranslation(LANG + "progress", built, job.cells.length));
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
                HotbarMessages.send(owner, new ChatComponentTranslation(LANG + message, args));
            else refuse(owner, message, args);
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
                if (outcome != PLACED) job.skipped.add(job.boxIndex(cell));
                if (job.denied >= DENIED_LIMIT) break;
            }
        } finally {
            RunnableMachineUpdate.setCurrentThreadEnabled(updates);
            job.placingNanos += System.nanoTime() - began;
            job.placingTicks++;
        }
        return System.nanoTime() - began;
    }

    public static EntityPlayerMP playerOf(UUID id) {
        for (Object entry : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (entry instanceof EntityPlayerMP player && player.getUniqueID()
                .equals(id)) return player;
        }
        return null;
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
    // stays: every way out before the final take puts the captured blocks back.
    private static int place(EntityPlayerMP player, Job job, Cell cell) {
        long mark = System.nanoTime();
        World world = player.worldObj;
        int x = cell.x(), y = cell.y(), z = cell.z();
        switch (job.site.compute(world, cell.cell())) {
            case ProjectorSite.CORRECT -> {
                return EXISTING;
            }
            case ProjectorSite.UNLOADED -> {
                return UNLOADED;
            }
            case ProjectorSite.MISSING -> {}
            default -> {
                int cleared = clear(player, x, y, z);
                if (cleared != CLEARED) return cleared;
                job.cleared++;
            }
        }
        ItemBlock item = (ItemBlock) cell.stack()
            .getItem();
        int damage = cell.stack()
            .getItemDamage();
        ItemStack stack = single(cell.stack(), 1);
        Block block = item.field_150939_a;
        if (!world.canMineBlock(player, x, y, z) || !player.canPlayerEdit(x, y, z, ForgeDirection.UP.ordinal(), stack))
            return BLOCKED;
        if (!world.canPlaceEntityOnSide(block, x, y, z, false, ForgeDirection.UP.ordinal(), null, stack))
            return OBSTRUCTED;
        boolean free = player.capabilities.isCreativeMode;
        if (!free && InventoryUtility.takeFromInventory(player, single(stack, 1), true) < 1) return SHORT;
        mark = lap(job, 0, mark);
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
            mark = lap(job, 1, mark);
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
            mark = lap(job, 2, mark);
            if (!free && InventoryUtility.takeFromInventory(player, single(stack, 1), false) < 1) return SHORT;
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
            mark = lap(job, 3, mark);
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
            lap(job, 4, mark);
            return PLACED;
        } finally {
            if (!keep) restore(world, snapshots);
        }
    }

    private static long lap(Job job, int phase, long since) {
        long now = System.nanoTime();
        job.phases[phase] += now - since;
        return now;
    }

    private static void orient(Job job, Cell cell, IGregTechTileEntity tile) {
        if (job.site.isController(cell.x(), cell.y(), cell.z())) {
            tile.setFrontFacing(job.facing.getDirection());
            if (tile.getMetaTileEntity() instanceof IAlignment alignment) alignment.setExtendedFacing(job.facing);
        } else if (cell.front() >= 0) {
            tile.setFrontFacing(ForgeDirection.getOrientation(cell.front()));
        }
    }

    // Restoring with this flag set keeps the removed blocks from dropping items, as Forge does for cancelled places.
    private static void restore(World world, List<BlockSnapshot> snapshots) {
        world.restoringBlockSnapshots = true;
        try {
            for (int i = snapshots.size() - 1; i >= 0; i--) snapshots.get(i)
                .restore(true, false);
        } finally {
            world.restoringBlockSnapshots = false;
        }
    }
}
