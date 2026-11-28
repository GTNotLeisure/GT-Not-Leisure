package com.science.gtnl.common.projector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import com.gtnewhorizon.gtnhlib.eventbus.Phase;
import com.gtnewhorizon.structurelib.util.InventoryUtility;
import com.gtnllib.utils.HotbarMessages;
import com.science.gtnl.ScienceNotLeisure;

import appeng.api.AEApi;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.storage.data.IAEItemStack;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

// One order per player: every missing material the ME network can make is requested at once, and the build starts by
// itself once all of them can be taken. The order outlives a restart; the crafts themselves are AE's to keep.
@EventBusSubscriber(phase = Phase.PRE)
public class ProjectorOrders {

    public static final String NAME = "gtnl_projector_orders";
    public static final int CHECK_TICKS = 40, ORDER_TICKS = 36_000, CPU_RETRY_TICKS = 100, CPU_RETRIES = 12,
        PROGRESS_TICKS = 600;

    public static class Queued {

        public final ICraftingJob job;
        public final ItemStack stack;
        public int tries;

        public Queued(ICraftingJob job, ItemStack stack) {
            this.job = job;
            this.stack = stack;
        }
    }

    private static final Map<UUID, Order> ORDERS = new HashMap<>();
    private static boolean loaded;
    private static int ticks;

    public static class Order {

        public final UUID owner;
        public final int location, dimension;
        public final long deadline;
        public final List<ItemStack> wanted;
        public final List<Future<ICraftingJob>> pending = new ArrayList<>();
        public final List<ItemStack> pendingStacks = new ArrayList<>();
        public final List<IChatComponent> failed = new ArrayList<>();
        public final List<Queued> queued = new ArrayList<>();
        public long nextTry;
        public int submitted;

        public Order(UUID owner, int location, int dimension, long deadline, List<ItemStack> wanted) {
            this.owner = owner;
            this.location = location;
            this.dimension = dimension;
            this.deadline = deadline;
            this.wanted = wanted;
        }
    }

    public static class Saved extends WorldSavedData {

        public Saved(String name) {
            super(name);
        }

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            ORDERS.clear();
            NBTTagList list = tag.getTagList("Orders", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                UUID owner;
                try {
                    owner = UUID.fromString(entry.getString("Owner"));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                int[] items = entry.getIntArray("Items"), damages = entry.getIntArray("Damages"),
                    counts = entry.getIntArray("Counts");
                if (damages.length != items.length || counts.length != items.length) continue;
                List<ItemStack> wanted = new ArrayList<>();
                for (int k = 0; k < items.length; k++) {
                    Item item = Item.getItemById(items[k]);
                    if (item != null && counts[k] > 0) wanted.add(new ItemStack(item, counts[k], damages[k]));
                }
                if (wanted.isEmpty()) continue;
                ORDERS.put(
                    owner,
                    new Order(
                        owner,
                        entry.getInteger("Location"),
                        entry.getInteger("Dim"),
                        entry.getLong("Deadline"),
                        wanted));
            }
        }

        @Override
        public void writeToNBT(NBTTagCompound tag) {
            NBTTagList list = new NBTTagList();
            for (Order order : ORDERS.values()) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setString("Owner", order.owner.toString());
                entry.setInteger("Location", order.location);
                entry.setInteger("Dim", order.dimension);
                entry.setLong("Deadline", order.deadline);
                int[] items = new int[order.wanted.size()], damages = new int[items.length],
                    counts = new int[items.length];
                for (int k = 0; k < items.length; k++) {
                    ItemStack stack = order.wanted.get(k);
                    items[k] = Item.getIdFromItem(stack.getItem());
                    damages[k] = stack.getItemDamage();
                    counts[k] = stack.stackSize;
                }
                entry.setIntArray("Items", items);
                entry.setIntArray("Damages", damages);
                entry.setIntArray("Counts", counts);
                list.appendTag(entry);
            }
            tag.setTag("Orders", list);
        }
    }

    private ProjectorOrders() {}

    // The server saves its data after it reports stopping, so the orders stay until the next server loads its own.
    public static void clear() {
        loaded = false;
    }

    private static Saved saved() {
        World world = DimensionManager.getWorld(0);
        if (world == null) return null;
        Saved data = (Saved) world.mapStorage.loadData(Saved.class, NAME);
        if (data == null) {
            data = new Saved(NAME);
            world.mapStorage.setData(NAME, data);
        }
        return data;
    }

    private static void save() {
        Saved data = saved();
        if (data != null) data.markDirty();
    }

    private static long worldTime() {
        World world = DimensionManager.getWorld(0);
        return world == null ? 0 : world.getTotalWorldTime();
    }

    public static boolean place(EntityPlayerMP owner, int location, List<ItemStack> missing) {
        ProjectorNetwork.Link link = ProjectorNetwork
            .link(owner, SecurityPermissions.CRAFT, SecurityPermissions.EXTRACT);
        if (link == null) return false;
        ICraftingGrid crafting = link.grid()
            .getCache(ICraftingGrid.class);
        if (crafting == null) return false;
        Order order = new Order(
            owner.getUniqueID(),
            location,
            owner.dimension,
            worldTime() + ORDER_TICKS,
            new ArrayList<>());
        for (ItemStack stack : missing) {
            IAEItemStack wanted = AEApi.instance()
                .storage()
                .createItemStack(stack);
            if (wanted == null || crafting.getCraftingFor(wanted, null, -1, owner.worldObj)
                .isEmpty()) continue;
            order.pending.add(crafting.beginCraftingJob(owner.worldObj, link.grid(), link.source(), wanted, null));
            order.pendingStacks.add(stack.copy());
            order.wanted.add(stack.copy());
        }
        if (order.wanted.isEmpty()) return false;
        ORDERS.put(order.owner, order);
        save();
        owner.addChatMessage(
            new ChatComponentTranslation(ProjectorBuilder.LANG + "ordered", order.wanted.size(), ORDER_TICKS / 1200));
        return true;
    }

    public static boolean cancel(EntityPlayerMP player, boolean tell) {
        if (ORDERS.remove(player.getUniqueID()) == null) return false;
        save();
        if (tell) player.addChatMessage(new ChatComponentTranslation(ProjectorBuilder.LANG + "order_cancelled"));
        return true;
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!loaded) {
            loaded = true;
            ORDERS.clear();
            saved();
        }
        if (ORDERS.isEmpty()) return;
        boolean check = ++ticks % CHECK_TICKS == 0;
        long now = worldTime();
        Iterator<Order> iterator = ORDERS.values()
            .iterator();
        while (iterator.hasNext()) {
            Order order = iterator.next();
            EntityPlayerMP owner = ProjectorBuilder.playerOf(order.owner);
            try {
                if (now > order.deadline) {
                    iterator.remove();
                    save();
                    if (owner != null) ProjectorBuilder.refuse(owner, "order_expired");
                    continue;
                }
                if (owner == null) continue;
                submit(owner, order, now);
                if (!order.pending.isEmpty() || !order.queued.isEmpty() || !check) continue;
                if (order.wanted.isEmpty()) {
                    iterator.remove();
                    save();
                    continue;
                }
                if (owner.dimension != order.dimension) continue;
                int ready = readyKinds(owner, order);
                if (ticks % PROGRESS_TICKS == 0) HotbarMessages.send(
                    owner,
                    new ChatComponentTranslation(ProjectorBuilder.LANG + "order_progress", ready, order.wanted.size()));
                if (ready < order.wanted.size() || !ProjectorBuilder.canRequest(order.owner)
                    || ProjectorServer.projectorAt(owner, order.location) == null) continue;
                iterator.remove();
                save();
                owner.addChatMessage(new ChatComponentTranslation(ProjectorBuilder.LANG + "order_ready"));
                ProjectorBuilder.request(owner, order.location, true);
            } catch (RuntimeException e) {
                ScienceNotLeisure.LOG.warn("Projector order for {} failed", order.owner, e);
                iterator.remove();
                save();
            }
        }
    }

    // Hands each finished plan to AE. A plan that lacks ingredients leaves the order; one that finds every crafting CPU
    // busy waits and tries again a few times before it gives up.
    private static void submit(EntityPlayerMP owner, Order order, long now) {
        if (order.pending.isEmpty() && order.queued.isEmpty()) return;
        for (int i = order.pending.size() - 1; i >= 0; i--) {
            Future<ICraftingJob> future = order.pending.get(i);
            if (!future.isDone()) continue;
            ItemStack stack = order.pendingStacks.remove(i);
            order.pending.remove(i);
            ICraftingJob job;
            try {
                job = future.get();
            } catch (InterruptedException | ExecutionException e) {
                job = null;
            }
            if (job == null) fail(order, stack, "order_reason_error");
            else if (job.isSimulation()) fail(order, stack, "order_reason_ingredients");
            else order.queued.add(new Queued(job, stack));
        }
        if (!order.queued.isEmpty() && now >= order.nextTry) {
            order.nextTry = now + CPU_RETRY_TICKS;
            ProjectorNetwork.Link link = ProjectorNetwork
                .link(owner, SecurityPermissions.CRAFT, SecurityPermissions.EXTRACT);
            ICraftingGrid crafting = link == null ? null
                : link.grid()
                    .getCache(ICraftingGrid.class);
            Iterator<Queued> queued = order.queued.iterator();
            while (queued.hasNext()) {
                Queued entry = queued.next();
                if (crafting == null) {
                    queued.remove();
                    fail(order, entry.stack, "order_reason_network");
                } else if (crafting.submitJob(entry.job, null, null, false, link.source()) != null) {
                    queued.remove();
                    order.submitted++;
                } else if (++entry.tries >= CPU_RETRIES) {
                    queued.remove();
                    fail(order, entry.stack, "order_reason_cpu");
                }
            }
        }
        if (!order.pending.isEmpty() || !order.queued.isEmpty()) return;
        if (order.submitted > 0) owner
            .addChatMessage(new ChatComponentTranslation(ProjectorBuilder.LANG + "order_submitted", order.submitted));
        order.submitted = 0;
        for (IChatComponent line : order.failed) owner.addChatMessage(line);
        order.failed.clear();
        save();
    }

    private static void fail(Order order, ItemStack stack, String reason) {
        order.failed.add(
            new ChatComponentTranslation(
                ProjectorBuilder.LANG + "order_failed",
                stack.func_151000_E(),
                new ChatComponentTranslation(ProjectorBuilder.LANG + reason)));
        order.wanted.removeIf(wanted -> wanted.isItemEqual(stack));
    }

    private static int readyKinds(EntityPlayerMP owner, Order order) {
        int ready = 0;
        for (ItemStack stack : order.wanted) {
            ItemStack plain = new ItemStack(stack.getItem(), stack.stackSize, stack.getItemDamage());
            if (InventoryUtility.takeFromInventory(owner, plain, true) >= stack.stackSize) ready++;
        }
        return ready;
    }

}
