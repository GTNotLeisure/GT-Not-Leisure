package com.science.gtnl.common.effect.effects;

import static com.science.gtnl.ScienceNotLeisure.LOG;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingFallEvent;

import com.gtnllib.mod.ModList;
import com.reavaritia.common.entity.EntityChronarchClock;
import com.science.gtnl.common.effect.EffectBase;
import com.science.gtnl.common.tickrate.TickAccelerationHooks;
import com.science.gtnl.config.EffectConfig;
import com.science.gtnl.config.ReAvaritiaConfig;
import com.xir.NHUtilities.common.api.interfaces.ITileEntityTickAcceleration;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

public class MissBroomBlessingEffect extends EffectBase {

    private static final long MAX_ACCELERATION_NANOS = 1_000_000L;
    private final Map<World, AccelerationFrame> accelerationFrames = new WeakHashMap<>();

    public MissBroomBlessingEffect(int id) {
        super(id, "miss_broom_blessing", false, 0xB978F2, 6);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }

    @Override
    public void performEffect(EntityLivingBase entity, int amplifier) {
        if (!(entity instanceof EntityPlayer player) || entity.worldObj.isRemote) return;
        World world = entity.worldObj;
        long tick = world.getTotalWorldTime();
        EffectConfig.MissBroomBlessing config = EffectConfig.miss_broom_blessing;
        if (config.enableCleanse && tick % (config.cleanseIntervalSeconds * 20L) == 0L) cleanse(player);
        if (!config.enableAcceleration || config.accelerationPercent <= 0) return;
        long phase = (tick + 1L) % 100L;
        int extraTicks = (int) (((phase + 1L) * config.accelerationPercent / 100L)
            - (phase * config.accelerationPercent / 100L));
        if (extraTicks == 0) return;

        AccelerationFrame frame = accelerationFrames.computeIfAbsent(world, ignored -> new AccelerationFrame());
        if (frame.tick != tick) {
            frame.tick = tick;
            frame.positions.clear();
            frame.elapsedNanos = 0L;
        }
        if (frame.elapsedNanos >= MAX_ACCELERATION_NANOS) return;

        long started = System.nanoTime();
        long deadline = started + MAX_ACCELERATION_NANOS - frame.elapsedNanos;
        try {
            List<TileEntity> candidates = nearbyTiles(world, player, config.accelerationRadius);
            if (candidates.isEmpty()) return;
            int start = frame.cursor % candidates.size();
            boolean nhUtilitiesLoaded = ModList.NHUtilities.isModLoaded();
            for (int offset = 0; offset < candidates.size(); offset++) {
                int index = (start + offset) % candidates.size();
                if (System.nanoTime() > deadline) return;
                frame.cursor = (index + 1) % candidates.size();
                TileEntity tile = candidates.get(index);
                if (tile.isInvalid() || !tile.canUpdate() || isBlacklisted(tile)) continue;
                if (!world.blockExists(tile.xCoord, tile.yCoord, tile.zCoord)
                    || world.getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord) != tile) continue;
                long position = ((long) tile.xCoord & 0x3FFFFFFL) << 38 | ((long) tile.zCoord & 0x3FFFFFFL) << 12
                    | (tile.yCoord & 0xFFFL);
                if (!frame.positions.add(position)) continue;
                try {
                    if (TickAccelerationHooks.accelerate(tile, extraTicks)) continue;
                    if (nhUtilitiesLoaded && tile instanceof ITileEntityTickAcceleration accelerated
                        && accelerated.tickAcceleration(extraTicks)) continue;
                    for (int extra = 0; extra < extraTicks; extra++) {
                        if (System.nanoTime() > deadline) return;
                        if (tile.isInvalid()) break;
                        tile.updateEntity();
                    }
                } catch (RuntimeException exception) {
                    if (frame.loggedFailures.add(tile.getClass())) {
                        LOG.warn(
                            "Unable to accelerate tile entity at ({}, {}, {})",
                            tile.xCoord,
                            tile.yCoord,
                            tile.zCoord,
                            exception);
                    }
                }
            }
        } finally {
            frame.elapsedNanos += System.nanoTime() - started;
        }
    }

    private static List<TileEntity> nearbyTiles(World world, EntityPlayer player, int radius) {
        int cx = MathHelper.floor_double(player.posX);
        int cy = MathHelper.floor_double(player.posY);
        int cz = MathHelper.floor_double(player.posZ);
        int minY = Math.max(0, cy - radius);
        int maxY = Math.min(world.getActualHeight() - 1, cy + radius);
        List<TileEntity> result = new ArrayList<>();
        if (minY > maxY) return result;
        for (int chunkX = (cx - radius) >> 4; chunkX <= (cx + radius) >> 4; chunkX++) {
            for (int chunkZ = (cz - radius) >> 4; chunkZ <= (cz + radius) >> 4; chunkZ++) {
                if (!world.getChunkProvider()
                    .chunkExists(chunkX, chunkZ)) continue;
                Chunk chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);
                for (Object entry : chunk.chunkTileEntityMap.values()) {
                    if (entry instanceof TileEntity tile && tile.getWorldObj() == world
                        && tile.xCoord >= cx - radius
                        && tile.xCoord <= cx + radius
                        && tile.yCoord >= minY
                        && tile.yCoord <= maxY
                        && tile.zCoord >= cz - radius
                        && tile.zCoord <= cz + radius) result.add(tile);
                }
            }
        }
        return result;
    }

    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event) {
        if (EffectConfig.miss_broom_blessing.preventFallDamage && event.entity instanceof EntityPlayer player
            && !player.worldObj.isRemote
            && player.getActivePotionEffect(this) != null) event.setCanceled(true);
    }

    private static void cleanse(EntityPlayer player) {
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            int potionId = effect.getPotionID();
            if (potionId < 0 || potionId >= Potion.potionTypes.length) continue;
            Potion potion = Potion.potionTypes[potionId];
            if (potion != null && potion.isBadEffect()) player.removePotionEffect(potionId);
        }
    }

    private static boolean isBlacklisted(TileEntity tile) {
        String[] clockBlacklist = ReAvaritiaConfig.chronarch_clock.chronarchsClockTileEntityBlacklist;
        String[] blessingBlacklist = EffectConfig.miss_broom_blessing.tileEntityBlacklist;
        if (clockBlacklist.length == 0 && blessingBlacklist.length == 0) return false;
        String registryName = EntityChronarchClock.getRegistryName(tile);
        if (registryName == null) return false;
        return isListed(registryName, clockBlacklist) || isListed(registryName, blessingBlacklist);
    }

    private static boolean isListed(String registryName, String[] blacklist) {
        for (String entry : blacklist) {
            if (entry != null && registryName.equals(entry.trim())) return true;
        }
        return false;
    }

    private static class AccelerationFrame {

        private long tick = Long.MIN_VALUE;
        private long elapsedNanos;
        private int cursor;
        private final LongSet positions = new LongOpenHashSet();
        private final Set<Class<?>> loggedFailures = new HashSet<>();
    }
}
