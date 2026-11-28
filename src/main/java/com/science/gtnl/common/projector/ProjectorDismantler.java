package com.science.gtnl.common.projector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.gtnllib.utils.HotbarMessages;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.packet.StructureAnimationStartPacket.Start;
import com.science.gtnl.common.packet.StructureAnimationStatePacket;
import com.science.gtnl.common.projector.ProjectorBroadcast.Box;
import com.science.gtnl.common.projector.ProjectorBroadcast.Kinds;
import com.science.gtnl.common.projector.ProjectorBroadcast.Show;
import com.science.gtnl.common.projector.ProjectorBuilder.Cell;
import com.science.gtnl.common.projector.ProjectorServer.Budget;
import com.science.gtnl.common.structureanimation.StructureAnimationStyle;
import com.science.gtnl.config.MachineConfig;

import appeng.api.config.SecurityPermissions;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.threads.RunnableMachineUpdate;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

// Takes down a projected structure, or undoes the last build, re-checking every block right before it goes. The build's
// animation plays backwards while they go, and what they drop goes back to the player's ME network.
public class ProjectorDismantler {

    public static final int REVERSE_LEAD = 20, HEARTBEAT_TICKS = 20, REPORT_TICKS = 5, MAX_NEIGHBOUR_UPDATES = 4096;
    public static final int REMOVED = 0, CHANGED = 1, DENIED = 2, OUTCOMES = 3;
    private static final Map<UUID, Job> JOBS = new HashMap<>();

    public static class Job {

        public final int id, dimension;
        public final UUID owner;
        public final ProjectorLedger.Site site;
        public final boolean all;
        public final List<Cell> cells;
        public final ProjectorLedger.Build record, leftover = new ProjectorLedger.Build();
        public final List<ItemStack> held = new ArrayList<>();
        public final LongOpenHashSet removed = new LongOpenHashSet();
        public boolean pauseTold;
        public final int[] outcomes = new int[OUTCOMES];
        public final List<UUID> viewers = new ArrayList<>();
        public final ProjectorNetwork.Link network;
        public int next, ticks, reported = -1, toNetwork, toPlayer;
        public Start header;
        public byte[] extra;

        public Job(int id, int dimension, UUID owner, ProjectorLedger.Site site, boolean all, List<Cell> cells,
            ProjectorLedger.Build record, ProjectorNetwork.Link network) {
            this.id = id;
            this.dimension = dimension;
            this.owner = owner;
            this.site = site;
            this.all = all;
            this.cells = cells;
            this.record = record;
            this.network = network;
        }

        public int processed() {
            return outcomes[REMOVED] + outcomes[CHANGED] + outcomes[DENIED];
        }
    }

    private ProjectorDismantler() {}

    public static boolean busy(UUID id) {
        return JOBS.containsKey(id);
    }

    public static int running() {
        return JOBS.size();
    }

    // The server saves the world after it reports stopping, so what the jobs hold still reaches a player or the ground.
    public static void clear() {
        for (Job job : JOBS.values()) abortSafely(job, ProjectorServer.playerOf(job.owner));
        JOBS.clear();
    }

    // Undo takes back only the last build: the blocks it placed that are still as it left them.
    public static void undo(EntityPlayerMP player, int location) {
        if (!MachineConfig.projectorBuildEnabled) {
            ProjectorServer.refuse(player, "disabled");
            return;
        }
        ItemStack projector = ProjectorServer.projectorAt(player, location);
        if (projector == null || !player.isEntityAlive()) return;
        UUID id = player.getUniqueID();
        if (ProjectorServer.busy(id)) {
            ProjectorServer.refuse(player, "busy");
            return;
        }
        if (!ProjectorServer.BUILDS.take(player)) return;
        ProjectorPlan plan = ProjectorPlan.read(projector);
        if (!plan.hasMachine() || !plan.isPlaced() || plan.dimension != player.dimension) {
            ProjectorServer.refuse(player, "not_placed");
            return;
        }
        ProjectorLedger ledger = ProjectorLedger.get();
        ProjectorLedger.Site site = ProjectorLedger.Site.of(id, plan);
        List<ProjectorLedger.Build> builds = ledger == null ? new ArrayList<>() : ledger.builds(site);
        if (builds.isEmpty()) {
            ProjectorServer.refuse(player, "dismantle_nothing");
            return;
        }
        ProjectorLedger.Build record = new ProjectorLedger.Build(), leftover = new ProjectorLedger.Build();
        List<Cell> cells = collect(player, builds.subList(builds.size() - 1, builds.size()), record, leftover);
        if (cells.isEmpty()) {
            ledger.replaceLast(site, leftover);
            ProjectorServer.refuse(player, leftover.size() > 0 ? "dismantle_far" : "dismantle_changed");
            return;
        }
        List<Cell> sorted = new ArrayList<>(cells.size());
        start(player, plan, site, false, sorted, topDown(cells, record, sorted), leftover);
    }

    // Dismantling takes down every block at the projection that matches the structure, whoever placed it.
    public static void fromSite(EntityPlayerMP player, ProjectorBuilder.Preparation preparation) {
        ProjectorSite site = preparation.site;
        World world = player.worldObj;
        List<Cell> cells = new ArrayList<>();
        ProjectorLedger.Build record = new ProjectorLedger.Build();
        LongOpenHashSet seen = new LongOpenHashSet();
        for (int n = 0; n < preparation.candidates.size(); n++) {
            int index = preparation.candidates.getInt(n);
            int x = site.worldX[index], y = site.worldY[index], z = site.worldZ[index];
            if (!seen.add(CoordinatePacker.pack(x, y, z))) continue;
            if (cells.size() >= ProjectorBuilder.MAX_CELLS) break;
            addCell(world, x, y, z, cells, record);
        }
        if (cells.isEmpty()) {
            ProjectorServer.refuse(player, "dismantle_none");
            return;
        }
        List<Cell> sorted = new ArrayList<>(cells.size());
        ProjectorLedger.Build sortedRecord = topDown(cells, record, sorted);
        start(
            player,
            preparation.plan,
            ProjectorLedger.Site.of(player.getUniqueID(), preparation.plan),
            true,
            sorted,
            sortedRecord,
            new ProjectorLedger.Build());
    }

    private static void addCell(World world, int x, int y, int z, List<Cell> cells, ProjectorLedger.Build record) {
        Block block = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        Item item = Item.getItemFromBlock(block);
        if (item == null || block.isAir(world, x, y, z)) return;
        int damage = ProjectorStructure.damageOf(world, block, meta, x, y, z);
        TileEntity tile = world.getTileEntity(x, y, z);
        int machine = tile instanceof IGregTechTileEntity gt ? gt.getMetaTileID() : -1;
        byte front = tile instanceof IGregTechTileEntity gt ? (byte) gt.getFrontFacing()
            .ordinal() : -1;
        cells.add(new Cell(cells.size(), x, y, z, new ItemStack(item, 1, damage), front));
        record.add(x, y, z, Item.getIdFromItem(item), damage, Block.getIdFromBlock(block), meta, machine);
    }

    private static void start(EntityPlayerMP player, ProjectorPlan plan, ProjectorLedger.Site site, boolean all,
        List<Cell> cells, ProjectorLedger.Build record, ProjectorLedger.Build leftover) {
        Job job = new Job(
            ProjectorBuilder.nextJobId(),
            player.dimension,
            player.getUniqueID(),
            site,
            all,
            cells,
            record,
            ProjectorNetwork.link(player, SecurityPermissions.INJECT));
        job.leftover.addAll(leftover);
        JOBS.put(player.getUniqueID(), job);
        announce(player, job, plan);
        HotbarMessages.send(player, new ChatComponentTranslation(ProjectorServer.LANG + "dismantling"));
    }

    // Newest builds first, each position once, and only blocks still exactly as the build left them. Blocks out of
    // reach or in unloaded chunks stay in the record for a later undo; changed ones leave it.
    private static List<Cell> collect(EntityPlayerMP player, List<ProjectorLedger.Build> builds,
        ProjectorLedger.Build kept, ProjectorLedger.Build leftover) {
        World world = player.worldObj;
        int range = MachineConfig.projectorBuildRange;
        LongOpenHashSet seen = new LongOpenHashSet();
        List<Cell> cells = new ArrayList<>();
        for (int b = builds.size() - 1; b >= 0; b--) {
            ProjectorLedger.Build build = builds.get(b);
            for (int i = 0; i < build.size(); i++) {
                int x = build.x.getInt(i), y = build.y.getInt(i), z = build.z.getInt(i);
                if (!seen.add(CoordinatePacker.pack(x, y, z))) continue;
                Item item = Item.getItemById(build.item.getInt(i));
                boolean reachable = world.blockExists(x, y, z)
                    && (range <= 0 || ProjectorServer.distanceSq(player, x, y, z, x, y, z) <= (double) range * range);
                if (!reachable || item == null || cells.size() >= ProjectorBuilder.MAX_CELLS) {
                    leftover.addFrom(build, i);
                    continue;
                }
                if (!unchanged(world, build, i)) continue;
                byte front = world.getTileEntity(x, y, z) instanceof IGregTechTileEntity tile
                    ? (byte) tile.getFrontFacing()
                        .ordinal()
                    : -1;
                cells.add(new Cell(cells.size(), x, y, z, new ItemStack(item, 1, build.damage.getInt(i)), front));
                kept.addFrom(build, i);
            }
        }
        return cells;
    }

    // The top comes down first; the record follows the same order, as the job checks cell i against record entry i.
    private static ProjectorLedger.Build topDown(List<Cell> cells, ProjectorLedger.Build record, List<Cell> sorted) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) order.add(i);
        order.sort(
            (first, second) -> Integer.compare(
                cells.get(second)
                    .y(),
                cells.get(first)
                    .y()));
        ProjectorLedger.Build sortedRecord = new ProjectorLedger.Build();
        for (int index : order) {
            Cell cell = cells.get(index);
            sorted.add(new Cell(sorted.size(), cell.x(), cell.y(), cell.z(), cell.stack(), cell.front()));
            sortedRecord.addFrom(record, index);
        }
        return sortedRecord;
    }

    private static boolean unchanged(World world, ProjectorLedger.Build build, int i) {
        int x = build.x.getInt(i), y = build.y.getInt(i), z = build.z.getInt(i);
        if (!world.blockExists(x, y, z)) return false;
        if (Block.getIdFromBlock(world.getBlock(x, y, z)) != build.block.getInt(i)
            || world.getBlockMetadata(x, y, z) != build.meta.getInt(i)) return false;
        int machine = world.getTileEntity(x, y, z) instanceof IGregTechTileEntity tile ? tile.getMetaTileID() : -1;
        return machine == build.machine.getInt(i);
    }

    // A structure too big for the start packet comes down without an animation.
    private static void announce(EntityPlayerMP player, Job job, ProjectorPlan plan) {
        StructureAnimationStyle style = plan.buildStyle;
        if (style == StructureAnimationStyle.NONE) return;
        int[][] coordinates = ProjectorBroadcast.coordinates(job.cells);
        Box box = Box.of(coordinates[0], coordinates[1], coordinates[2]);
        Kinds kinds = box.fits() ? ProjectorBroadcast.kindsOf(job.cells, box) : null;
        if (kinds == null) return;
        Show show = ProjectorBroadcast.prepare(
            job.id,
            player,
            job.cells,
            coordinates,
            box,
            kinds,
            plan.x,
            plan.y,
            plan.z,
            plan.facing.getIndex(),
            style,
            true);
        job.header = show.header;
        job.extra = show.extra;
        job.viewers.addAll(show.viewers);
        ProjectorBroadcast.begin(show, player);
    }

    public static void tick(Budget budget) {
        if (JOBS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Job>> iterator = JOBS.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Job job = iterator.next()
                .getValue();
            EntityPlayerMP owner = ProjectorServer.playerOf(job.owner);
            if (!ProjectorServer.present(owner, job.dimension)) {
                iterator.remove();
                abortSafely(job, owner);
                continue;
            }
            long share = budget.share(), began = System.nanoTime();
            try {
                job.ticks++;
                if (job.ticks % HEARTBEAT_TICKS == 0)
                    send(job, new StructureAnimationStatePacket(job.id, StructureAnimationStatePacket.PROGRESS, 0));
                // The blocks stay until the meshes standing in for them had a moment to appear.
                if (job.header != null && job.ticks < REVERSE_LEAD) continue;
                removeSome(owner, job, share);
                report(job, owner);
                if (job.next < job.cells.size() || !deliver(owner, job)) continue;
                iterator.remove();
                finish(job, owner);
            } catch (RuntimeException e) {
                ScienceNotLeisure.LOG.warn("Projector dismantle {} stopped", job.id, e);
                iterator.remove();
                abortSafely(job, owner);
                ProjectorServer.refuse(owner, "error");
            } finally {
                budget.spend(System.nanoTime() - began);
            }
        }
    }

    public static boolean cancel(EntityPlayerMP player) {
        Job job = JOBS.remove(player.getUniqueID());
        if (job == null) return false;
        abortSafely(job, player);
        return true;
    }

    // The job has already left the table, so nothing thrown while giving back its items may reach the tick loop.
    private static void abortSafely(Job job, EntityPlayerMP owner) {
        try {
            abort(job, owner);
        } catch (RuntimeException e) {
            ScienceNotLeisure.LOG.warn("Projector dismantle {} could not hand back its items", job.id, e);
        }
    }

    // Whatever the job still holds goes to the player, or onto the site when the player is gone.
    private static void abort(Job job, EntityPlayerMP owner) {
        send(job, new StructureAnimationStatePacket(job.id, StructureAnimationStatePacket.ABORTED, 0));
        World world = owner != null ? owner.worldObj : DimensionManager.getWorld(job.dimension);
        Cell first = job.cells.get(0);
        for (ItemStack stack : job.held) {
            if (owner != null) ProjectorBlocks.give(owner, stack);
            else if (world != null) world
                .spawnEntityInWorld(new EntityItem(world, first.x() + 0.5, first.y() + 0.5, first.z() + 0.5, stack));
        }
        job.held.clear();
        updateNeighbours(job, world);
    }

    // GT walks every connected machine block after each removed casing; that waits until the job ends, when the
    // machines next to what came down get one update each.
    private static void removeSome(EntityPlayerMP owner, Job job, long budget) {
        long deadline = System.nanoTime() + budget;
        World world = owner.worldObj;
        if (!deliver(owner, job)) {
            if (!job.pauseTold)
                HotbarMessages.send(owner, new ChatComponentTranslation(ProjectorServer.LANG + "dismantle_paused"));
            job.pauseTold = true;
            return;
        }
        job.pauseTold = false;
        boolean updates = RunnableMachineUpdate.isCurrentThreadEnabled();
        RunnableMachineUpdate.setCurrentThreadEnabled(false);
        try {
            while (job.next < job.cells.size() && job.held.isEmpty() && System.nanoTime() < deadline) {
                int i = job.next++;
                Cell cell = job.cells.get(i);
                if (!unchanged(world, job.record, i)) {
                    job.outcomes[CHANGED]++;
                    continue;
                }
                int result = ProjectorBlocks.breakAsPlayer(
                    owner,
                    cell.x(),
                    cell.y(),
                    cell.z(),
                    false,
                    cell.stack(),
                    drop -> stow(owner, job, drop));
                if (result == ProjectorBuilder.CLEARED) {
                    job.outcomes[REMOVED]++;
                    job.removed.add(CoordinatePacker.pack(cell.x(), cell.y(), cell.z()));
                } else {
                    job.outcomes[DENIED]++;
                    job.leftover.addFrom(job.record, i);
                }
            }
        } finally {
            RunnableMachineUpdate.setCurrentThreadEnabled(updates);
        }
    }

    // Drops go to the network, then the inventory; what fits nowhere waits in the job, which pauses until it fits.
    private static void stow(EntityPlayerMP owner, Job job, ItemStack drop) {
        int size = drop.stackSize;
        ItemStack left = ProjectorNetwork.inject(job.network, drop);
        if (left == null || left.stackSize < size) job.toNetwork++;
        if (left == null) return;
        int before = left.stackSize;
        owner.inventory.addItemStackToInventory(left);
        if (left.stackSize < before) job.toPlayer++;
        if (left.stackSize > 0) job.held.add(left);
    }

    private static boolean deliver(EntityPlayerMP owner, Job job) {
        if (job.held.isEmpty()) return true;
        List<ItemStack> waiting = new ArrayList<>(job.held);
        job.held.clear();
        for (ItemStack stack : waiting) stow(owner, job, stack);
        return job.held.isEmpty();
    }

    private static void updateNeighbours(Job job, World world) {
        if (world == null || job.removed.isEmpty()) return;
        LongOpenHashSet updated = new LongOpenHashSet();
        LongIterator iterator = job.removed.iterator();
        while (iterator.hasNext() && updated.size() < MAX_NEIGHBOUR_UPDATES) {
            long position = iterator.nextLong();
            int x = CoordinatePacker.unpackX(position), y = CoordinatePacker.unpackY(position),
                z = CoordinatePacker.unpackZ(position);
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                int nx = x + side.offsetX, ny = y + side.offsetY, nz = z + side.offsetZ;
                long neighbour = CoordinatePacker.pack(nx, ny, nz);
                if (job.removed.contains(neighbour) || !updated.add(neighbour) || !world.blockExists(nx, ny, nz))
                    continue;
                if (world.getTileEntity(nx, ny, nz) instanceof IGregTechTileEntity)
                    GregTechAPI.causeMachineUpdate(world, nx, ny, nz);
            }
        }
        job.removed.clear();
    }

    private static void report(Job job, EntityPlayerMP owner) {
        int done = job.processed();
        if (job.header == null || done == job.reported || job.ticks % REPORT_TICKS != 0 && job.next < job.cells.size())
            return;
        job.reported = done;
        ScienceNotLeisure.network
            .sendTo(new StructureAnimationStatePacket(job.id, StructureAnimationStatePacket.BUILT, done), owner);
    }

    private static void finish(Job job, EntityPlayerMP owner) {
        send(job, new StructureAnimationStatePacket(job.id, StructureAnimationStatePacket.DONE, job.cells.size()));
        updateNeighbours(job, owner.worldObj);
        ProjectorLedger ledger = ProjectorLedger.get();
        if (ledger != null) {
            if (job.all) ledger.forget(job.site);
            else ledger.replaceLast(job.site, job.leftover);
        }
        owner.addChatMessage(
            new ChatComponentTranslation(
                ProjectorServer.LANG + "dismantled",
                job.outcomes[REMOVED],
                job.outcomes[CHANGED],
                job.outcomes[DENIED],
                job.toNetwork,
                job.toPlayer));
    }

    private static void send(Job job, StructureAnimationStatePacket packet) {
        if (job.header != null) ProjectorBroadcast.send(job.viewers, packet);
    }
}
