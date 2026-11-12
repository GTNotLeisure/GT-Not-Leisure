package com.science.gtnl.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.Config.Comment;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultBoolean;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultDouble;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultFloat;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultInt;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultIntList;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultStringList;
import com.gtnewhorizon.gtnhlib.config.Config.Name;
import com.gtnewhorizon.gtnhlib.config.Config.RangeDouble;
import com.gtnewhorizon.gtnhlib.config.Config.RangeFloat;
import com.gtnewhorizon.gtnhlib.config.Config.RangeInt;
import com.gtnewhorizon.gtnhlib.config.Config.RequiresMcRestart;
import com.gtnewhorizon.gtnhlib.config.Config.Sync;
import com.science.gtnl.ScienceNotLeisure;

import gregtech.api.enums.Mods;

@Config(
    modid = ScienceNotLeisure.MODID,
    category = "item",
    configSubDirectory = ConfigRegistry.DIRECTORY,
    filename = ConfigRegistry.ITEM_FILE)
@Config.LangKeyPattern(pattern = ConfigRegistry.LANG_PATTERN, fullyQualified = true)
@Sync
@Comment("Item section")
public class ItemConfig {

    @Comment("Enable special hat and robe set bonuses for DreamYao520 and SereiaWei")
    @DefaultBoolean(false)
    @Sync(false)
    @RequiresMcRestart
    public static boolean enableSpecialPlayerSetBonuses = false;

    @Name("broom")
    public static final Broom broom = new Broom();

    @Comment("Broom")
    @RequiresMcRestart
    public static class Broom {

        @Comment("Allow looking up or down while moving forward to control broom altitude")
        @DefaultBoolean(true)
        public boolean enableViewControl = true;

        @Comment("Magic Broom maximum horizontal speed in blocks per second")
        @DefaultDouble(12.0D)
        @RangeDouble(min = 0.1D, max = 100.0D)
        public double maxSpeed = 12.0D;

        @Comment("Magic Broom flight duration in seconds; landing resets the timer")
        @DefaultInt(30)
        @RangeInt(min = 1, max = 3600)
        public int flightDurationSeconds = 30;

        @Comment("Elaina's Broom speed multiplier relative to the Magic Broom")
        @DefaultDouble(1.5D)
        @RangeDouble(min = 0.1D, max = 10.0D)
        public double elainaSpeedMultiplier = 1.5D;

        @Comment("Give Elaina's Broom unlimited flight; otherwise use flightDurationSeconds")
        @DefaultBoolean(true)
        public boolean elainaUnlimitedFlight = true;

        @Comment("Land Skills enchantment ID. Must match client/server; do not change after enchanting saved items")
        @DefaultInt(200)
        @RangeInt(min = 0, max = 32767)
        @Sync(false)
        public int landSkillsEnchantmentId = 200;

        @Comment("Air Skills enchantment ID. Must match client/server; do not change after enchanting saved items")
        @DefaultInt(201)
        @RangeInt(min = 0, max = 32767)
        @Sync(false)
        public int airSkillsEnchantmentId = 201;

        @Comment("Hovering enchantment ID. Must match client/server; do not change after enchanting saved items")
        @DefaultInt(202)
        @RangeInt(min = 0, max = 32767)
        @Sync(false)
        public int hoveringEnchantmentId = 202;

        @Comment("Seabreeze enchantment ID. Must match client/server; do not change after enchanting saved items")
        @DefaultInt(203)
        @RangeInt(min = 0, max = 32767)
        @Sync(false)
        public int seabreezeEnchantmentId = 203;
    }

    @Name("elaina_brooch")
    public static final ElainaBrooch elaina_brooch = new ElainaBrooch();

    @RequiresMcRestart
    @Comment("Elaina's Brooch; server values are synchronized to clients")
    public static class ElainaBrooch {

        @Comment("Enable all effects granted by Elaina's Brooch")
        @DefaultBoolean(true)
        public boolean enabled = true;

        @Comment("Grant creative flight while the brooch is worn")
        @DefaultBoolean(true)
        public boolean enableCreativeFlight = true;

        @Comment("Override armor value and armor mitigation; 25 or more blocks all armor-blockable damage")
        @DefaultBoolean(false)
        public boolean overrideArmor = false;

        @Comment("Fixed armor value; armor-blockable damage reduction is capped at 100%")
        @DefaultInt(12)
        @RangeInt(min = 0, max = 100)
        public int armorValue = 12;

        @Comment("Additional maximum health in health points, not hearts; 0 disables the bonus")
        @DefaultDouble(20.0D)
        @RangeDouble(min = 0.0D, max = 1000000.0D)
        public double maxHealthBonus = 20.0D;

        @Comment("Seconds between health, food and cleanse updates")
        @DefaultInt(1)
        @RangeInt(min = 1, max = 3600)
        public int restoreIntervalSeconds = 1;

        @Comment("Restore health to maximum at each restore interval")
        @DefaultBoolean(false)
        public boolean restoreHealth = false;

        @Comment("Restore hunger and saturation at each restore interval")
        @DefaultBoolean(true)
        public boolean restoreFood = true;

        @Comment("Hunger restored at each interval; vanilla maximum is 20")
        @DefaultInt(20)
        @RangeInt(min = 0, max = 20)
        public int foodLevel = 20;

        @Comment("Saturation restored at each interval, limited to foodLevel")
        @DefaultDouble(20.0D)
        @RangeDouble(min = 0.0D, max = 20.0D)
        public double saturation = 20.0D;

        @Comment("Remove negative potion effects at each restore interval")
        @DefaultBoolean(true)
        public boolean enableCleanse = true;

        @Comment("Prevent lethal damage and death while the brooch is worn")
        @DefaultBoolean(false)
        public boolean preventDeath = false;

        @Comment("Seconds of additional immunity after a hit; 0 disables this effect")
        @DefaultDouble(0.5D)
        @RangeDouble(min = 0.0D, max = 60.0D)
        public double invulnerabilitySeconds = 0.5D;

        @Comment("Enable periodic potion effects; disabling stops refreshes, existing effects expire naturally")
        @DefaultBoolean(true)
        public boolean enablePotionEffects = true;

        @Comment("Potion duration in seconds; use at least refresh interval + 10 to avoid night vision flicker")
        @DefaultInt(30)
        @RangeInt(min = 1, max = 3600)
        public int potionDurationSeconds = 30;

        @Comment("Seconds between potion refreshes; a longer interval than duration causes gaps")
        @DefaultInt(20)
        @RangeInt(min = 1, max = 3600)
        public int potionRefreshSeconds = 20;

        @Comment("Night Vision level; 0 disables the effect")
        @DefaultInt(1)
        @RangeInt(min = 0, max = 256)
        public int nightVisionLevel = 1;

        @Comment("Fire Resistance level; 0 disables the effect")
        @DefaultInt(1)
        @RangeInt(min = 0, max = 256)
        public int fireResistanceLevel = 1;

        @Comment("Regeneration level; 0 disables the effect")
        @DefaultInt(1)
        @RangeInt(min = 0, max = 256)
        public int regenerationLevel = 1;

        @Comment("Water Breathing level; 0 disables the effect")
        @DefaultInt(1)
        @RangeInt(min = 0, max = 256)
        public int waterBreathingLevel = 1;

        @Comment("Haste level; 0 disables the effect")
        @DefaultInt(1)
        @RangeInt(min = 0, max = 256)
        public int hasteLevel = 1;

        @Comment("Resistance level; 0 disables the effect")
        @DefaultInt(1)
        @RangeInt(min = 0, max = 256)
        public int resistanceLevel = 1;

        @Comment("Strength level; 0 disables the effect")
        @DefaultInt(2)
        @RangeInt(min = 0, max = 256)
        public int strengthLevel = 2;
    }

    public static final EnergyModule energyModule = new EnergyModule();

    @Comment("Energy module shared by the railgun and Celestweave armor")
    public static class EnergyModule {

        @Comment("EU capacity of a railgun or armor piece with a T1 energy module installed")
        @DefaultDouble(25000000)
        public double capacityT1 = 25000000;

        @Comment("EU capacity of a railgun or armor piece with a T2 energy module installed")
        @DefaultDouble(125000000)
        public double capacityT2 = 125000000;

        @Comment("EU capacity of a railgun or armor piece with a T3 energy module installed")
        @DefaultDouble(500000000)
        public double capacityT3 = 500000000;
    }

    public static final Railgun railgun = new Railgun();

    @Comment("Electromagnetic Railgun")
    public static class Railgun {

        @Comment("EU capacity of the railgun without an energy module")
        @DefaultDouble(250000)
        public double baseCapacity = 250000;

        @Comment("EU equivalent of one high voltage lightning, used by the sustained beam")
        @DefaultDouble(1600)
        public double hvLightningEu = 1600;

        @Comment("EU equivalent of one extreme high voltage lightning, used by charged shots and the EHV beam")
        @DefaultDouble(25600)
        public double ehvLightningEu = 25600;

        @Comment("Base EU consumed by the sustained beam every damage settlement (2 ticks)")
        @DefaultDouble(100)
        public double beamEuPerSettle = 100;

        @Comment("Base EU consumed by the extreme high voltage beam every damage settlement (2 ticks)")
        @DefaultDouble(1000)
        public double ehvBeamEuPerSettle = 1000;

        @Comment("Base EU consumed when firing an EHV1 charged shot")
        @DefaultDouble(2000)
        public double shotEuEhv1 = 2000;

        @Comment("Base EU consumed when firing an EHV2 charged shot")
        @DefaultDouble(10000)
        public double shotEuEhv2 = 10000;

        @Comment("Base EU consumed when firing an EHV3 charged shot")
        @DefaultDouble(50000)
        public double shotEuEhv3 = 50000;

        @Comment("EU consumed per charging tick before reaching EHV2")
        @DefaultDouble(250)
        public double chargeEuEhv1 = 250;

        @Comment("EU consumed per charging tick between EHV2 and EHV3")
        @DefaultDouble(1000)
        public double chargeEuEhv2 = 1000;

        @Comment("EU consumed per charging tick after reaching EHV3")
        @DefaultDouble(2500)
        public double chargeEuEhv3 = 2500;

        @Comment("Extra charging speed granted by each installed acceleration module, 0.25 means +25%")
        @DefaultDouble(0.25)
        @RangeDouble(min = 0, max = 1)
        public double accelerationBonus = 0.25;

        @Comment("Damage of the sustained beam per settlement")
        @DefaultDouble(20)
        public double beamDamage = 20;

        @Comment("Armor bypass ratio of the sustained beam, from 0 to 1")
        @DefaultDouble(0.4)
        public double beamBypass = 0.4;

        @Comment("Damage of the extreme high voltage beam per settlement")
        @DefaultDouble(200)
        public double ehvBeamDamage = 200;

        @Comment("Base damage of an EHV1 charged shot")
        @DefaultDouble(100)
        public double baseDamageEhv1 = 100;

        @Comment("Base damage of an EHV2 charged shot")
        @DefaultDouble(300)
        public double baseDamageEhv2 = 300;

        @Comment("Base damage of an EHV3 charged shot")
        @DefaultDouble(600)
        public double baseDamageEhv3 = 600;

        @Comment("Armor bypass ratio of charged shots, from 0 to 1")
        @DefaultDouble(0.8)
        public double chargedBypass = 0.8;

        @Comment("Allow the railgun to damage players when the PvP setting of the railgun is enabled")
        @DefaultBoolean(true)
        public boolean enablePlayerDamage = true;

        @Comment("Apply electromagnetic paralysis to players hit by the railgun")
        @DefaultBoolean(true)
        public boolean enablePlayerParalysis = true;

        @Comment("Allow charged shots to destroy terrain when the terrain setting of the railgun is enabled")
        @DefaultBoolean(false)
        public boolean enableTerrain = false;

        @Comment("Drop items from blocks destroyed by the railgun")
        @DefaultBoolean(false)
        public boolean enableTerrainDrops = false;

        @Comment("Maximum number of blocks the railgun destroys per tick across all impacts")
        @DefaultInt(200)
        @RangeInt(min = 1, max = 10000)
        public int terrainBlocksPerTick = 200;

        @Comment("Mod IDs whose blocks are never destroyed by the railgun, case-insensitive")
        @DefaultStringList({ Mods.ModIDs.GREG_TECH, Mods.ModIDs.BART_WORKS, Mods.ModIDs.TECTECH,
            Mods.ModIDs.G_T_N_H_LANTHANIDES, Mods.ModIDs.G_T_PLUS_PLUS, Mods.ModIDs.APPLIED_ENERGISTICS2,
            Mods.ModIDs.A_E2_FLUID_CRAFT, ScienceNotLeisure.MODID })
        public String[] terrainProtectedMods = { Mods.ModIDs.GREG_TECH, Mods.ModIDs.BART_WORKS, Mods.ModIDs.TECTECH,
            Mods.ModIDs.G_T_N_H_LANTHANIDES, Mods.ModIDs.G_T_PLUS_PLUS, Mods.ModIDs.APPLIED_ENERGISTICS2,
            Mods.ModIDs.A_E2_FLUID_CRAFT, ScienceNotLeisure.MODID };

        @Comment("Refill the held railgun from the owner's wireless EU network once per second")
        @DefaultBoolean(true)
        public boolean enableWirelessRefill = true;
    }

    public static final PortalGun portal_gun = new PortalGun();

    @Comment("Portal Gun")
    public static class PortalGun {

        @Comment("Shimmer the portal gun holds, in mB; shimmer is its only fuel")
        @DefaultInt(8000)
        @RangeInt(min = 100)
        public int shimmerCapacity = 8000;

        @Comment("Blocks of distance paid by 1 mB of shimmer when the destination is in the same dimension")
        @DefaultInt(4)
        @RangeInt(min = 1)
        public int blocksPerShimmer = 4;

        @Comment("Most shimmer a shot can cost, in mB. Shots to another dimension always cost this much")
        @DefaultInt(100)
        @RangeInt(min = 0)
        public int maxShimmerPerShot = 100;

        @Comment("Gravity on a portal gun shot, in blocks per tick squared; 0 flies straight")
        @DefaultDouble(0.05)
        @RangeDouble(min = 0, max = 0.2)
        public double orbGravity = 0.05;

        @Comment("How long a portal gun shot flies before it opens a portal in the air, in ticks")
        @DefaultInt(5)
        @RangeInt(min = 1, max = 100)
        public int airOpenTicks = 5;

        @Comment("How long a pair of portals stays open, in ticks")
        @DefaultInt(100)
        @RangeInt(min = 20)
        public int portalLifetime = 100;

        @Comment("Ticks after going through a pair of portals before the same pair carries that entity again; touching either portal of the pair meanwhile restarts the wait. 0 turns it off")
        @DefaultInt(20)
        @RangeInt(min = 0, max = 1200)
        public int pairCooldownTicks = 20;

        @Comment("Dimension IDs where portals can neither open nor lead to")
        @DefaultIntList({})
        public int[] dimensionBlacklist = {};
    }

    public static final PlayerDoll player_doll = new PlayerDoll();

    @Comment("Player Doll")
    public static class PlayerDoll {

        @Comment("Enable Custom Player Skin for Player Doll")
        @DefaultBoolean(true)
        @RequiresMcRestart
        public boolean enableCustomSkin = true;

        @Comment("Make Play Doll to MAX Tier Glass")
        @DefaultBoolean(true)
        @RequiresMcRestart
        public boolean enableRegisterMAXTierGlass = true;
    }

    public static final VeinMinerPickaxe vein_miner_pickaxe = new VeinMinerPickaxe();

    @Comment("Vein Miner Pickaxe")
    public static class VeinMinerPickaxe {

        @Comment("Set maximum number of chained blocks for Vein Mining Pickaxe")
        @DefaultInt(327670)
        public int maxAmount = 327670;

        @Comment("Set maximum block distance for Vein Mining Pickaxe")
        @DefaultInt(32)
        public int maxRange = 32;
    }

    public static final Stick stick = new Stick();

    @Comment("Stick")
    public static class Stick {

        @Comment("Enable stick fake item and block")
        @DefaultBoolean(true)
        @RequiresMcRestart
        public boolean enableStickItem = true;
    }

    public static final SteamRocket steam_rocket = new SteamRocket();

    @Comment("Steam Rocket")
    public static class SteamRocket {

        @Comment("The Page ID for the Steam Rocket Schematic")
        @DefaultInt(114514)
        public int idSchematicRocketSteam = 114514;

        @Comment("The GUI ID for the NASA Workbench when using Steam Rocket Schematic")
        @DefaultInt(114514)
        public int nasaWorkbenchSteamRocket = 114514;
    }

    public static final Celestweave celestweave = new Celestweave();

    @Comment("Celestweave Armor")
    public static class Celestweave {

        @Comment("EU capacity of each armor piece without an energy module")
        @DefaultDouble(2500000)
        public double baseCapacity = 2500000;

        @Comment("Refill worn armor from the owner's wireless EU network every second")
        @DefaultBoolean(true)
        public boolean enableWirelessRefill = true;

        @Comment("Upper limit of the idle arc count a wearer can choose")
        @DefaultInt(8)
        @RangeInt(min = 0, max = 8)
        public int maxIdleArcs = 8;

        @Comment("Draw from the owner's wireless EU network when the armor charge cannot pay for absorbed damage")
        @DefaultBoolean(true)
        public boolean enableWirelessFallback = true;

        @Comment("EU paid per point of absorbed damage")
        @DefaultDouble(20000)
        @RangeDouble(min = 0, max = 1.0E12)
        public double shieldEuPerDamage = 20000;

        @Comment("Upper limit of EU paid for a single hit")
        @DefaultDouble(1.0E9)
        @RangeDouble(min = 0, max = 1.0E15)
        public double maxEuPerHit = 1.0E9;

        @Comment("Damage absorbed per hit by the shield")
        @DefaultFloat(1.0E6F)
        @RangeFloat(min = 0, max = 1.0E9F)
        public float maxAbsorbPerHit = 1.0E6F;

        @Comment("EU paid when the shield prevents a death")
        @DefaultDouble(1.0E8)
        @RangeDouble(min = 0, max = 1.0E15)
        public double deathSaveEu = 1.0E8;

        @Comment("Share of the incoming damage dealt by each lightning strike of the reflect module")
        @DefaultFloat(0.5F)
        @RangeFloat(min = 0, max = 10)
        public float reflectRatio = 0.5F;

        @Comment("Radius in blocks within which the reflect module strikes hostile mobs")
        @DefaultDouble(8)
        @RangeDouble(min = 0, max = 32)
        public double reflectRadius = 8;

        @Comment("Most targets struck by lightning per reflected hit, including the attacker")
        @DefaultInt(4)
        @RangeInt(min = 1, max = 16)
        public int maxReflectTargets = 4;

        @Comment("EU paid per reflected hit or projectile")
        @DefaultDouble(50000)
        @RangeDouble(min = 0, max = 1.0E12)
        public double reflectEu = 50000;

        @Comment("EU paid per tick while the wearer is flying and moving with the flight module")
        @DefaultDouble(100)
        @RangeDouble(min = 0, max = 1.0E12)
        public double flightEuPerTick = 100;

        @Comment("EU paid per tick by the walk assist module while moving on the ground")
        @DefaultDouble(20)
        @RangeDouble(min = 0, max = 1.0E12)
        public double walkEuPerTick = 20;

        @Comment("Upper limit of the walk assist speed and jump multipliers")
        @DefaultInt(4)
        @RangeInt(min = 1, max = 16)
        public int maxWalkLevel = 4;

        @Comment("Upper limit of the flight speed multiplier")
        @DefaultInt(8)
        @RangeInt(min = 1, max = 16)
        public int maxFlightLevel = 8;

        @Comment("EU paid per dash")
        @DefaultDouble(500000)
        @RangeDouble(min = 0, max = 1.0E12)
        public double dashEu = 500000;

        @Comment("Ticks between two dashes")
        @DefaultInt(40)
        @RangeInt(min = 0, max = 1200)
        public int dashCooldown = 40;

        @Comment("Dash velocity in blocks per tick")
        @DefaultFloat(2.2F)
        @RangeFloat(min = 0, max = 10)
        public float dashSpeed = 2.2F;

        @Comment("Extra reach of the reach module at level 1")
        @DefaultFloat(5)
        @RangeFloat(min = 0, max = 100)
        public float reachLevel1 = 5;

        @Comment("Extra reach of the reach module at level 2")
        @DefaultFloat(10)
        @RangeFloat(min = 0, max = 100)
        public float reachLevel2 = 10;

        @Comment("Extra reach of the reach module at level 3")
        @DefaultFloat(20)
        @RangeFloat(min = 0, max = 100)
        public float reachLevel3 = 20;

        @Comment("EU paid per point of hunger refilled by the saturation module")
        @DefaultDouble(5000)
        @RangeDouble(min = 0, max = 1.0E12)
        public double saturationEu = 5000;

        @Comment("EU paid per removed negative effect by the purification module")
        @DefaultDouble(20000)
        @RangeDouble(min = 0, max = 1.0E12)
        public double purificationEu = 20000;

        @Comment("EU paid per tick by the night vision module")
        @DefaultDouble(5)
        @RangeDouble(min = 0, max = 1.0E12)
        public double nightVisionEuPerTick = 5;

        @Comment("EU paid per point of mana supplied by the terrasteel module")
        @DefaultDouble(100)
        @RangeDouble(min = 0, max = 1.0E12)
        public double manaEu = 100;

        @Comment("Vis cost discount in percentage points while the thaumic module is powered")
        @DefaultInt(40)
        @RangeInt(min = 0, max = 100)
        public int visDiscount = 40;

        @Comment("Maximum Vis supplied per primal aspect each second, shared across charged wands")
        @DefaultInt(1)
        @RangeInt(min = 0, max = 1000)
        public int visPerSecond = 1;

        @Comment("EU paid per point of Vis supplied by the thaumic module")
        @DefaultDouble(10000)
        @RangeDouble(min = 0, max = 1.0E12)
        public double visEu = 10000;

        @Comment("Capacity multiplier for carried wands while the thaumic module capacity boost is powered")
        @DefaultInt(2)
        @RangeInt(min = 1, max = 16)
        public int visCapacityMultiplier = 2;

        @Comment("EU paid per tick for Warp Ward and removal of active warp ailments")
        @DefaultDouble(100)
        @RangeDouble(min = 0, max = 1.0E12)
        public double warpSuppressionEuPerTick = 100;
    }
}
