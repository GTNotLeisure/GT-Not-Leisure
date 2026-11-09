package com.science.gtnl.common.wireless;

import static com.science.gtnl.common.wireless.WirelessCardActions.CYCLE_VISUALISATION;
import static com.science.gtnl.common.wireless.WirelessCardActions.HIGHLIGHT;
import static com.science.gtnl.common.wireless.WirelessCardActions.OPEN_DEVICE;
import static com.science.gtnl.common.wireless.WirelessCardActions.REFRESH;
import static com.science.gtnl.common.wireless.WirelessCardActions.RENAME_CLUSTER;
import static com.science.gtnl.common.wireless.WirelessCardActions.RENAME_DEVICE;
import static com.science.gtnl.common.wireless.WirelessCardActions.RENAME_GROUP;
import static com.science.gtnl.common.wireless.WirelessCardActions.SET_VISUALISATION;
import static com.science.gtnl.common.wireless.WirelessCardActions.TELEPORT;
import static com.science.gtnl.common.wireless.WirelessCardActions.TOGGLE_AUTO;
import static com.science.gtnl.common.wireless.WirelessCardActions.TOGGLE_GROUPING;
import static com.science.gtnl.common.wireless.WirelessCardActions.TOGGLE_PAUSE;
import static com.science.gtnl.common.wireless.WirelessCardActions.isRowAction;

import java.util.Comparator;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;

import com.science.gtnl.common.wireless.WirelessCardSnapshot.Device;
import com.science.gtnl.common.wireless.WirelessChannelManager.Address;
import com.science.gtnl.common.wireless.remote.RemoteDeviceSessions;
import com.science.gtnl.common.wireless.remote.RemoteDeviceState;

public class WirelessCardActionHandler {

    private final WirelessCardContainer session;
    private long nextRequestTick = Long.MIN_VALUE;

    WirelessCardActionHandler(WirelessCardContainer session) {
        this.session = session;
    }

    void execute(long revision, int action, long targetId, String text) {
        EntityPlayer player = session.player;
        ItemStack card = session.card;
        WirelessCardBinding binding = session.binding;
        if (isRowAction(action) && action != OPEN_DEVICE
            && action != TOGGLE_PAUSE
            && action != TELEPORT
            && action != HIGHLIGHT) return;
        if (!acceptRequest(player.worldObj.getTotalWorldTime())) return;
        if (session.revision != revision) {
            session.update(true);
            return;
        }
        var device = session.snapshot == null ? null : session.snapshot.device(targetId);
        if (action == RENAME_DEVICE) {
            try {
                if (binding == null || device == null) message("stale");
                else if (!WirelessOperations.renameDevice(player, binding.frequency(), device, text)) message("denied");
            } catch (WirelessOperations.Denied denied) {
                message("denied");
            } catch (IllegalStateException stale) {
                message("stale");
            }
            session.view.invalidatePresentation();
            session.update(true);
            return;
        }
        if (action == OPEN_DEVICE) {
            if (!openDevice(player, card, binding, device, targetId)) session.update(true);
            return;
        }
        if (device != null && (action == TELEPORT || action == HIGHLIGHT)) {
            if (binding == null) message("stale");
            else if (action == TELEPORT ? teleportDevice(device, binding) : highlightDevice(device, binding)) return;
            session.update(true);
            return;
        }
        var row = session.snapshot == null ? null : session.snapshot.target(targetId);
        if ((isRowAction(action) || action == RENAME_CLUSTER) && (binding == null || row == null)) {
            message("stale");
            session.update(true);
            return;
        }
        if (action == CYCLE_VISUALISATION) {
            WirelessCardVisualisation.cycle(card, player);
        } else if (action == SET_VISUALISATION) {
            if (targetId >= 0 && targetId < WirelessCardVisualisation.MODE_COUNT)
                WirelessCardVisualisation.set(card, player, (int) targetId);
        } else if (action == RENAME_GROUP) {
            var group = session.snapshot == null ? null : session.snapshot.machine(targetId);
            try {
                if (binding == null || group == null
                    || !WirelessOperations.renameGroup(player, binding.frequency(), group, text)) message("stale");
            } catch (WirelessOperations.Denied denied) {
                message("denied");
            }
        } else if (action == RENAME_CLUSTER) {
            try {
                if (!WirelessOperations.renameCluster(player, binding.frequency(), row, text)) message("denied");
            } catch (WirelessOperations.Denied denied) {
                message("denied");
            }
        } else if (action == TELEPORT && player instanceof EntityPlayerMP serverPlayer) {
            try {
                switch (WirelessOperations.teleport(serverPlayer, binding.frequency(), row)) {
                    case STALE -> message("stale");
                    case UNAVAILABLE -> message("teleport_unavailable");
                    case DENIED -> message("denied");
                    case APPLIED -> {
                        return;
                    }
                    default -> {}
                }
            } catch (PhysicalClusterTracker.ScanLimitException limit) {
                message("stale");
            }
        } else if (action == HIGHLIGHT) {
            try {
                if (!WirelessClusterManager.isCurrentLink(binding.frequency(), row)) {
                    message("stale");
                    session.update(true);
                    return;
                }
            } catch (PhysicalClusterTracker.ScanLimitException limit) {
                message("stale");
                return;
            }
            if (row.target()
                .dimension() != player.dimension) {
                message(
                    "highlight_dimension",
                    row.target()
                        .dimension());
            } else {
                var targets = WirelessClusterManager.highlight(row)
                    .stream()
                    .filter(target -> target.dimension() == player.dimension)
                    .sorted(
                        Comparator.comparingDouble(target -> player.getDistanceSq(target.x(), target.y(), target.z())))
                    .limit(512)
                    .toList();
                highlight(targets);
            }
        } else if (action == TOGGLE_AUTO) {
            WirelessAutoConnect.toggleAutomatic(player, card);
        } else if (action == TOGGLE_GROUPING) {
            WirelessCardBinding.toggleGrouping(card);
            WirelessCardInventory.sync(player, card);
        } else if (action == TOGGLE_PAUSE) {
            try {
                WirelessOperations.setPaused(player, binding.frequency(), row);
            } catch (WirelessOperations.Denied denied) {
                message("denied");
            } catch (IllegalStateException stale) {
                message("stale");
            }
        } else if (action != REFRESH) return;
        if (action == REFRESH || action == RENAME_CLUSTER) session.view.invalidatePresentation();
        session.update(true);
    }

    private boolean openDevice(EntityPlayer player, ItemStack card, WirelessCardBinding binding, Device device,
        long targetId) {
        var row = device != null || session.snapshot == null ? null : session.snapshot.target(targetId);
        if (binding == null || device == null && row == null || !(player instanceof EntityPlayerMP serverPlayer)) {
            message("stale");
            return false;
        }
        var node = device != null ? device.node() : session.snapshot.remoteTarget(targetId);
        // The session follows the card by identity while it moves through the device screen's inventory.
        if (WirelessCardBinding.identify(card)) WirelessCardInventory.sync(serverPlayer, card);
        var state = RemoteDeviceSessions
            .open(serverPlayer, new WirelessCardInventory.TrackedCard(card, binding), binding, node);
        if (state == RemoteDeviceState.OPEN) return true;
        message("remote." + state.key());
        return false;
    }

    private boolean teleportDevice(Device device, WirelessCardBinding binding) {
        if (!(session.player instanceof EntityPlayerMP serverPlayer)) return false;
        try {
            switch (WirelessOperations.teleport(serverPlayer, binding.frequency(), device.link(), device.node())) {
                case STALE -> message("stale");
                case UNAVAILABLE -> message("teleport_unavailable");
                case DENIED -> message("denied");
                case APPLIED -> {
                    return true;
                }
                default -> {}
            }
        } catch (PhysicalClusterTracker.ScanLimitException limit) {
            message("stale");
        }
        return false;
    }

    private boolean highlightDevice(Device device, WirelessCardBinding binding) {
        try {
            if (!WirelessClusterManager.isCurrentLink(binding.frequency(), device.link()) || !device.link()
                .members()
                .contains(device.node())) {
                message("stale");
                return false;
            }
        } catch (PhysicalClusterTracker.ScanLimitException limit) {
            message("stale");
            return true;
        }
        var address = WirelessClusterManager.deviceAddress(device.node());
        if (address == null) {
            message("stale");
            return false;
        }
        if (address.dimension() != session.player.dimension) message("highlight_dimension", address.dimension());
        else highlight(List.of(address));
        return false;
    }

    private void highlight(List<Address> targets) {
        session.pendingHighlight = new NBTTagList();
        for (var target : targets) {
            NBTTagCompound pos = new NBTTagCompound();
            pos.setInteger("dimension", target.dimension());
            pos.setInteger("x", target.x());
            pos.setInteger("y", target.y());
            pos.setInteger("z", target.z());
            session.pendingHighlight.appendTag(pos);
        }
        message("highlight_started", targets.size());
    }

    private boolean acceptRequest(long tick) {
        if (tick < nextRequestTick) return false;
        nextRequestTick = tick + 4;
        return true;
    }

    private void message(String key, Object... args) {
        session.player.addChatMessage(new ChatComponentTranslation("gtnl.wireless.gui." + key, args));
    }
}
