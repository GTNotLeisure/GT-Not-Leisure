package com.science.gtnl.common.wireless;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.WorldEvent;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import com.gtnewhorizon.gtnhlib.item.ItemStackNBT;
import com.science.gtnl.common.wireless.WirelessChannelManager.Address;
import com.science.gtnl.common.wireless.WirelessChannelManager.Entrance;
import com.science.gtnl.utils.enums.GTNLItemList;

import appeng.api.AEApi;
import appeng.api.definitions.ITileDefinition;
import appeng.api.exceptions.FailedConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.util.IOrientable;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

@EventBusSubscriber
public class WirelessBenchmark {

    public static final int MAX_CLUSTERS = 5000, MAX_DEVICES = 32;
    public static final String SITE_KEY = "gtnlWirelessBenchmarkSite";
    private static final int COLUMNS = 40, ROWS = 8, MIXED_EVERY = 5, CONTROLLER_SPAN = 7, LINKS_PER_TICK = 4,
        SETTLE_TICKS = 40, SAMPLES = 20;
    private static final Map<UUID, Site> SITES = new Object2ObjectOpenHashMap<>();

    private WirelessBenchmark() {}

    private static class Site {

        private final World world;
        private final String id = UUID.randomUUID()
            .toString();
        private final Map<Address, Block> blocks = new Object2ObjectOpenHashMap<>();
        private final List<Address> probe = new ObjectArrayList<>();
        private final ObjectArrayFIFOQueue<Address> pending = new ObjectArrayFIFOQueue<>();
        private WirelessFrequency frequency;
        private final Consumer<String> report;
        private int settle = SETTLE_TICKS;
        private int linked, failed;

        private Site(EntityPlayerMP player, Consumer<String> report) {
            this.world = player.worldObj;
            this.report = report;
        }
    }

    public static void build(EntityPlayerMP player, int clusters, int devices, Consumer<String> report) {
        if (clusters < 1 || clusters > MAX_CLUSTERS || devices < 1 || devices > MAX_DEVICES)
            throw new IllegalArgumentException("clusters 1-" + MAX_CLUSTERS + ", devices 1-" + MAX_DEVICES);
        if (SITES.containsKey(player.getUniqueID()))
            throw new IllegalStateException("Remove the previous benchmark site first.");
        Block controller = block(
            AEApi.instance()
                .definitions()
                .blocks()
                .controller());
        Block energy = block(
            AEApi.instance()
                .definitions()
                .blocks()
                .energyCellCreative());
        var definitions = AEApi.instance()
            .definitions()
            .blocks();
        List<Block> kinds = List.of(
            block(definitions.iface()),
            block(definitions.drive()),
            block(definitions.chest()),
            block(definitions.iOPort()),
            block(definitions.spatialIOPort()));
        World world = player.worldObj;
        int dimension = world.provider.dimensionId;
        int x = MathHelper.floor_double(player.posX) + 3, y = MathHelper.floor_double(player.posY),
            z = MathHelper.floor_double(player.posZ);
        List<Address> controllers = new ObjectArrayList<>();
        for (int layer = 0; layer < CONTROLLER_SPAN; layer += 2) {
            for (int i = 0; i < CONTROLLER_SPAN; i++) controllers.add(position(dimension, x + i, y + layer, z));
            for (int tooth = 0; tooth < CONTROLLER_SPAN; tooth += 2) {
                for (int i = 1; i < CONTROLLER_SPAN; i++)
                    controllers.add(position(dimension, x + tooth, y + layer, z + i));
            }
            if (layer > 0) controllers.add(position(dimension, x, y + layer - 1, z + CONTROLLER_SPAN - 1));
        }
        Address power = position(dimension, x, y + 1, z);
        List<List<Address>> rows = new ObjectArrayList<>();
        for (int cluster = 0; cluster < clusters; cluster++) {
            List<Address> row = new ObjectArrayList<>();
            int rowX = x + CONTROLLER_SPAN + 2 + cluster % COLUMNS * 2,
                rowZ = z + cluster / COLUMNS % ROWS * (devices + 1), rowY = y + cluster / (COLUMNS * ROWS) * 2;
            for (int i = 0; i < devices; i++) row.add(position(dimension, rowX, rowY, rowZ + i));
            rows.add(row);
        }
        List<Address> all = new ObjectArrayList<>(controllers);
        all.add(power);
        rows.forEach(all::addAll);
        for (Address address : all) {
            if (address.y() < 1 || address.y() > 254
                || !world.blockExists(address.x(), address.y(), address.z())
                || !world.isAirBlock(address.x(), address.y(), address.z()))
                throw new IllegalStateException(
                    "The site needs loaded air at " + address.x() + ", " + address.y() + ", " + address.z());
        }
        Site site = new Site(player, report);
        SITES.put(player.getUniqueID(), site);
        for (Address address : controllers) place(site, address, controller);
        place(site, power, energy);
        for (int cluster = 0; cluster < clusters; cluster++) {
            List<Address> row = rows.get(cluster);
            for (int i = 0; i < row.size(); i++) {
                Address address = row.get(i);
                Block kind = kinds
                    .get((cluster % MIXED_EVERY == MIXED_EVERY - 1 ? cluster + i : cluster) % kinds.size());
                place(site, address, kind);
                if (address.tile() instanceof IOrientable oriented && oriented.canBeRotated())
                    oriented.setOrientation(ForgeDirection.EAST, ForgeDirection.UP);
            }
        }
        TileEntity source = controllers.get(0)
            .tile();
        WirelessFrequency frequency = WirelessFrequencies.bind(source, player.getUniqueID());
        site.frequency = frequency;
        site.probe.addAll(rows.get(0));
        for (List<Address> row : rows) site.pending.enqueue(row.get(0));
        ItemStack card = GTNLItemList.WirelessFrequencyCard.get(1);
        new WirelessCardBinding(
            frequency,
            WirelessFrequencies.structure(source)
                .representative(),
            player.getUniqueID(),
            player.getUniqueID(),
            player.getCommandSenderName()).write(ItemStackNBT.get(card));
        if (!player.inventory.addItemStackToInventory(card)) player.dropPlayerItemWithRandomChoice(card, false);
        report.accept(
            "Placed " + clusters
                + " subnets x "
                + devices
                + " devices from "
                + x
                + ", "
                + y
                + ", "
                + z
                + ". Linking "
                + LINKS_PER_TICK
                + " per tick; a bound card was added to your inventory.");
    }

    private static void place(Site site, Address address, Block block) {
        if (!site.world.setBlock(address.x(), address.y(), address.z(), block)) throw new IllegalStateException(
            "Could not place benchmark block at " + address + "; remove the partial site.");
        site.blocks.put(address, block);
        TileEntity tile = site.world.getTileEntity(address.x(), address.y(), address.z());
        NBTTagCompound marker = new NBTTagCompound();
        marker.setString(SITE_KEY, site.id);
        IWirelessTileData.of(tile)
            .setTag(IWirelessTileData.DEBUG_KEY, marker);
        tile.markDirty();
    }

    private static Block block(ITileDefinition definition) {
        Block block = definition.maybeBlock()
            .orNull();
        if (block == null) throw new IllegalStateException("An AE2 block needed by the benchmark is disabled.");
        return block;
    }

    private static Address position(int dimension, int x, int y, int z) {
        return new Address(dimension, x, y, z, ForgeDirection.UNKNOWN);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SITES.isEmpty()) return;
        for (Site site : SITES.values()) link(site);
    }

    private static void link(Site site) {
        if (site.pending.isEmpty()) return;
        if (site.settle > 0) {
            site.settle--;
            return;
        }
        IGridNode source = WirelessFrequencies.controllerNode(site.frequency);
        if (source == null || source.getGrid() == null
            || !AutomaticWirelessEntrances.isSettled(
                source.getGrid()
                    .getCache(IPathingGrid.class)))
            return;
        for (int i = 0; i < LINKS_PER_TICK && !site.pending.isEmpty(); i++) {
            Address target = site.pending.dequeue();
            try {
                WirelessOperations.debugConnect(site.frequency, target);
                site.linked++;
            } catch (IllegalArgumentException | IllegalStateException | FailedConnection failure) {
                site.failed++;
            }
        }
        if (site.pending.isEmpty())
            site.report.accept("Benchmark site linked: " + site.linked + " subnets, " + site.failed + " failed.");
    }

    public static void measure(EntityPlayerMP player, Consumer<String> report) {
        Site site = SITES.get(player.getUniqueID());
        if (site == null) throw new IllegalStateException("Build a benchmark site first.");
        if (site.frequency == null) throw new IllegalStateException("Remove the incomplete benchmark site first.");
        if (!site.pending.isEmpty())
            throw new IllegalStateException("Still linking: " + site.pending.size() + " subnets left.");
        ItemStack card = null;
        WirelessCardBinding binding = null;
        for (ItemStack stack : WirelessCardInventory.carried(player)) {
            WirelessCardBinding candidate = WirelessCardBinding.read(stack.getTagCompound());
            if (candidate == null || !WirelessFrequencies.same(candidate.frequency(), site.frequency)) continue;
            card = stack;
            binding = candidate;
        }
        if (card == null) throw new IllegalStateException("Carry the card bound to the benchmark site.");
        WirelessClusterManager.refresh();
        var summary = WirelessClusterManager.summary(site.frequency);
        report.accept(
            "Subnets " + summary.clusters()
                + " (active "
                + summary.active()
                + ", waiting "
                + summary.waiting()
                + "), tracked nodes "
                + WirelessClusterManager.trackedNodes()
                + ", entrances "
                + WirelessChannelManager.entranceCount(site.frequency));
        report.accept("Full rescan: " + micros(WirelessClusterManager::refresh) + " us");
        report.accept("Idle tick (reconcile only): " + micros(WirelessClusterManager::tick) + " us");
        report.accept(
            "One chunk-unload scan: " + micros(() -> WirelessClusterManager.forgetTargets(address -> false))
                + " us (before batching this ran once per unloaded chunk, up to 100 per tick)");
        WirelessCardViewBuilder view = new WirelessCardViewBuilder();
        ItemStack held = card;
        WirelessCardBinding bound = binding;
        WirelessCardSnapshot[] snapshot = new WirelessCardSnapshot[1];
        report.accept(
            "GUI view build (full): " + micros(() -> snapshot[0] = view.build(player, bound, held, true)) + " us");
        report.accept(
            "GUI view check (unchanged): " + micros(() -> snapshot[0] = view.build(player, bound, held, false))
                + " us");
        NBTTagCompound packet = snapshot[0].packet(0, 0, null);
        try {
            report.accept("GUI view packet: " + CompressedStreamTools.compress(packet).length + " bytes compressed");
        } catch (IOException failure) {
            report.accept("GUI view packet could not be encoded: " + failure.getMessage());
        }
        int[] counts = new int[2];
        report.accept(
            "Relink one subnet: " + micros(() -> relink(site, counts))
                + " us ("
                + counts[0]
                + " of "
                + (SAMPLES + 1)
                + " rounds failed, a full scan was pending before "
                + counts[1]
                + " of "
                + (SAMPLES + 1) * 2
                + " operations)");
        Map<IGridNode, Shape> scoped = shapes();
        int scopedClusters = WirelessClusterManager.trackedClusters()
            .size();
        WirelessClusterManager.refresh();
        Map<IGridNode, Shape> full = shapes();
        int fullClusters = WirelessClusterManager.trackedClusters()
            .size();
        long differing = differingNodes(scoped, full);
        report.accept(
            "Scoped state matches full rescan: " + (differing == 0 && scopedClusters == fullClusters)
                + " (clusters "
                + scopedClusters
                + " / "
                + fullClusters
                + ", tracked nodes "
                + scoped.size()
                + " / "
                + full.size()
                + ", differing nodes "
                + differing
                + ")");
    }

    private record Shape(Set<IGridNode> nodes, Set<WirelessFrequency> frequencies, boolean blocked, boolean limited) {}

    private static long differingNodes(Map<IGridNode, Shape> scoped, Map<IGridNode, Shape> full) {
        Map<Shape, Shape> compared = new Reference2ObjectOpenHashMap<>();
        Set<Shape> matched = new ReferenceOpenHashSet<>();
        long differing = 0;
        for (var entry : scoped.entrySet()) {
            Shape previous = entry.getValue(), current = full.get(entry.getKey());
            if (!compared.containsKey(previous)) {
                compared.put(previous, current);
                if (previous.equals(current)) matched.add(previous);
            }
            if (!matched.contains(previous) || compared.get(previous) != current) differing++;
        }
        for (IGridNode node : full.keySet()) {
            if (!scoped.containsKey(node)) differing++;
        }
        return differing;
    }

    private static Map<IGridNode, Shape> shapes() {
        Map<IGridNode, Shape> shapes = new Reference2ObjectOpenHashMap<>();
        for (var cluster : WirelessClusterManager.trackedClusters()) {
            Shape shape = new Shape(cluster.nodes(), cluster.frequencies(), cluster.blocked(), cluster.limited());
            for (IGridNode node : cluster.nodes()) shapes.put(node, shape);
        }
        return shapes;
    }

    private static void relink(Site site, int[] counts) {
        try {
            Entrance entrance = entranceInto(site);
            if (WirelessClusterManager.scanPending()) counts[1]++;
            if (entrance == null || !WirelessOperations.debugDisconnect(entrance)) {
                counts[0]++;
                return;
            }
            if (WirelessClusterManager.scanPending()) counts[1]++;
            WirelessOperations.debugConnect(site.frequency, site.probe.get(0));
        } catch (IllegalArgumentException | IllegalStateException | FailedConnection failure) {
            counts[0]++;
        }
    }

    private static Entrance entranceInto(Site site) {
        for (Entrance entrance : WirelessChannelManager.entrances()) {
            if (WirelessFrequencies.same(entrance.frequency(), site.frequency) && site.probe.contains(
                entrance.target()
                    .withoutSide()))
                return entrance;
        }
        return null;
    }

    private static long micros(Runnable action) {
        action.run();
        long start = System.nanoTime();
        for (int i = 0; i < SAMPLES; i++) action.run();
        return (System.nanoTime() - start) / SAMPLES / 1000;
    }

    public static void remove(EntityPlayerMP player, Consumer<String> report) {
        Site site = SITES.get(player.getUniqueID());
        if (site == null) throw new IllegalStateException("No benchmark site to remove.");
        site.pending.clear();
        // Detach wireless edges before controller removal can migrate the whole grid.
        if (site.frequency != null) for (Entrance entrance : WirelessChannelManager.entrances()) {
            if (!WirelessFrequencies.same(entrance.frequency(), site.frequency)) continue;
            WirelessChannelManager.disconnect(entrance.id());
        }
        site.frequency = null;
        int removed = 0;
        for (var iterator = site.blocks.entrySet()
            .iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            Address address = entry.getKey();
            if (!site.world.blockExists(address.x(), address.y(), address.z())) continue;
            TileEntity tile = site.world.getTileEntity(address.x(), address.y(), address.z());
            if (site.world.getBlock(address.x(), address.y(), address.z()) == entry.getValue() && tile != null
                && site.id.equals(
                    IWirelessTileData.of(tile)
                        .getCompoundTag(IWirelessTileData.DEBUG_KEY)
                        .getString(SITE_KEY))) {
                if (!site.world.setBlockToAir(address.x(), address.y(), address.z())) continue;
                removed++;
            }
            iterator.remove();
        }
        if (site.blocks.isEmpty()) SITES.remove(player.getUniqueID());
        WirelessClusterManager.invalidateTopology();
        report.accept(
            "Removed " + removed
                + " benchmark blocks; "
                + site.blocks.size()
                + " remain. Load their chunks and run bench remove again if needed.");
    }

    @SubscribeEvent
    public static void unloadWorld(WorldEvent.Unload event) {
        if (!event.world.isRemote) SITES.values()
            .removeIf(site -> site.world == event.world);
    }

    public static void clear() {
        SITES.clear();
    }
}
