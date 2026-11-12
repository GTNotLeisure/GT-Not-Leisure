package com.science.gtnl.common.item.items;

import static com.science.gtnl.ScienceNotLeisure.RESOURCE_ROOT_ID;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.FoodStats;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import com.gtnllib.text.TextEffects;
import com.science.gtnl.common.item.BaubleItem;
import com.science.gtnl.common.item.GTNLCreativeTabs;
import com.science.gtnl.config.ItemConfig;
import com.science.gtnl.mixins.early.minecraft.AccessorFoodStats;
import com.science.gtnl.utils.enums.GTNLItemList;

import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.expanded.BaubleExpandedSlots;
import baubles.api.expanded.IBaubleExpanded;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemElainaBrooch extends BaubleItem implements IBaubleExpanded {

    private static final String TITLE_TYPE = "title";
    private static final String[] BAUBLE_TYPES = { TITLE_TYPE };
    private static final String FLIGHT_TAG = "GTNLElainaBroochFlight";
    private static final String INVULNERABLE_TICKS_TAG = "GTNLElainaBroochInvulnerableTicks";
    private static final String NEXT_BUFF_REFRESH_TAG = "GTNLElainaBroochNextBuffRefresh";
    private static final String TRAVELER_ACTIVE_TAG = "GTNLMajoTravelerActive";
    private static final String TRAVELER_FLIGHT_TAG = "GTNLMajoTravelerFlight";
    private static final UUID MAX_HEALTH_MODIFIER_ID = UUID.fromString("61f8a7e4-2c5d-4bb7-b1f4-2cfcdbef1eb6");

    public ItemElainaBrooch() {
        BaubleExpandedSlots.tryRegisterType(TITLE_TYPE);
        setUnlocalizedName("gtnl.elaina_brooch");
        setTextureName(RESOURCE_ROOT_ID + ":elaina_brooch");
        setMaxStackSize(1);
        setCreativeTab(GTNLCreativeTabs.GTNotLeisureItem);

        GTNLItemList.ElainaBrooch.set(new ItemStack(this));
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public BaubleType getBaubleType(ItemStack stack) {
        return null;
    }

    @Override
    public String[] getBaubleTypes(ItemStack stack) {
        return BAUBLE_TYPES;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        for (int line = 0; line < 4; line++) {
            tooltip.add(
                TextEffects.apply(
                    StatCollector.translateToLocal("item.gtnl.elaina_brooch.lore." + line),
                    TextEffects.GENESIS_COMPONENT_RARITY_SHADER));
        }
        tooltip.add("");
        ItemConfig.ElainaBrooch config = ItemConfig.elaina_brooch;
        if (!config.enabled) {
            tooltip.add(
                TextEffects.apply(
                    StatCollector.translateToLocal("item.gtnl.elaina_brooch.disabled"),
                    TextEffects.EVERCOLD_CYAN));
            return;
        }
        String worn = StatCollector.translateToLocal("item.gtnl.elaina_brooch.worn");
        tooltip.add(TextEffects.apply(worn, TextEffects.EVERCOLD_CYAN));
        if (config.enableCreativeFlight) {
            tooltip.add(
                TextEffects.apply(
                    StatCollector.translateToLocal("item.gtnl.elaina_brooch.tooltip.0"),
                    TextEffects.EVERCOLD_CYAN));
        }
        List<String> attributes = new ArrayList<>();
        if (config.overrideArmor)
            attributes.add(StatCollector.translateToLocalFormatted("item.gtnl.elaina_brooch.armor", config.armorValue));
        if (config.maxHealthBonus > 0.0D) attributes.add(
            StatCollector
                .translateToLocalFormatted("item.gtnl.elaina_brooch.max_health", formatValue(config.maxHealthBonus)));
        if (config.restoreHealth) attributes.add(
            StatCollector
                .translateToLocalFormatted("item.gtnl.elaina_brooch.restore_health", config.restoreIntervalSeconds));
        if (config.restoreFood) attributes.add(
            StatCollector.translateToLocalFormatted(
                "item.gtnl.elaina_brooch.food",
                config.foodLevel,
                formatValue(Math.min(config.saturation, config.foodLevel))));
        if (config.enableCleanse) attributes.add(StatCollector.translateToLocal("item.gtnl.elaina_brooch.cleanse"));
        addEffectTooltip(tooltip, 1, attributes);
        List<String> protection = new ArrayList<>();
        if (config.preventDeath)
            protection.add(StatCollector.translateToLocal("item.gtnl.elaina_brooch.prevent_death"));
        if (config.invulnerabilitySeconds > 0.0D) protection.add(
            StatCollector.translateToLocalFormatted(
                "item.gtnl.elaina_brooch.invulnerability",
                formatValue(config.invulnerabilitySeconds)));
        addEffectTooltip(tooltip, 2, protection);
    }

    @SideOnly(Side.CLIENT)
    private static void addEffectTooltip(List<String> tooltip, int line, List<String> effects) {
        if (effects.isEmpty()) return;
        tooltip.add(
            TextEffects.apply(
                StatCollector
                    .translateToLocalFormatted("item.gtnl.elaina_brooch.tooltip." + line, String.join(", ", effects)),
                TextEffects.EVERCOLD_CYAN));
    }

    @SideOnly(Side.CLIENT)
    private static String formatValue(double value) {
        return value == (long) value ? Long.toString((long) value) : Double.toString(value);
    }

    @Override
    public void onEquippedOrLoadedIntoWorld(ItemStack stack, EntityLivingBase wearer) {
        if (!wearer.worldObj.isRemote) wearer.getEntityData()
            .removeTag(NEXT_BUFF_REFRESH_TAG);
    }

    public static boolean isEquipped(EntityPlayer player) {
        IInventory baubles = BaublesApi.getBaubles(player);
        if (baubles == null) return false;
        for (int slot = 0; slot < baubles.getSizeInventory(); slot++) {
            if (!TITLE_TYPE.equals(BaubleExpandedSlots.getSlotType(slot))) continue;
            ItemStack stack = baubles.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof ItemElainaBrooch) return true;
        }
        return false;
    }

    public static boolean hasGrantedFlight(EntityPlayer player) {
        return player.getEntityData()
            .getBoolean(FLIGHT_TAG);
    }

    public static int getArmorValue() {
        return ItemConfig.elaina_brooch.armorValue;
    }

    public static boolean isActive(EntityPlayer player) {
        return ItemConfig.elaina_brooch.enabled && isEquipped(player);
    }

    public static boolean overridesArmor(EntityPlayer player) {
        return ItemConfig.elaina_brooch.overrideArmor && isActive(player);
    }

    public static boolean grantsCreativeFlight(EntityPlayer player) {
        return ItemConfig.elaina_brooch.enableCreativeFlight && isActive(player);
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer player) || player.worldObj.isRemote) return;

        ItemConfig.ElainaBrooch config = ItemConfig.elaina_brooch;
        NBTTagCompound data = player.getEntityData();
        int invulnerableTicks = Math.min(data.getInteger(INVULNERABLE_TICKS_TAG), getInvulnerabilityTicks());
        if (invulnerableTicks > 0) data.setInteger(INVULNERABLE_TICKS_TAG, invulnerableTicks - 1);
        else data.removeTag(INVULNERABLE_TICKS_TAG);

        boolean active = isActive(player);
        updateMaxHealthBonus(player, active);
        updateFlight(player, data, active && config.enableCreativeFlight);
        if (!active) {
            data.removeTag(INVULNERABLE_TICKS_TAG);
            data.removeTag(NEXT_BUFF_REFRESH_TAG);
            return;
        }

        long worldTime = player.worldObj.getTotalWorldTime();
        long nextBuffRefresh = data.getLong(NEXT_BUFF_REFRESH_TAG);
        int refreshTicks = config.potionRefreshSeconds * 20;
        if (!config.enablePotionEffects) {
            data.removeTag(NEXT_BUFF_REFRESH_TAG);
        } else if (!data.hasKey(NEXT_BUFF_REFRESH_TAG) || worldTime >= nextBuffRefresh
            || nextBuffRefresh - worldTime > refreshTicks) {
                refreshBuffs(player);
                data.setLong(NEXT_BUFF_REFRESH_TAG, worldTime + refreshTicks);
            }

        if (player.ticksExisted % (config.restoreIntervalSeconds * 20) != 0) return;
        if (config.restoreHealth) player.setHealth(player.getMaxHealth());
        if (config.restoreFood) {
            FoodStats food = player.getFoodStats();
            AccessorFoodStats foodAccessor = (AccessorFoodStats) food;
            foodAccessor.setFoodlevel(config.foodLevel);
            foodAccessor.setFoodSaturationLevel((float) Math.min(config.saturation, config.foodLevel));
            foodAccessor.setFoodExhaustionLevel(0.0F);
        }
        if (!config.enableCleanse) return;
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            int potionId = effect.getPotionID();
            if (potionId < 0 || potionId >= Potion.potionTypes.length) continue;
            Potion potion = Potion.potionTypes[potionId];
            if (potion != null && potion.isBadEffect()) player.removePotionEffect(potionId);
        }
    }

    private static void refreshBuffs(EntityPlayer player) {
        ItemConfig.ElainaBrooch config = ItemConfig.elaina_brooch;
        refreshBuff(player, Potion.nightVision, config.nightVisionLevel);
        refreshBuff(player, Potion.fireResistance, config.fireResistanceLevel);
        refreshBuff(player, Potion.regeneration, config.regenerationLevel);
        refreshBuff(player, Potion.waterBreathing, config.waterBreathingLevel);
        refreshBuff(player, Potion.digSpeed, config.hasteLevel);
        refreshBuff(player, Potion.resistance, config.resistanceLevel);
        refreshBuff(player, Potion.damageBoost, config.strengthLevel);
    }

    private static void refreshBuff(EntityPlayer player, Potion potion, int level) {
        if (level == 0) return;
        int amplifier = level - 1;
        int duration = ItemConfig.elaina_brooch.potionDurationSeconds * 20;
        PotionEffect current = player.getActivePotionEffect(potion);
        if (current != null && (current.getAmplifier() > amplifier
            || current.getAmplifier() == amplifier && current.getDuration() >= duration)) return;
        player.addPotionEffect(new PotionEffect(potion.id, duration, amplifier, true));
    }

    private static void updateMaxHealthBonus(EntityPlayer player, boolean enabled) {
        IAttributeInstance attribute = player.getEntityAttribute(SharedMonsterAttributes.maxHealth);
        if (attribute == null) return;
        AttributeModifier current = attribute.getModifier(MAX_HEALTH_MODIFIER_ID);
        double bonus = enabled ? ItemConfig.elaina_brooch.maxHealthBonus : 0.0D;
        if (bonus <= 0.0D) {
            if (current == null) return;
            attribute.removeModifier(current);
        } else {
            if (current != null && current.getAmount() == bonus && current.getOperation() == 0 && !current.isSaved())
                return;
            if (current != null) attribute.removeModifier(current);
            attribute.applyModifier(
                new AttributeModifier(MAX_HEALTH_MODIFIER_ID, "Elaina brooch maximum health", bonus, 0)
                    .setSaved(false));
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private static void updateFlight(EntityPlayer player, NBTTagCompound data, boolean enabled) {
        if (enabled) {
            if (!player.capabilities.allowFlying) {
                player.capabilities.allowFlying = true;
                data.setBoolean(FLIGHT_TAG, true);
                player.sendPlayerAbilities();
            } else if (data.getBoolean(TRAVELER_FLIGHT_TAG)) {
                data.setBoolean(FLIGHT_TAG, true);
            }
        } else if (data.getBoolean(FLIGHT_TAG)) {
            data.removeTag(FLIGHT_TAG);
            if (!player.capabilities.isCreativeMode && !data.getBoolean(TRAVELER_ACTIVE_TAG)) {
                player.capabilities.allowFlying = false;
                player.capabilities.isFlying = false;
                player.sendPlayerAbilities();
            }
        }
    }

    private static int getInvulnerabilityTicks() {
        return (int) Math.ceil(ItemConfig.elaina_brooch.invulnerabilitySeconds * 20.0D);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer player) || player.worldObj.isRemote) return;
        if (getInvulnerabilityTicks() > 0 && isActive(player)
            && player.getEntityData()
                .getInteger(INVULNERABLE_TICKS_TAG) > 0)
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer player) || player.worldObj.isRemote) return;
        if (!isActive(player) || event.ammount <= 0.0F) return;
        player.getEntityData()
            .setInteger(INVULNERABLE_TICKS_TAG, getInvulnerabilityTicks());
        if (ItemConfig.elaina_brooch.overrideArmor && getArmorValue() >= 25 && !event.source.isUnblockable()) {
            event.setCanceled(true);
            return;
        }
        if (ItemConfig.elaina_brooch.preventDeath && event.ammount > player.getMaxHealth()) {
            event.setCanceled(true);
            player.setHealth(1.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer player) || player.worldObj.isRemote) return;
        if (!ItemConfig.elaina_brooch.preventDeath || !isActive(player)) return;
        event.setCanceled(true);
        player.setHealth(1.0F);
        player.getEntityData()
            .setInteger(INVULNERABLE_TICKS_TAG, getInvulnerabilityTicks());
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.wasDeath) return;
        NBTTagCompound originalData = event.original.getEntityData();
        NBTTagCompound newData = event.entityPlayer.getEntityData();
        if (originalData.getBoolean(FLIGHT_TAG)) newData.setBoolean(FLIGHT_TAG, true);
        int invulnerableTicks = originalData.getInteger(INVULNERABLE_TICKS_TAG);
        if (invulnerableTicks > 0) newData.setInteger(INVULNERABLE_TICKS_TAG, invulnerableTicks);
    }
}
