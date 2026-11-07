package com.science.gtnl.client.wireless;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.world.WorldEvent;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.event.MouseInputEvent;
import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.packet.RemoteDeviceRequestPacket;
import com.science.gtnl.common.wireless.WirelessChannelManager.Address;
import com.science.gtnl.common.wireless.remote.IRemoteDeviceGui;
import com.science.gtnl.common.wireless.remote.RemoteDeviceGuis;
import com.science.gtnl.common.wireless.remote.RemoteDeviceHost;
import com.science.gtnl.common.wireless.remote.RemoteDeviceMirror;
import com.science.gtnl.mixins.early.minecraft.AccessorGuiContainer;

import codechicken.nei.api.API;
import codechicken.nei.api.INEIGuiAdapter;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.metatileentity.BaseMetaTileEntity;

@SideOnly(Side.CLIENT)
@EventBusSubscriber(side = Side.CLIENT)
public class RemoteDeviceClient {

    private static final int BUTTON_HEIGHT = 20, BUTTON_GAP = 1, BUTTON_PADDING = 12;
    private static final ResourceLocation CLICK = new ResourceLocation("gui.button.press");
    private static final GuiButton RETURN = new GuiButton(0, 0, 0, 0, BUTTON_HEIGHT, "");
    private static Session current;
    private static WirelessCardClientView.State cardState;
    private static boolean neiExclusion;

    private static class Session {

        private final long id;
        private final RemoteDeviceHost mirror;
        private final Container container;

        private Session(long id, RemoteDeviceHost mirror, Container container) {
            this.id = id;
            this.mirror = mirror;
            this.container = container;
        }
    }

    public static void open(Minecraft minecraft, long id, int windowId, String gui, int screen, Address address,
        @Nullable byte[] data) {
        EntityClientPlayerMP player = minecraft.thePlayer;
        if (player == null) return;
        try {
            if (!neiExclusion) {
                API.registerNEIGuiHandler(new NeiExclusion());
                neiExclusion = true;
            }
            if (data == null) {
                if (player.openContainer != null && player.openContainer.windowId == windowId) {
                    current = new Session(id, null, player.openContainer);
                    return;
                }
            } else {
                NBTTagCompound mirror = RemoteDeviceMirror.decode(data);
                RemoteDeviceHost host = current != null && current.id == id && current.mirror != null ? current.mirror
                    : build(minecraft.theWorld, address, mirror);
                IRemoteDeviceGui adapter = RemoteDeviceGuis.byId(gui);
                Object created = host == null || adapter == null ? null
                    : adapter.clientScreen(player, host, screen, mirror);
                if (created instanceof GuiContainer container) {
                    container.inventorySlots.windowId = windowId;
                    minecraft.displayGuiScreen(container);
                    current = new Session(id, host, player.openContainer);
                    return;
                }
            }
        } catch (RuntimeException failure) {
            ScienceNotLeisure.LOG
                .error("Cannot build remote device screen {} through {} at {}", screen, gui, address, failure);
        }
        current = null;
        cardState = null;
        ScienceNotLeisure.network
            .sendToServer(new RemoteDeviceRequestPacket(RemoteDeviceRequestPacket.FAILED, id, windowId));
    }

    private static @Nullable RemoteDeviceHost build(World client, Address address, NBTTagCompound data) {
        Block block = Block.getBlockById(data.getInteger("block"));
        int meta = data.getInteger("meta");
        if (block == null || !block.hasTileEntity(meta)) return null;
        var world = new RemoteDeviceMirrorWorld(client, address, block, meta);
        if (data.hasKey("machine")) {
            BaseMetaTileEntity machine = new BaseMetaTileEntity();
            machine.setInitialValuesAsNBT(null, data.getShort("machine"));
            world.place(machine);
        } else {
            TileEntity tile = world.place(block.createTileEntity(world, meta));
            if (tile != null && data.hasKey("description")) tile.onDataPacket(
                Minecraft.getMinecraft()
                    .getNetHandler()
                    .getNetworkManager(),
                new S35PacketUpdateTileEntity(
                    address.x(),
                    address.y(),
                    address.z(),
                    data.getInteger("action"),
                    data.getCompoundTag("description")));
        }
        return RemoteDeviceHost.resolve(world.tile(), address.side());
    }

    public static void rememberCard(WirelessCardClientView.State state) {
        cardState = state;
        current = null;
    }

    public static @Nullable WirelessCardClientView.State takeCardState(long session) {
        // Only the server's successful return identifies which remote session this card screen belongs to.
        if (current == null || current.id != session || cardState == null) return null;
        var state = cardState;
        cardState = null;
        current = null;
        return state;
    }

    private static @Nullable GuiContainer remoteScreen(GuiScreen screen) {
        return current != null && screen instanceof GuiContainer container
            && container.inventorySlots == current.container
            && Minecraft.getMinecraft().thePlayer.openContainer == current.container ? container : null;
    }

    private static void placeButton(GuiContainer container) {
        Minecraft minecraft = Minecraft.getMinecraft();
        var bounds = (AccessorGuiContainer) container;
        String label = StatCollector.translateToLocal("gtnl.wireless.gui.remote.return");
        int width = minecraft.fontRenderer.getStringWidth(label) + BUTTON_PADDING;
        int y = bounds.gtnl$getGuiTop() - BUTTON_HEIGHT - BUTTON_GAP;
        if (y < 0) y = bounds.gtnl$getGuiTop() + bounds.gtnl$getYSize() + BUTTON_GAP;
        RETURN.xPosition = Math.max(0, Math.min(bounds.gtnl$getGuiLeft(), container.width - width));
        RETURN.yPosition = Math.min(y, container.height - BUTTON_HEIGHT);
        RETURN.width = width;
        RETURN.displayString = label;
    }

    @SubscribeEvent
    public static void draw(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiContainer container = remoteScreen(event.gui);
        if (container == null) return;
        placeButton(container);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glDisable(GL11.GL_LIGHTING);
        RETURN.drawButton(Minecraft.getMinecraft(), event.mouseX, event.mouseY);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void click(MouseInputEvent.Pre event) {
        if (!Mouse.getEventButtonState() || Mouse.getEventButton() != 0) return;
        GuiContainer container = remoteScreen(event.gui);
        if (container == null) return;
        placeButton(container);
        Minecraft minecraft = Minecraft.getMinecraft();
        int mouseX = Mouse.getEventX() * container.width / minecraft.displayWidth;
        int mouseY = container.height - Mouse.getEventY() * container.height / minecraft.displayHeight - 1;
        if (!RETURN.mousePressed(minecraft, mouseX, mouseY)) return;
        event.setCanceled(true);
        minecraft.getSoundHandler()
            .playSound(PositionedSoundRecord.func_147674_a(CLICK, 1.0F));
        ScienceNotLeisure.network.sendToServer(
            new RemoteDeviceRequestPacket(RemoteDeviceRequestPacket.RETURN, current.id, current.container.windowId));
    }

    private static class NeiExclusion extends INEIGuiAdapter {

        @Override
        public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int width, int height) {
            if (remoteScreen(gui) == null) return false;
            placeButton(gui);
            return x < RETURN.xPosition + RETURN.width && x + width > RETURN.xPosition
                && y < RETURN.yPosition + RETURN.height
                && y + height > RETURN.yPosition;
        }
    }

    @SubscribeEvent
    public static void disconnect(ClientDisconnectionFromServerEvent event) {
        current = null;
        cardState = null;
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload event) {
        if (event.world.isRemote) {
            current = null;
            cardState = null;
        }
    }
}
