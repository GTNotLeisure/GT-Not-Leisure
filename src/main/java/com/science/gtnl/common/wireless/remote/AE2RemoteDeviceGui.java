package com.science.gtnl.common.wireless.remote;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.Nullable;

import com.glodblock.github.common.tile.TileFluidInterface;

import appeng.api.parts.IAdvancedLevelEmitter;
import appeng.api.parts.ICraftingTerminal;
import appeng.api.parts.IInterfaceTerminal;
import appeng.api.parts.ILevelEmitter;
import appeng.api.parts.IPart;
import appeng.api.parts.IPatternTerminal;
import appeng.api.parts.IPatternTerminalEx;
import appeng.api.parts.IStorageBus;
import appeng.api.storage.ITerminalHost;
import appeng.block.AEBaseBlock;
import appeng.block.qnb.BlockQuantumLinkChamber;
import appeng.container.AEBaseContainer;
import appeng.container.ContainerOpenContext;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.GuiHostType;
import appeng.helpers.IInterfaceHost;
import appeng.parts.automation.PartBaseFormationPlane;
import appeng.parts.automation.PartSharedItemBus;
import appeng.parts.reporting.AbstractPartTerminal;
import appeng.tile.crafting.TileCraftingTile;
import appeng.tile.crafting.TileMolecularAssembler;
import appeng.tile.misc.TileAdvancedInscriber;
import appeng.tile.misc.TileCrystalGrowthChamber;
import appeng.tile.misc.TileInscriber;
import appeng.tile.misc.TileSecurity;
import appeng.tile.misc.TileStorageReshuffle;
import appeng.tile.misc.TileSuperMEReplenisher;
import appeng.tile.misc.TileVibrationChamber;
import appeng.tile.networking.TileWireless;
import appeng.tile.qnb.TileQuantumBridge;
import appeng.tile.spatial.TileSpatialIOPort;
import appeng.tile.spatial.TileSpatialLinkChamber;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;
import appeng.tile.storage.TileIOPort;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class AE2RemoteDeviceGui implements IRemoteDeviceGui {

    public static final AE2RemoteDeviceGui INSTANCE = new AE2RemoteDeviceGui();

    // Terminal hosts stay last: ME chests and security stations are terminal hosts with screens of their own.
    private static final List<Main> MAIN = List.of(
        new Main(TileChest.class, GuiBridge.GUI_CHEST),
        new Main(TileDrive.class, GuiBridge.GUI_DRIVE),
        new Main(TileIOPort.class, GuiBridge.GUI_IOPORT),
        new Main(TileMolecularAssembler.class, GuiBridge.GUI_MAC),
        new Main(TileInscriber.class, GuiBridge.GUI_INSCRIBER),
        new Main(TileAdvancedInscriber.class, GuiBridge.GUI_ADVANCED_INSCRIBER),
        new Main(TileCrystalGrowthChamber.class, GuiBridge.GUI_CRYSTAL_GROWTH_CHAMBER),
        new Main(TileVibrationChamber.class, GuiBridge.GUI_VIBRATION_CHAMBER),
        new Main(TileCraftingTile.class, GuiBridge.GUI_CRAFTING_CPU),
        new Main(TileSecurity.class, GuiBridge.GUI_SECURITY),
        new Main(TileWireless.class, GuiBridge.GUI_WIRELESS),
        new Main(TileSpatialIOPort.class, GuiBridge.GUI_SPATIAL_IO_PORT),
        new Main(TileSpatialLinkChamber.class, GuiBridge.GUI_SPATIAL_LINK_CHAMBER),
        new Main(TileStorageReshuffle.class, GuiBridge.GUI_STORAGE_RESHUFFLE),
        new Main(TileSuperMEReplenisher.class, GuiBridge.GUI_SUPER_ME_REPLENISHER),
        new Main(IInterfaceHost.class, GuiBridge.GUI_INTERFACE),
        new Main(IStorageBus.class, GuiBridge.GUI_STORAGEBUS),
        new Main(PartSharedItemBus.class, GuiBridge.GUI_BUS),
        new Main(PartBaseFormationPlane.class, GuiBridge.GUI_FORMATION_PLANE),
        new Main(IAdvancedLevelEmitter.class, GuiBridge.GUI_ADVANCED_LEVEL_EMITTER),
        new Main(ILevelEmitter.class, GuiBridge.GUI_LEVEL_EMITTER),
        new Main(IInterfaceTerminal.class, GuiBridge.GUI_INTERFACE_TERMINAL),
        new Main(IPatternTerminalEx.class, GuiBridge.GUI_PATTERN_TERMINAL_EX),
        new Main(IPatternTerminal.class, GuiBridge.GUI_PATTERN_TERMINAL),
        new Main(ICraftingTerminal.class, GuiBridge.GUI_CRAFTING_TERMINAL),
        new Main(ITerminalHost.class, GuiBridge.GUI_ME));

    private record Main(Class<?> type, GuiBridge screen) {}

    private static final ClassValue<Boolean> OPENS_LIKE_AE2 = new ClassValue<>() {

        @Override
        protected Boolean computeValue(Class<?> type) {
            if (IPart.class.isAssignableFrom(type))
                return declaredByAE2(type, "onActivate", EntityPlayer.class, Vec3.class)
                    && declaredByAE2(type, "onPartActivate", EntityPlayer.class, Vec3.class);
            return AEBaseBlock.class.isAssignableFrom(type) && declaredByAE2(
                type,
                "onActivated",
                World.class,
                int.class,
                int.class,
                int.class,
                EntityPlayer.class,
                int.class,
                float.class,
                float.class,
                float.class);
        }
    };

    @Override
    public String id() {
        return "ae2";
    }

    @Override
    public int mainScreen(Object device, EntityPlayer player) {
        // AE2FC's block overrides onActivated, but still opens the native AE2 interface screen.
        if (device.getClass() == TileFluidInterface.class) return GuiBridge.GUI_INTERFACE.ordinal();
        if (!opensLikeAE2(device)) return NO_GUI;
        if (device instanceof AbstractPartTerminal terminal) return terminal.getGui(player)
            .ordinal();
        if (device instanceof TileQuantumBridge bridge)
            return bridge.getBlockType() instanceof BlockQuantumLinkChamber ? GuiBridge.GUI_QNB.ordinal() : NO_GUI;
        if (device instanceof TileCraftingTile unit && !(unit.isFormed() && unit.isActive())) return NO_GUI;
        for (Main main : MAIN) if (main.type()
            .isInstance(device))
            return main.screen()
                .ordinal();
        return NO_GUI;
    }

    @Override
    public RemoteDeviceState open(RemoteDeviceSession session, int screen) {
        GuiBridge bridge = bridge(screen);
        if (bridge == null) return RemoteDeviceState.NO_GUI;
        TileEntity tile = session.host.tile();
        ForgeDirection side = session.host.side();
        Object target = target(bridge, session.host);
        if (target == null) return RemoteDeviceState.NO_GUI;
        if (!bridge.hasPermissions(tile, tile.xCoord, tile.yCoord, tile.zCoord, side, session.player))
            return RemoteDeviceState.DENIED;
        Object container = bridge.ConstructContainer(session.player.inventory, side, target);
        if (!(container instanceof Container opened)) return RemoteDeviceState.FAILED;
        if (opened instanceof AEBaseContainer base) {
            ContainerOpenContext context = new ContainerOpenContext(target);
            context.setWorld(tile.getWorldObj());
            context.setX(tile.xCoord);
            context.setY(tile.yCoord);
            context.setZ(tile.zCoord);
            context.setSide(side);
            base.setOpenContext(context);
        }
        return RemoteDeviceSessions.show(session, this, opened, screen);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public @Nullable Object clientScreen(EntityPlayer player, RemoteDeviceHost mirror, int screen,
        NBTTagCompound data) {
        GuiBridge bridge = bridge(screen);
        Object target = bridge == null ? null : target(bridge, mirror);
        return target == null ? null : bridge.ConstructGui(player.inventory, mirror.side(), target);
    }

    public static boolean openInSession(EntityPlayer player, TileEntity tile, ForgeDirection side, GuiBridge bridge) {
        if (!(player instanceof EntityPlayerMP serverPlayer) || tile == null) return false;
        var session = RemoteDeviceSessions.of(serverPlayer);
        if (session == null || session.host.tile() != tile
            || session.host.part() != null && side != session.host.side()
            || bridge.getType() == GuiHostType.ITEM) return false;
        RemoteDeviceSessions.reopen(session, INSTANCE, bridge.ordinal());
        return true;
    }

    // Add-on subclasses that keep AE2's right click handler open the same AE2 screen; overriding it means their own.
    public static boolean opensLikeAE2(Object device) {
        return OPENS_LIKE_AE2.get(
            device instanceof TileEntity tile ? tile.getBlockType()
                .getClass() : device.getClass());
    }

    private static boolean declaredByAE2(Class<?> type, String method, Class<?>... parameters) {
        try {
            return type.getMethod(method, parameters)
                .getDeclaringClass()
                .getName()
                .startsWith("appeng.");
        } catch (NoSuchMethodException missing) {
            return false;
        }
    }

    private static @Nullable GuiBridge bridge(int screen) {
        GuiBridge[] bridges = GuiBridge.values();
        return screen >= 0 && screen < bridges.length ? bridges[screen] : null;
    }

    private static @Nullable Object target(GuiBridge bridge, RemoteDeviceHost host) {
        if (bridge.getType() == GuiHostType.ITEM) return null;
        if (host.part() != null && bridge.CorrectTileOrPart(host.part())) return host.part();
        return bridge.CorrectTileOrPart(host.tile()) ? host.tile() : null;
    }
}
