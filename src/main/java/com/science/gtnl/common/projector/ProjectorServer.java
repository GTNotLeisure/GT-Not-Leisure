package com.science.gtnl.common.projector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import com.gtnewhorizon.gtnhlib.eventbus.Phase;
import com.science.gtnl.common.item.items.MultiblockProjector;
import com.science.gtnl.common.wireless.WirelessCardInventory;
import com.science.gtnl.common.wireless.WirelessCardInventory.CardSlot;
import com.science.gtnl.config.MachineConfig;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

// The one server tick for every projector job, and what the builder, dismantler, reading and orders share.
@EventBusSubscriber(phase = Phase.PRE)
public class ProjectorServer {

    public static final String LANG = "gtnl.projector.build.";
    public static final int BUILD_COOLDOWN_TICKS = 40, READ_COOLDOWN_TICKS = 100, QUOTE_COOLDOWN_TICKS = 20;
    public static final Cooldown BUILDS = new Cooldown(BUILD_COOLDOWN_TICKS), READS = new Cooldown(READ_COOLDOWN_TICKS),
        QUOTES = new Cooldown(QUOTE_COOLDOWN_TICKS);

    // Ticks since a player's last request, so one player cannot start heavy work every tick.
    public static class Cooldown {

        private final Map<UUID, Integer> last = new HashMap<>();
        private final int ticks;

        public Cooldown(int ticks) {
            this.ticks = ticks;
        }

        public boolean ready(UUID id) {
            Integer before = last.get(id);
            return before == null || now() - before >= ticks;
        }

        public boolean pass(UUID id) {
            if (!ready(id)) return false;
            last.put(id, now());
            return true;
        }

        public boolean take(EntityPlayerMP player) {
            if (pass(player.getUniqueID())) return true;
            refuse(player, "too_soon");
            return false;
        }

        public void prune() {
            if (last.isEmpty()) return;
            int now = now();
            last.values()
                .removeIf(before -> now - before >= ticks);
        }

        public void clear() {
            last.clear();
        }
    }

    // The tick's time for projector work, handed out in equal shares; what a job leaves unused goes to the jobs after
    // it, so any number of jobs costs the server no more than the configured budget.
    public static class Budget {

        private long remaining;
        private int sharing;

        public Budget(long nanos, int sharing) {
            remaining = nanos;
            this.sharing = sharing;
        }

        public long share() {
            return Math.max(0, remaining) / Math.max(1, sharing--);
        }

        public void spend(long nanos) {
            remaining -= nanos;
        }
    }

    private ProjectorServer() {}

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        BUILDS.prune();
        READS.prune();
        QUOTES.prune();
        Budget budget = new Budget(
            MachineConfig.projectorPlaceBudgetMillis * 1_000_000L,
            ProjectorBuilder.running() + ProjectorDismantler.running() + ProjectorReading.running());
        ProjectorBuilder.tick(budget);
        ProjectorDismantler.tick(budget);
        ProjectorReading.tick(budget);
        ProjectorOrders.tick();
        ProjectorBuilder.releaseChunks();
    }

    public static void clear() {
        ProjectorSimulation.clearShared();
        ProjectorDismantler.clear();
        ProjectorBuilder.clear();
        ProjectorOrders.clear();
        ProjectorReading.clear();
        BUILDS.clear();
        READS.clear();
        QUOTES.clear();
    }

    public static boolean busy(UUID id) {
        return ProjectorBuilder.busy(id) || ProjectorDismantler.busy(id);
    }

    public static ItemStack projectorAt(EntityPlayer player, int location) {
        CardSlot slot = WirelessCardInventory.resolve(player, location);
        if (slot == null) return null;
        ItemStack stack = slot.inventory()
            .getStackInSlot(slot.index());
        return stack != null && stack.getItem() instanceof MultiblockProjector ? stack : null;
    }

    public static void applyPlan(EntityPlayerMP player, int location, NBTTagCompound tag) {
        ItemStack stack = projectorAt(player, location);
        if (stack == null) return;
        ProjectorPlan plan = new ProjectorPlan();
        plan.readFromNBT(tag);
        if (plan.hasMachine() && !ProjectorMachines.isSupported(plan.machine)) plan.clear();
        plan.dropUnknownHatches();
        plan.write(stack);
        WirelessCardInventory.sync(player, stack);
    }

    public static void openGui(EntityPlayerMP player, int location) {
        if (projectorAt(player, location) == null) return;
        var factory = GuiFactories.playerInventory();
        if (location < 0) factory.openFromBaubles(player, -location - 1);
        else if (location == 0) factory.openFromMainHand(player);
        else factory.openFromPlayerInventory(player, location - 1);
    }

    public static void refuse(EntityPlayerMP player, String reason, Object... args) {
        player.addChatMessage(new ChatComponentTranslation(LANG + reason, args));
    }

    public static EntityPlayerMP playerOf(UUID id) {
        for (Object entry : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (entry instanceof EntityPlayerMP player && player.getUniqueID()
                .equals(id)) return player;
        }
        return null;
    }

    public static boolean present(EntityPlayerMP player, int dimension) {
        return player != null && player.isEntityAlive() && player.dimension == dimension;
    }

    public static double distanceSq(Entity entity, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        double dx = Math.max(0, Math.max(minX - entity.posX, entity.posX - maxX - 1));
        double dy = Math.max(0, Math.max(minY - entity.posY, entity.posY - maxY - 1));
        double dz = Math.max(0, Math.max(minZ - entity.posZ, entity.posZ - maxZ - 1));
        return dx * dx + dy * dy + dz * dz;
    }

    public static <T extends WorldSavedData> T savedData(Class<T> type, String name, Function<String, T> create) {
        World world = DimensionManager.getWorld(0);
        if (world == null) return null;
        T data = type.cast(world.mapStorage.loadData(type, name));
        if (data == null) {
            data = create.apply(name);
            world.mapStorage.setData(name, data);
        }
        return data;
    }

    private static int now() {
        return MinecraftServer.getServer()
            .getTickCounter();
    }
}
