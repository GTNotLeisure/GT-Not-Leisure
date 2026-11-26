package com.science.gtnl.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.Config.Comment;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultEnum;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultString;
import com.gtnewhorizon.gtnhlib.config.Config.LangKey;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnllib.gui.theme.ScreenTheme;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.utils.enums.EquipmentScreenTheme;

@Config(
    modid = ScienceNotLeisure.MODID,
    category = "screen_theme",
    configSubDirectory = ConfigRegistry.DIRECTORY,
    filename = ConfigRegistry.CLIENT_FILE)
@Config.LangKeyPattern(pattern = ConfigRegistry.LANG_PATTERN, fullyQualified = true)
@Comment("Client themes. Empty or unavailable overrides use the base theme.")
public class ScreenThemeConfig {

    @Config.Ignore
    private static final String INHERIT = "gtnl.gui.config.screen_theme.inherit";
    @Config.Ignore
    private static final List<String> CUSTOM_THEMES = List.of(
        "celestweaveCustomTheme",
        "railgunCustomTheme",
        "portalGunCustomTheme",
        "wirelessCardCustomTheme",
        "miniTerminalCustomTheme");

    @Comment("Celestweave screen and equipment wheel theme.")
    @LangKey("gtnl.gui.config.screen_theme.celestweave")
    @DefaultEnum("EQUIPMENT")
    public static EquipmentScreenTheme celestweave = EquipmentScreenTheme.EQUIPMENT;

    @Comment("Railgun screen theme.")
    @LangKey("gtnl.gui.config.screen_theme.railgun")
    @DefaultEnum("EQUIPMENT")
    public static EquipmentScreenTheme railgun = EquipmentScreenTheme.EQUIPMENT;

    @Comment("Portal gun screen theme.")
    @LangKey("gtnl.gui.config.screen_theme.portal_gun")
    @DefaultEnum("EQUIPMENT")
    public static EquipmentScreenTheme portalGun = EquipmentScreenTheme.EQUIPMENT;

    @Comment("Wireless card screen theme.")
    @LangKey("gtnl.gui.config.screen_theme.wireless_card")
    @DefaultEnum("AE2_LIGHT")
    public static ScreenTheme wirelessCard = ScreenTheme.AE2_LIGHT;

    @Comment("Mini terminal theme; DEFAULT keeps the original look.")
    @LangKey("gtnl.gui.config.screen_theme.mini_terminal")
    @DefaultEnum("DEFAULT")
    public static ScreenTheme miniTerminal = ScreenTheme.DEFAULT;

    @Comment("Optional AE2 theme override.")
    @LangKey("gtnl.gui.config.screen_theme.celestweave_custom")
    @DefaultString("")
    public static String celestweaveCustomTheme = "";

    @Comment("Optional AE2 theme override.")
    @LangKey("gtnl.gui.config.screen_theme.railgun_custom")
    @DefaultString("")
    public static String railgunCustomTheme = "";

    @Comment("Optional AE2 theme override.")
    @LangKey("gtnl.gui.config.screen_theme.portal_gun_custom")
    @DefaultString("")
    public static String portalGunCustomTheme = "";

    @Comment("Optional AE2 theme override.")
    @LangKey("gtnl.gui.config.screen_theme.wireless_card_custom")
    @DefaultString("")
    public static String wirelessCardCustomTheme = "";

    @Comment("Optional AE2 theme override.")
    @LangKey("gtnl.gui.config.screen_theme.mini_terminal_custom")
    @DefaultString("")
    public static String miniTerminalCustomTheme = "";

    public static void listThemes(Collection<String> ids) {
        Configuration config = ConfigurationManager.getConfig(ScreenThemeConfig.class);
        if (config == null) return;
        Set<String> available = new LinkedHashSet<>();
        available.add(INHERIT);
        available.addAll(ids);
        for (String name : CUSTOM_THEMES) {
            Property property = config.getCategory("screen_theme")
                .get(name);
            if (property == null) continue;
            Set<String> choices = new LinkedHashSet<>(available);
            String selected = property.getString();
            if (!selected.isEmpty()) choices.add(selected);
            property.setValidValues(choices.toArray(new String[0]));
        }
    }
}
