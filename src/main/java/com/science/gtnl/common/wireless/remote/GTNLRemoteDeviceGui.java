package com.science.gtnl.common.wireless.remote;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.Nullable;

import com.science.gtnl.client.gui.GTNLClientGuiHandler;
import com.science.gtnl.common.block.blocks.tile.TileEntityMEChisel;
import com.science.gtnl.common.block.blocks.tile.TileEntitySuperDualInterface;
import com.science.gtnl.common.block.blocks.tile.TileEntitySuperInterface;
import com.science.gtnl.common.gui.GTNLGuiHandler;
import com.science.gtnl.common.part.PartActiveFormationPlane;
import com.science.gtnl.common.part.PartMECellDock;
import com.science.gtnl.common.part.PartSuperDualInterface;
import com.science.gtnl.common.part.PartSuperInterface;
import com.science.gtnl.common.part.PartUniversalExportBus;
import com.science.gtnl.common.part.PartUniversalImportBus;
import com.science.gtnl.common.part.PartUniversalStorageBus;
import com.science.gtnl.utils.enums.GuiType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class GTNLRemoteDeviceGui implements IRemoteDeviceGui {

    public static final GTNLRemoteDeviceGui INSTANCE = new GTNLRemoteDeviceGui();

    // Subclasses before their parents: the dual interface extends the plain one.
    private static final List<Main> MAIN = List.of(
        new Main(PartSuperDualInterface.class, GuiType.SuperDualInterfaceGUI),
        new Main(PartSuperInterface.class, GuiType.SuperInterfaceGUI),
        new Main(PartActiveFormationPlane.class, GuiType.ActiveFormationPlaneGUI),
        new Main(PartUniversalStorageBus.class, GuiType.UniversalStorageBusGUI),
        new Main(PartUniversalImportBus.class, GuiType.UniversalBusGUI),
        new Main(PartUniversalExportBus.class, GuiType.UniversalBusGUI),
        new Main(PartMECellDock.class, GuiType.MECellDockGUI),
        new Main(TileEntitySuperDualInterface.class, GuiType.SuperDualInterfaceGUI),
        new Main(TileEntitySuperInterface.class, GuiType.SuperInterfaceGUI),
        new Main(TileEntityMEChisel.class, GuiType.MEChiselGUI));

    private record Main(Class<?> type, GuiType screen) {}

    @Override
    public String id() {
        return "gtnl";
    }

    @Override
    public int mainScreen(Object device, EntityPlayer player) {
        for (Main main : MAIN) if (main.type()
            .isInstance(device))
            return main.screen()
                .getID();
        return NO_GUI;
    }

    @Override
    public RemoteDeviceState open(RemoteDeviceSession session, int screen) {
        GuiType type = type(screen);
        if (type == null) return RemoteDeviceState.NO_GUI;
        Object container = GTNLGuiHandler.tileElement(type, session.host.side(), session.player, session.host.tile());
        if (!(container instanceof Container opened)) return RemoteDeviceState.NO_GUI;
        return RemoteDeviceSessions.show(session, this, opened, screen);
    }

    @Override
    public void describe(Object device, NBTTagCompound data) {
        // A nearby client gets the chisel's parallel from a packet sent just before its screen opens.
        if (device instanceof TileEntityMEChisel chisel) data.setInteger("parallel", chisel.getParallel());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public @Nullable Object clientScreen(EntityPlayer player, RemoteDeviceHost mirror, int screen,
        NBTTagCompound data) {
        GuiType type = type(screen);
        if (type == null) return null;
        if (mirror.tile() instanceof TileEntityMEChisel chisel && data.hasKey("parallel"))
            chisel.setParallel(data.getInteger("parallel"));
        return GTNLClientGuiHandler.tileElement(type, mirror.side(), player, mirror.tile());
    }

    public static boolean openInSession(EntityPlayer player, GuiType type, ForgeDirection side, World world, int x,
        int y, int z) {
        if (!(player instanceof EntityPlayerMP serverPlayer)) return false;
        var session = RemoteDeviceSessions.of(serverPlayer);
        if (session == null) return false;
        TileEntity tile = session.host.tile();
        if (tile.getWorldObj() != world || tile.xCoord != x
            || tile.yCoord != y
            || tile.zCoord != z
            || session.host.part() != null && side != session.host.side()) return false;
        RemoteDeviceSessions.reopen(session, INSTANCE, type.getID());
        return true;
    }

    private static @Nullable GuiType type(int screen) {
        for (GuiType type : GuiType.values()) if (type.getID() == screen) return type;
        return null;
    }
}
