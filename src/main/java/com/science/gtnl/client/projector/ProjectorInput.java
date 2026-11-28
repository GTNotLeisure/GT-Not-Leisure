package com.science.gtnl.client.projector;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.MouseEvent;

import org.lwjgl.input.Keyboard;

import com.gtnewhorizon.gtnhlib.compat.BaublesCompat;
import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import com.gtnewhorizon.gtnhlib.eventbus.Phase;
import com.gtnewhorizon.gtnhlib.util.AboveHotbarHUD;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.item.items.MultiblockProjector;
import com.science.gtnl.common.packet.OpenProjectorPacket;
import com.science.gtnl.common.projector.ProjectorOrientation;
import com.science.gtnl.common.projector.ProjectorPlan;
import com.science.gtnl.common.projector.ProjectorStructure;
import com.science.gtnl.common.projector.ProjectorStructure.Choice;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@EventBusSubscriber(side = Side.CLIENT, phase = Phase.PRE)
public class ProjectorInput {

    public static final String CATEGORY = "key.categories.gtnl";
    private static final KeyBinding ROTATE = new KeyBinding("key.gtnl.projector_rotate", Keyboard.KEY_R, CATEGORY);
    private static final KeyBinding DISPLAY = new KeyBinding("key.gtnl.projector_display", Keyboard.KEY_N, CATEGORY);
    private static final KeyBinding OPEN = new KeyBinding("key.gtnl.projector_open", Keyboard.KEY_NONE, CATEGORY);
    private static final KeyBinding LOCK = new KeyBinding("key.gtnl.projector_lock", Keyboard.KEY_NONE, CATEGORY);

    private ProjectorInput() {}

    public static void register() {
        ClientRegistry.registerKeyBinding(ROTATE);
        ClientRegistry.registerKeyBinding(DISPLAY);
        ClientRegistry.registerKeyBinding(OPEN);
        ClientRegistry.registerKeyBinding(LOCK);
        ProjectorHud.register();
        ProjectorClient.registerListener();
    }

    @SubscribeEvent
    public static void key(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean rotate = ROTATE.isPressed(), display = DISPLAY.isPressed(), open = OPEN.isPressed(),
            lock = LOCK.isPressed();
        if (mc.currentScreen != null || mc.thePlayer == null) return;
        KeyBinding pickBlock = mc.gameSettings.keyBindPickBlock;
        if (pickBlock.getKeyCode() > 0 && Keyboard.getEventKeyState()
            && Keyboard.getEventKey() == pickBlock.getKeyCode()
            && pick(mc)) presses(pickBlock);
        if (open) openAnywhere(mc.thePlayer);
        if (lock) ProjectorClient.lockKey(mc.thePlayer.isSneaking());
        ProjectorPlan plan = ProjectorClient.plan;
        if (plan == null || !plan.hasMachine()
            || plan.state == ProjectorPlan.State.IDLE
            || plan.isPlaced() && plan.dimension != mc.thePlayer.dimension) return;
        if (rotate) {
            if (isShiftDown()) turn(facing -> ProjectorOrientation.mirror(facing, ProjectorClient.validFacings()));
            else turn(facing -> ProjectorOrientation.rotateY(facing, 1, ProjectorClient.validFacings()));
        }
        if (display) cycleDisplay(plan);
    }

    @SubscribeEvent
    public static void pickBlock(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!event.buttonstate || event.button < 0
            || event.button - 100 != mc.gameSettings.keyBindPickBlock.getKeyCode()) return;
        if (mc.currentScreen == null && mc.thePlayer != null && pick(mc)) event.setCanceled(true);
    }

    private static boolean pick(Minecraft mc) {
        ProjectorPlan plan = ProjectorClient.plan;
        if (plan == null || !plan.hasMachine() || plan.state == ProjectorPlan.State.IDLE || !ProjectorClient.visible)
            return false;
        EntityPlayer player = mc.thePlayer;
        MovingObjectPosition hit = ProjectorClient.trace(mc.theWorld, player);
        boolean onBlock = hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
        ProjectorCheck check = ProjectorClient.CHECK;
        int hitIndex = onBlock ? check.indexAt(hit.blockX, hit.blockY, hit.blockZ) : -1;
        Vec3 eye = ProjectorClient.eye(player);
        int ghost = check
            .firstCellAlong(eye, player.getLook(1.0f), onBlock ? eye.distanceTo(hit.hitVec) : ProjectorClient.REACH);
        if (ghost >= 0 && ghost != hitIndex) return openSpot(check.structure, check.site.local(ghost), null, true);
        int hitCell = hitIndex < 0 ? -1 : check.site.local(hitIndex);
        // A worn projector leaves real blocks to vanilla pick block, which builders use to take blocks.
        if (!onBlock || ProjectorClient.location != 0 && !player.isSneaking()) return false;
        ItemStack clicked = pickedStack(mc.theWorld, player, hit);
        if (hitCell >= 0) return openSpot(check.structure, hitCell, clicked, false);
        ProjectorStructure structure = ProjectorClient.structure;
        String channel = structure == null ? null : ProjectorChannels.channelOf(clicked, structure.channels);
        if (channel == null) return false;
        ProjectorSpotScreen.openChannel(structure, -1, channel, clicked);
        return true;
    }

    private static boolean openSpot(ProjectorStructure shown, int cell, ItemStack clicked, boolean ghost) {
        Choice choice = shown.choiceAt(cell);
        if (shown.slotAt(cell) != null) ProjectorSpotScreen.openHatch(shown, cell);
        else if (choice != null && choice.channel() != null)
            ProjectorSpotScreen.openChannel(shown, shown.choiceOf[cell], choice.channel(), clicked);
        else if (choice != null) ProjectorSpotScreen.openChoice(shown, cell);
        else if (!ghost) return false;
        else say(
            StatCollector.translateToLocalFormatted(
                ProjectorSpotScreen.LANG
                    + (shown.metas[cell] == ProjectorStructure.ANY_META ? "any_variant" : "only_block"),
                nameOf(shown, cell)));
        return true;
    }

    private static String nameOf(ProjectorStructure shown, int cell) {
        ItemStack item = shown.items[cell];
        return item != null ? ProjectorChannels.name(item) : shown.blocks[cell].getLocalizedName();
    }

    private static ItemStack pickedStack(World world, EntityPlayer player, MovingObjectPosition hit) {
        Block block = world.getBlock(hit.blockX, hit.blockY, hit.blockZ);
        try {
            return block.getPickBlock(hit, world, hit.blockX, hit.blockY, hit.blockZ, player);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String wornFollowHint() {
        int code = LOCK.getKeyCode();
        if (code == Keyboard.KEY_NONE)
            return StatCollector.translateToLocal(ProjectorClient.LANG + "follow_worn_unbound");
        String key = GameSettings.getKeyDisplayString(code);
        return StatCollector.translateToLocalFormatted(ProjectorClient.LANG + "follow_worn", key, key);
    }

    private static int presses(KeyBinding key) {
        int count = 0;
        while (key.isPressed()) count++;
        return count;
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private static void openAnywhere(EntityPlayer player) {
        ItemStack held = player.getCurrentEquippedItem();
        if (held != null && held.getItem() instanceof MultiblockProjector) {
            ScienceNotLeisure.network.sendToServer(new OpenProjectorPacket(0));
            return;
        }
        IInventory worn = BaublesCompat.getBaubles(player);
        if (worn == null) return;
        for (int slot = 0; slot < worn.getSizeInventory(); slot++) {
            ItemStack stack = worn.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof MultiblockProjector) {
                ScienceNotLeisure.network.sendToServer(new OpenProjectorPacket(-slot - 1));
                return;
            }
        }
    }

    public static void turn(UnaryOperator<ExtendedFacing> change) {
        ProjectorPlan plan = ProjectorClient.plan;
        if (plan.state == ProjectorPlan.State.ANCHORED) {
            hint(ProjectorClient.LANG + "anchored");
            return;
        }
        ExtendedFacing next = change.apply(plan.facing);
        if (next == plan.facing) {
            hint(ProjectorClient.LANG + "invalid_facing");
            return;
        }
        edit(copy -> ProjectorClient.reorient(copy, next, ProjectorClient.structure));
    }

    public static void edit(Consumer<ProjectorPlan> change) {
        ProjectorPlan next = ProjectorClient.plan.copy();
        change.accept(next);
        ProjectorClient.send(next);
    }

    private static void cycleDisplay(ProjectorPlan plan) {
        ProjectorPlan.Display[] values = ProjectorPlan.Display.values();
        ProjectorPlan.Display next = values[(plan.display.ordinal() + 1) % values.length];
        edit(copy -> copy.display = next);
        hint(ProjectorClient.LANG + "display." + next.name());
    }

    public static void hint(String key) {
        say(StatCollector.translateToLocal(key));
    }

    private static void say(String text) {
        AboveHotbarHUD.renderTextAboveHotbar(EnumChatFormatting.WHITE + text, 40, true, true);
    }
}
