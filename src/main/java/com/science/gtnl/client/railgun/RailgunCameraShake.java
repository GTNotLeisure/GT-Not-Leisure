package com.science.gtnl.client.railgun;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import com.gtnewhorizon.gtnhlib.eventbus.Phase;
import com.gtnllib.client.fx.world.FxCameraShake;
import com.gtnllib.client.fx.world.FxCameraShake.Axis;
import com.science.gtnl.config.ClientConfig;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@EventBusSubscriber(side = Side.CLIENT, phase = Phase.PRE)
public class RailgunCameraShake {

    private static final FxCameraShake.Source SHAKE = new FxCameraShake.Source();
    private static final int RECOIL_TICKS = 4;
    private static final float PITCH_FLOOR = -90F;
    private static final float RECOIL_KICK_BASE = 0.34F;
    private static final float RECOIL_KICK_STEP = 0.56F;
    private static final float CHARGE_TREMOR_MAX = 0.7F;
    private static final float CHARGE_TIER_KICK = 0.7F;
    private static final float BEAM_TREMOR = 0.16F;

    private static float pendingPitch;
    private static int recoilTicks;
    private static int chargedTier;

    public static void recoil(float pitch, int tier) {
        if (!Float.isFinite(pitch)) return;
        pendingPitch += Math.max(0F, pitch);
        recoilTicks = RECOIL_TICKS;
        if (ClientConfig.enableRailgunShake) SHAKE.kick(RECOIL_KICK_BASE + RECOIL_KICK_STEP * Math.max(0, tier - 1));
    }

    public static void clear() {
        SHAKE.clear();
        pendingPitch = 0F;
        recoilTicks = 0;
        chargedTier = 0;
    }

    public static float tremor(Axis axis, float partialTicks) {
        return SHAKE.tremor(axis, partialTicks);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            clear();
            return;
        }
        applyRecoil(player);
        SHAKE.sustain(sustain(player));
    }

    private static void applyRecoil(EntityPlayer player) {
        if (recoilTicks <= 0) return;
        float change = pendingPitch / recoilTicks;
        player.rotationPitch = Math.max(PITCH_FLOOR, player.rotationPitch - change);
        pendingPitch -= change;
        recoilTicks--;
    }

    private static float sustain(EntityPlayer player) {
        if (!ClientConfig.enableRailgunShake) return 0F;
        float target = RailgunBeamRenderer.localFiring() && RailgunBeamRenderer.active(player.getEntityId())
            ? BEAM_TREMOR
            : 0F;
        if (!RailgunCharge.charging(player)) {
            chargedTier = 0;
            return target;
        }
        float progress = RailgunCharge.progress(player);
        target = Math.max(target, CHARGE_TREMOR_MAX * progress * (0.35F + 0.65F * progress));
        int tier = RailgunCharge.tier(player)
            .ordinal();
        if (tier > chargedTier) {
            SHAKE.kick(CHARGE_TIER_KICK * tier);
            chargedTier = tier;
        }
        return target;
    }
}
