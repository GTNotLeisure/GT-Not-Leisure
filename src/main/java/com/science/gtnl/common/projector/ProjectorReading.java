package com.science.gtnl.common.projector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import com.gtnewhorizon.gtnhlib.eventbus.Phase;
import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.gtnewhorizon.structurelib.alignment.IAlignment;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnllib.utils.HotbarMessages;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.projector.ProjectorStructure.Choice;
import com.science.gtnl.common.wireless.WirelessCardInventory;
import com.science.gtnl.config.MachineConfig;
import com.science.gtnl.utils.enums.GTNLStructureChannels;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.misc.GTStructureChannels;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

// Reads a built machine into the projector's plan: its own structure check names its blocks, block-kind settings such
// as coil tiers come from the blocks that stand there when one kind is used throughout, and size settings are found
// by simulating each candidate value and keeping the one that matches the site and covers most of the machine.
@EventBusSubscriber(phase = Phase.PRE)
public class ProjectorReading {

    public static final int MAX_SIZE_VALUE = 16, MISSES_TO_STOP = 2, HEARTBEAT_TICKS = 40, COOLDOWN_TICKS = 100,
        FALLBACK_RANGE = 64;
    private static final Map<UUID, Reading> READINGS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_REQUEST = new HashMap<>();

    public static class Reading {

        public final UUID owner;
        public final int location, dimension, x, y, z, machine;
        public final ExtendedFacing facing;
        public final LongOpenHashSet blocks;
        public final TreeMap<String, Integer> channels = new TreeMap<>();
        public final List<String> sizes = new ArrayList<>();
        public ProjectorSimulation simulation;
        public ProjectorStructure result;
        public ProjectorPlan.Settings wanted;
        public boolean based, ambiguous, unread;
        public int bestSize = -1;
        public int sizeIndex, value = 1, bestValue, bestCoverage = -1, bestScore = Integer.MIN_VALUE, misses, ticks;
        public boolean bestValid;

        public Reading(UUID owner, int location, int dimension, int x, int y, int z, int machine, ExtendedFacing facing,
            LongOpenHashSet blocks) {
            this.owner = owner;
            this.location = location;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.machine = machine;
            this.facing = facing;
            this.blocks = blocks;
        }
    }

    private ProjectorReading() {}

    public static void clear() {
        READINGS.clear();
        LAST_REQUEST.clear();
    }

    public static void request(EntityPlayerMP player, int location, int x, int y, int z) {
        if (ProjectorServer.projectorAt(player, location) == null) return;
        // Each request runs the machine's full structure check twice on the spot, so a player gets one every few
        // seconds; a newer request for another machine replaces the one still running.
        int now = MinecraftServer.getServer()
            .getTickCounter();
        Integer last = LAST_REQUEST.get(player.getUniqueID());
        if (last != null && now - last < COOLDOWN_TICKS) {
            ProjectorBuilder.refuse(player, "too_soon");
            return;
        }
        LAST_REQUEST.put(player.getUniqueID(), now);
        READINGS.remove(player.getUniqueID());
        World world = player.worldObj;
        int range = MachineConfig.projectorBuildRange > 0 ? MachineConfig.projectorBuildRange : FALLBACK_RANGE;
        if (ProjectorBuilder.distanceSq(player, x, y, z, x, y, z) > (double) range * range) {
            ProjectorBuilder.refuse(player, "too_far", range);
            return;
        }
        if (!world.blockExists(x, y, z) || !(world.getTileEntity(x, y, z) instanceof IGregTechTileEntity tile)
            || !ProjectorMachines.isSupported(tile.getMetaTileID())) return;
        if (!(tile.getMetaTileEntity() instanceof IAlignment alignment)) return;
        Reading reading = new Reading(
            player.getUniqueID(),
            location,
            player.dimension,
            x,
            y,
            z,
            tile.getMetaTileID(),
            alignment.getExtendedFacing(),
            ProjectorCapture.blocksOf(world, x, y, z));
        READINGS.put(reading.owner, reading);
        HotbarMessages.send(player, new ChatComponentTranslation(ProjectorBuilder.LANG + "reading"));
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || READINGS.isEmpty()) return;
        long budget = MachineConfig.projectorPlaceBudgetMillis * 1_000_000L / 2 / READINGS.size();
        Iterator<Reading> iterator = READINGS.values()
            .iterator();
        while (iterator.hasNext()) {
            Reading reading = iterator.next();
            EntityPlayerMP owner = ProjectorBuilder.playerOf(reading.owner);
            if (owner == null || owner.dimension != reading.dimension) {
                iterator.remove();
                continue;
            }
            try {
                if (++reading.ticks % HEARTBEAT_TICKS == 0)
                    HotbarMessages.send(owner, new ChatComponentTranslation(ProjectorBuilder.LANG + "reading"));
                if (!advance(owner, reading, System.nanoTime() + budget)) continue;
                iterator.remove();
                write(owner, reading);
            } catch (RuntimeException e) {
                ScienceNotLeisure.LOG.warn("Projector reading for {} failed", owner.getCommandSenderName(), e);
                iterator.remove();
                ProjectorBuilder.refuse(owner, "read_failed");
            }
        }
    }

    // True once every setting is settled.
    private static boolean advance(EntityPlayerMP owner, Reading reading, long deadline) {
        while (System.nanoTime() < deadline) {
            TreeMap<String, Integer> trying = new TreeMap<>(reading.channels);
            if (reading.based && reading.sizeIndex < reading.sizes.size())
                trying.put(reading.sizes.get(reading.sizeIndex), reading.value);
            ProjectorStructure structure = structure(reading, trying, deadline);
            if (structure == null) return false;
            if (!reading.based) {
                readKinds(owner.worldObj, reading, structure);
                reading.based = true;
                continue;
            }
            if (reading.sizeIndex >= reading.sizes.size()) {
                reading.result = structure;
                return true;
            }
            judge(owner.worldObj, reading, structure);
        }
        return false;
    }

    private static ProjectorStructure structure(Reading reading, TreeMap<String, Integer> channels, long deadline) {
        ProjectorPlan plan = new ProjectorPlan();
        plan.selectMachine(reading.machine);
        for (Map.Entry<String, Integer> entry : channels.entrySet()) plan.setChannel(entry.getKey(), entry.getValue());
        ProjectorPlan.Settings settings = plan.settings();
        ProjectorStructure known = ProjectorSimulation.shared(settings);
        if (known != null) return known;
        if (reading.simulation == null || !settings.equals(reading.wanted)) {
            reading.simulation = new ProjectorSimulation(settings, ProjectorSimulation.SERVER_BUDGET);
            reading.wanted = settings;
        }
        if (!reading.simulation.step(Math.max(1, deadline - System.nanoTime()))) return null;
        ProjectorStructure result = reading.simulation.result();
        ProjectorSimulation.share(result);
        reading.simulation = null;
        return result;
    }

    // A block-kind setting counts only when every spot it governs holds the same known kind.
    private static void readKinds(World world, Reading reading, ProjectorStructure structure) {
        ProjectorSite site = new ProjectorSite(structure, reading.facing, reading.x, reading.y, reading.z, new int[3]);
        Map<String, Integer> found = new HashMap<>();
        List<String> mixed = new ArrayList<>();
        for (int cell = 0; cell < structure.size; cell++) {
            Choice choice = structure.choiceAt(cell);
            if (choice == null || choice.channel() == null || choice.tiers().length == 0) continue;
            int wx = site.worldX[cell], wy = site.worldY[cell], wz = site.worldZ[cell];
            if (!world.blockExists(wx, wy, wz)) continue;
            Block block = world.getBlock(wx, wy, wz);
            Item item = Item.getItemFromBlock(block);
            int tier = item == null ? 0
                : choice.tierOf(
                    new ItemStack(
                        item,
                        1,
                        ProjectorStructure.damageOf(world, block, world.getBlockMetadata(wx, wy, wz), wx, wy, wz)));
            Integer before = found.putIfAbsent(choice.channel(), tier);
            if (tier == 0 || before != null && before != tier) mixed.add(choice.channel());
        }
        for (Map.Entry<String, Integer> entry : found.entrySet())
            if (!mixed.contains(entry.getKey())) reading.channels.put(entry.getKey(), entry.getValue());
        for (String channel : structure.channels) {
            if (found.containsKey(channel) || channel.equals(GTStructureChannels.HATCH.get())
                || channel.equals(GTNLStructureChannels.STRUCTURE_RENDER.get())) continue;
            reading.sizes.add(channel);
        }
        if (!reading.sizes.isEmpty()) reading.value = 1;
    }

    // A value fits when every block it asks for stands right; among those, the one covering most of the built
    // machine wins. Two different values covering the same are reported as uncertain.
    private static void judge(World world, Reading reading, ProjectorStructure structure) {
        ProjectorSite site = new ProjectorSite(structure, reading.facing, reading.x, reading.y, reading.z, new int[3]);
        int coverage = 0, bad = structure.failure.isEmpty() && !structure.isEmpty() ? 0 : 1;
        for (int cell = 0; cell < structure.size; cell++) {
            byte state = site.compute(world, cell);
            if (state == ProjectorSite.MISSING || state == ProjectorSite.WRONG) bad++;
            else if (state == ProjectorSite.CORRECT && reading.blocks
                .contains(CoordinatePacker.pack(site.worldX[cell], site.worldY[cell], site.worldZ[cell]))) coverage++;
        }
        boolean valid = bad == 0;
        if (valid && (!reading.bestValid || coverage > reading.bestCoverage)) {
            reading.bestSize = structure.size;
            reading.ambiguous = false;
            reading.bestValid = true;
            reading.bestCoverage = coverage;
            reading.bestValue = reading.value;
        } else if (valid && coverage == reading.bestCoverage && structure.size != reading.bestSize) {
            // Machines clamp values past their largest size; an equal structure is the same answer, not a doubt.
            reading.ambiguous = true;
        } else if (!reading.bestValid && coverage - bad > reading.bestScore) {
            reading.bestScore = coverage - bad;
            reading.bestValue = reading.value;
        }
        reading.misses = valid ? 0 : reading.misses + 1;
        boolean done = reading.value >= MAX_SIZE_VALUE || reading.bestValid && reading.misses >= MISSES_TO_STOP;
        if (!done) {
            reading.value++;
            return;
        }
        if (!reading.bestValid) reading.unread = true;
        reading.channels.put(reading.sizes.get(reading.sizeIndex), reading.bestValue);
        reading.bestSize = -1;
        reading.sizeIndex++;
        reading.value = 1;
        reading.bestValue = 0;
        reading.bestCoverage = -1;
        reading.bestScore = Integer.MIN_VALUE;
        reading.bestValid = false;
        reading.misses = 0;
    }

    private static void write(EntityPlayerMP owner, Reading reading) {
        ItemStack stack = ProjectorServer.projectorAt(owner, reading.location);
        if (stack == null) return;
        ProjectorPlan plan = ProjectorPlan.read(stack);
        if (plan.machine != reading.machine || plan.x != reading.x || plan.y != reading.y || plan.z != reading.z)
            return;
        plan.channels.clear();
        for (Map.Entry<String, Integer> entry : reading.channels.entrySet())
            plan.setChannel(entry.getKey(), entry.getValue());
        int hatches = reading.result == null ? 0 : readHatches(owner.worldObj, reading, plan, reading.result);
        plan.write(stack);
        WirelessCardInventory.sync(owner, stack);
        owner.addChatMessage(
            new ChatComponentTranslation(
                ProjectorBuilder.LANG
                    + (reading.unread ? "read_unmatched" : reading.ambiguous ? "read_uncertain" : "read_done"),
                describe(reading.channels),
                hatches));
    }

    private static String describe(TreeMap<String, Integer> channels) {
        if (channels.isEmpty()) return "-";
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : channels.entrySet()) parts.add(entry.getKey() + "=" + entry.getValue());
        return String.join(", ", parts);
    }

    // The hatches the built machine holds go into the plan by role, so the projection shows the same ones.
    private static int readHatches(World world, Reading reading, ProjectorPlan plan, ProjectorStructure structure) {
        plan.clearHatches();
        ProjectorSite site = new ProjectorSite(structure, reading.facing, reading.x, reading.y, reading.z, new int[3]);
        int read = 0;
        for (int cell = 0; cell < structure.size; cell++) {
            ProjectorStructure.HatchSlot slot = structure.slotAt(cell);
            int rank = structure.rankOf(cell);
            if (slot == null || rank < 0) continue;
            int wx = site.worldX[cell], wy = site.worldY[cell], wz = site.worldZ[cell];
            if (!world.blockExists(wx, wy, wz)
                || !(world.getTileEntity(wx, wy, wz) instanceof IGregTechTileEntity tile)) continue;
            int id = tile.getMetaTileID();
            for (int accepted : slot.accepted()) {
                if (accepted != id) continue;
                plan.setHatch(slot.role(), rank, id);
                read++;
                break;
            }
        }
        return read;
    }
}
