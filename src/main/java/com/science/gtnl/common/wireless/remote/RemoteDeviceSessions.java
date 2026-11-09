package com.science.gtnl.common.wireless.remote;

import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.player.PlayerOpenContainerEvent;
import net.minecraftforge.event.world.ChunkEvent;

import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.packet.RemoteDeviceOpenPacket;
import com.science.gtnl.common.packet.RemoteDeviceRequestPacket;
import com.science.gtnl.common.wireless.WirelessAutoConnect;
import com.science.gtnl.common.wireless.WirelessCardBinding;
import com.science.gtnl.common.wireless.WirelessCardInventory;
import com.science.gtnl.common.wireless.WirelessCardInventory.TrackedCard;
import com.science.gtnl.common.wireless.WirelessChannelManager;
import com.science.gtnl.common.wireless.WirelessChannelManager.Address;
import com.science.gtnl.common.wireless.WirelessClusterManager;
import com.science.gtnl.common.wireless.WirelessFrequencies;
import com.science.gtnl.common.wireless.WirelessFrequency;

import appeng.api.networking.IGridNode;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

@EventBusSubscriber
public class RemoteDeviceSessions {

    private static final String MESSAGE = "gtnl.wireless.gui.remote.";
    private static final Map<EntityPlayerMP, RemoteDeviceSession> SESSIONS = new Reference2ObjectOpenHashMap<>();
    private static final Map<EntityPlayerMP, RemoteDeviceSession> RETURNS = new Reference2ObjectOpenHashMap<>();
    private static long nextId;

    public record Located(RemoteDeviceState state, @Nullable Address address, @Nullable RemoteDeviceHost host) {}

    public record Availability(boolean hasScreen, RemoteDeviceState state) {}

    private RemoteDeviceSessions() {}

    public static Located locate(IGridNode node) {
        Address address = WirelessClusterManager.deviceAddress(node);
        if (address == null) return new Located(RemoteDeviceState.STALE, null, null);
        World world = DimensionManager.getWorld(address.dimension());
        if (world == null || !world.blockExists(address.x(), address.y(), address.z()))
            return new Located(RemoteDeviceState.UNLOADED, address, null);
        RemoteDeviceHost host = RemoteDeviceHost
            .resolve(world.getTileEntity(address.x(), address.y(), address.z()), address.side());
        if (host == null || !host.owns(node)) return new Located(RemoteDeviceState.STALE, address, null);
        return new Located(RemoteDeviceState.OPEN, address, host);
    }

    public static Availability probe(EntityPlayer player, WirelessFrequency frequency, IGridNode node) {
        var located = locate(node);
        if (located.host() == null) return new Availability(false, located.state());
        Object device = located.host()
            .device();
        if (RemoteDeviceGuis.find(device, player) == null)
            return new Availability(false, RemoteDeviceGuis.withoutScreen(device));
        return new Availability(true, checkAccess(player, frequency, node));
    }

    public static RemoteDeviceState open(EntityPlayerMP player, TrackedCard card, WirelessCardBinding binding,
        IGridNode node) {
        if (!card.carriedBy(player)) return RemoteDeviceState.CARD;
        var located = locate(node);
        if (located.host() == null) return located.state();
        var access = checkAccess(player, binding.frequency(), node);
        if (access != RemoteDeviceState.OPEN) return access;
        Object device = located.host()
            .device();
        var match = RemoteDeviceGuis.find(device, player);
        if (match == null) return RemoteDeviceGuis.withoutScreen(device);
        var session = new RemoteDeviceSession(++nextId, player, card, binding, node, located.address(), located.host());
        Container previous = player.openContainer;
        try {
            return match.gui()
                .open(session, match.screen());
        } catch (RuntimeException failure) {
            close(session);
            // MUI2 may install its container before attach() has registered the remote session.
            if (player.openContainer != previous) {
                try {
                    player.closeScreen();
                } catch (RuntimeException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                } finally {
                    player.openContainer = player.inventoryContainer;
                }
            }
            ScienceNotLeisure.LOG.error(
                "Cannot open remote device screen {} of {} through {} at {}",
                match.screen(),
                device.getClass()
                    .getName(),
                match.gui()
                    .id(),
                located.address(),
                failure);
            return RemoteDeviceState.FAILED;
        }
    }

    static RemoteDeviceState checkAccess(EntityPlayer player, WirelessFrequency frequency, IGridNode node) {
        if (!WirelessClusterManager.isLinked(frequency, node)) return RemoteDeviceState.STALE;
        IGridNode source = WirelessFrequencies.controllerNode(frequency);
        if (source == null || source.getGrid() == null) return RemoteDeviceState.STALE;
        // Paused clusters remain manageable, but the source still authorizes remote access after their grids split.
        return WirelessChannelManager.canBuild(source, player) && WirelessChannelManager.canBuild(node, player)
            ? RemoteDeviceState.OPEN
            : RemoteDeviceState.DENIED;
    }

    public static void reopen(RemoteDeviceSession session, IRemoteDeviceGui gui, int screen) {
        var state = session.check();
        if (state != RemoteDeviceState.OPEN) {
            closeWindow(session, state);
            return;
        }
        try {
            state = gui.open(session, screen);
            if (state != RemoteDeviceState.OPEN) message(session.player, "refused", state);
        } catch (RuntimeException failure) {
            ScienceNotLeisure.LOG.error(
                "Cannot switch remote device {} to screen {} through {} at {}",
                session.host.device()
                    .getClass()
                    .getName(),
                screen,
                gui.id(),
                session.address,
                failure);
            closeWindow(session, RemoteDeviceState.FAILED);
        }
    }

    public static @Nullable RemoteDeviceSession of(EntityPlayerMP player) {
        var session = SESSIONS.get(player);
        return session != null && session.isOpen() ? session : null;
    }

    public static RemoteDeviceState show(RemoteDeviceSession session, IRemoteDeviceGui gui, Container container,
        int screen) {
        EntityPlayerMP player = session.player;
        NBTTagCompound mirror = RemoteDeviceMirror.describe(session.host.tile());
        gui.describe(session.host.device(), mirror);
        byte[] data;
        try {
            data = RemoteDeviceMirror.encode(mirror);
        } catch (IllegalArgumentException failure) {
            ScienceNotLeisure.LOG.warn("Cannot encode remote device data at {}", session.address, failure);
            return RemoteDeviceState.FAILED;
        }
        player.getNextWindowId();
        player.closeContainer();
        int windowId = player.currentWindowId;
        session.container = container;
        player.openContainer = container;
        container.windowId = windowId;
        SESSIONS.put(player, session);
        ScienceNotLeisure.network
            .sendTo(new RemoteDeviceOpenPacket(session.id, windowId, gui.id(), screen, session.address, data), player);
        container.addCraftingToCrafters(player);
        return RemoteDeviceState.OPEN;
    }

    public static boolean attach(RemoteDeviceSession session, IRemoteDeviceGui gui, Container previous, int screen) {
        EntityPlayerMP player = session.player;
        Container opened = player.openContainer;
        if (opened == previous || opened == player.inventoryContainer) return false;
        session.container = opened;
        SESSIONS.put(player, session);
        ScienceNotLeisure.network.sendTo(
            new RemoteDeviceOpenPacket(session.id, opened.windowId, gui.id(), screen, session.address, null),
            player);
        return true;
    }

    public static void request(EntityPlayerMP player, int kind, long id, int windowId) {
        var session = of(player);
        if (session == null || session.id != id || session.container.windowId != windowId) return;
        if (kind == RemoteDeviceRequestPacket.RETURN) {
            returnToCard(player, session);
        } else if (kind == RemoteDeviceRequestPacket.FAILED) {
            close(session);
            player.closeScreen();
            message(player, "closed", RemoteDeviceState.FAILED);
        }
    }

    private static void returnToCard(EntityPlayerMP player, RemoteDeviceSession session) {
        if (!session.card.carriedBy(player)) {
            closeWindow(session, RemoteDeviceState.CARD);
            return;
        }
        ItemStack card = session.card.stack();
        if (WirelessCardInventory.location(player, card) == Integer.MIN_VALUE) {
            player.addChatMessage(new ChatComponentTranslation(MESSAGE + "card_cursor"));
            return;
        }
        close(session);
        RETURNS.put(player, session);
        try {
            WirelessAutoConnect.openGui(player, card);
        } finally {
            RETURNS.remove(player);
        }
    }

    public static @Nullable RemoteDeviceSession takeReturn(EntityPlayer player) {
        return player instanceof EntityPlayerMP serverPlayer && !RETURNS.isEmpty() ? RETURNS.remove(serverPlayer)
            : null;
    }

    public static void beforePacket(INetHandler handler) {
        if (SESSIONS.isEmpty() || !(handler instanceof NetHandlerPlayServer server)) return;
        var session = of(server.playerEntity);
        if (session == null) return;
        var state = session.check();
        if (state != RemoteDeviceState.OPEN) closeWindow(session, state);
    }

    @SubscribeEvent
    public static void interact(PlayerOpenContainerEvent event) {
        if (SESSIONS.isEmpty() || !(event.entityPlayer instanceof EntityPlayerMP player)) return;
        var session = SESSIONS.get(player);
        if (session == null) return;
        if (!session.isOpen()) {
            close(session);
            return;
        }
        var state = session.check();
        if (state == RemoteDeviceState.OPEN && event.canInteractWith) {
            event.setResult(Event.Result.ALLOW);
            return;
        }
        event.setResult(Event.Result.DENY);
        close(session);
        message(player, "closed", state == RemoteDeviceState.OPEN ? RemoteDeviceState.STALE : state);
    }

    @SubscribeEvent
    public static void logout(PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP player) {
            SESSIONS.remove(player);
            RETURNS.remove(player);
        }
    }

    @SubscribeEvent
    public static void changeDimension(PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP player && of(player) != null)
            closeWindow(SESSIONS.get(player), RemoteDeviceState.STALE);
    }

    @SubscribeEvent
    public static void respawn(PlayerRespawnEvent event) {
        UUID id = event.player.getUniqueID();
        SESSIONS.keySet()
            .removeIf(
                player -> player != event.player && player.getUniqueID()
                    .equals(id));
    }

    @SubscribeEvent
    public static void chunkUnload(ChunkEvent.Unload event) {
        if (SESSIONS.isEmpty()) return;
        Chunk chunk = event.getChunk();
        var unloaded = SESSIONS.values()
            .stream()
            .filter(session -> inChunk(session.host.tile(), chunk))
            .toList();
        for (RemoteDeviceSession session : unloaded) {
            if (session.isOpen()) closeWindow(session, RemoteDeviceState.UNLOADED);
            else close(session);
        }
    }

    private static boolean inChunk(TileEntity tile, Chunk chunk) {
        return tile.getWorldObj() == chunk.worldObj && tile.xCoord >> 4 == chunk.xPosition
            && tile.zCoord >> 4 == chunk.zPosition;
    }

    public static void clear() {
        SESSIONS.clear();
        RETURNS.clear();
    }

    private static void closeWindow(RemoteDeviceSession session, RemoteDeviceState reason) {
        close(session);
        session.player.closeScreen();
        message(session.player, "closed", reason);
    }

    private static void close(RemoteDeviceSession session) {
        SESSIONS.remove(session.player, session);
    }

    private static void message(EntityPlayerMP player, String key, RemoteDeviceState reason) {
        if (player.isEntityAlive()) player.addChatMessage(
            new ChatComponentTranslation(MESSAGE + key, new ChatComponentTranslation(MESSAGE + reason.key())));
    }
}
