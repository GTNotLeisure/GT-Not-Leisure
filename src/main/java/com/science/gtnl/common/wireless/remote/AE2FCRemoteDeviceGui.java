package com.science.gtnl.common.wireless.remote;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import com.glodblock.github.common.parts.PartLevelTerminal;
import com.glodblock.github.common.tile.TileFluidAutoFiller;
import com.glodblock.github.common.tile.TileFluidPacketDecoder;
import com.glodblock.github.common.tile.TileLevelMaintainer;
import com.glodblock.github.common.tile.TileSuperStockReplenisher;
import com.glodblock.github.inventory.gui.GuiType;
import com.glodblock.github.inventory.gui.TileGuiFactory;
import com.glodblock.github.util.BlockPos;
import com.glodblock.github.util.Util;
import com.science.gtnl.mixins.late.aeFluidCraft.AccessorTileGuiFactory;

import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGridHost;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class AE2FCRemoteDeviceGui implements IRemoteDeviceGui {

    public static final AE2FCRemoteDeviceGui INSTANCE = new AE2FCRemoteDeviceGui();

    // Fluid parts keep AE2's right click; block fluid interfaces are mapped explicitly in the AE2 adapter.
    private static final List<Main> MAIN = List.of(
        new Main(TileLevelMaintainer.class, GuiType.LEVEL_MAINTAINER, SecurityPermissions.CRAFT),
        new Main(TileFluidAutoFiller.class, GuiType.FLUID_AUTO_FILLER, SecurityPermissions.INJECT),
        new Main(TileFluidPacketDecoder.class, GuiType.FLUID_PACKET_DECODER, null),
        new Main(TileSuperStockReplenisher.class, GuiType.GUI_SUPER_STOCK_REPLENISHER, null),
        new Main(PartLevelTerminal.class, GuiType.LEVEL_TERMINAL, null));

    private record Main(Class<?> type, GuiType screen, @Nullable SecurityPermissions permission) {}

    @Override
    public String id() {
        return "ae2fc";
    }

    @Override
    public int mainScreen(Object device, EntityPlayer player) {
        Main main = main(device);
        return main == null ? NO_GUI
            : main.screen()
                .ordinal();
    }

    @Override
    public RemoteDeviceState open(RemoteDeviceSession session, int screen) {
        GuiType type = GuiType.getByOrdinal(screen);
        if (type == null) return RemoteDeviceState.NO_GUI;
        Main main = main(session.host.device());
        if (main != null && main.screen() == type
            && main.permission() != null
            && session.host.tile() instanceof IGridHost host
            && !Util.hasPermission(session.player, main.permission(), host)) return RemoteDeviceState.DENIED;
        TileEntity tile = session.host.tile();
        Object container = type.guiFactory.createServerGui(
            session.player,
            tile.getWorldObj(),
            tile.xCoord,
            tile.yCoord,
            tile.zCoord,
            session.host.side());
        if (!(container instanceof Container opened)) return RemoteDeviceState.NO_GUI;
        return RemoteDeviceSessions.show(session, this, opened, screen);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public @Nullable Object clientScreen(EntityPlayer player, RemoteDeviceHost mirror, int screen,
        NBTTagCompound data) {
        GuiType type = GuiType.getByOrdinal(screen);
        if (type == null || !(type.guiFactory instanceof TileGuiFactory<?>factory)) return null;
        var access = (AccessorTileGuiFactory) factory;
        Object inventory = access.gtnl$getInventory(mirror.tile(), mirror.side());
        return inventory == null ? null : access.gtnl$createClientGui(player, inventory);
    }

    // AE2FC's own page switch passes the player's world instead of the device's, so only the position is compared.
    public static boolean openInSession(EntityPlayer player, World world, BlockPos pos, Object screen) {
        if (!(player instanceof EntityPlayerMP serverPlayer) || !(screen instanceof GuiType type)) return false;
        var session = RemoteDeviceSessions.of(serverPlayer);
        if (session == null) return false;
        TileEntity tile = session.host.tile();
        if (world != tile.getWorldObj() && world != player.worldObj || pos.getX() != tile.xCoord
            || pos.getY() != tile.yCoord
            || pos.getZ() != tile.zCoord) return false;
        RemoteDeviceSessions.reopen(session, INSTANCE, type.ordinal());
        return true;
    }

    private static @Nullable Main main(Object device) {
        for (Main main : MAIN) if (main.type()
            .isInstance(device)) return main;
        return null;
    }
}
