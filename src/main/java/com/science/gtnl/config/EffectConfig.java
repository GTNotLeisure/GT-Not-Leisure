package com.science.gtnl.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.Config.Comment;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultBoolean;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultInt;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultStringList;
import com.gtnewhorizon.gtnhlib.config.Config.Name;
import com.gtnewhorizon.gtnhlib.config.Config.RangeInt;
import com.gtnewhorizon.gtnhlib.config.Config.RequiresMcRestart;
import com.gtnewhorizon.gtnhlib.config.Config.Sync;
import com.science.gtnl.ScienceNotLeisure;

@Config(
    modid = ScienceNotLeisure.MODID,
    category = "effect",
    configSubDirectory = ConfigRegistry.DIRECTORY,
    filename = ConfigRegistry.GENERAL_FILE)
@Config.LangKeyPattern(pattern = ConfigRegistry.LANG_PATTERN, fullyQualified = true)
@Sync
@Comment("Effect section")
public class EffectConfig {

    @Name("miss_broom_blessing")
    public static final MissBroomBlessing miss_broom_blessing = new MissBroomBlessing();

    @RequiresMcRestart
    @Comment("Miss Broom's Blessing; server values are synchronized to clients")
    public static class MissBroomBlessing {

        @Comment("Enable nearby tile entity acceleration")
        @DefaultBoolean(true)
        public boolean enableAcceleration = true;

        @Comment("Acceleration radius in blocks along each axis")
        @DefaultInt(3)
        @RangeInt(min = 0, max = 16)
        public int accelerationRadius = 3;

        @Comment("Extra tile entity ticks as a percentage; 50 means 50% faster, 0 disables acceleration")
        @DefaultInt(25)
        @RangeInt(min = 0, max = 1000)
        public int accelerationPercent = 25;

        @Comment("Additional tile entity registry names to exclude; the Chronarch Clock blacklist also applies")
        @DefaultStringList({})
        public String[] tileEntityBlacklist = {};

        @Comment("Enable removal of negative potion effects")
        @DefaultBoolean(true)
        public boolean enableCleanse = true;

        @Comment("Seconds between removal of negative potion effects")
        @DefaultInt(5)
        @RangeInt(min = 1, max = 3600)
        public int cleanseIntervalSeconds = 5;

        @Comment("Prevent fall damage while the blessing is active")
        @DefaultBoolean(true)
        public boolean preventFallDamage = true;
    }

    @Comment("When the player dies and respawns, give the player Ghost Shape effect")
    @DefaultBoolean(true)
    public static boolean enableGhostlyShape = true;
}
