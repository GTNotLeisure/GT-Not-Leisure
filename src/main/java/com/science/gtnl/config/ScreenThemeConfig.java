package com.science.gtnl.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.Config.Comment;
import com.gtnewhorizon.gtnhlib.config.Config.DefaultEnum;
import com.gtnewhorizon.gtnhlib.config.Config.LangKey;
import com.gtnllib.gui.theme.ScreenTheme;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.utils.enums.EquipmentScreenTheme;

@Config(
    modid = ScienceNotLeisure.MODID,
    category = "screen_theme",
    configSubDirectory = ConfigRegistry.DIRECTORY,
    filename = ConfigRegistry.CLIENT_FILE)
@Config.LangKeyPattern(pattern = ConfigRegistry.LANG_PATTERN, fullyQualified = true)
@Comment("Themes of GTNL screens; only changes what this client sees")
public class ScreenThemeConfig {

    @Comment("Theme of the Celestweave armor screens, the equipment wheel and the equipment sound screen. EQUIPMENT: the equipment theme; DEFAULT: the ModularUI 2 default theme; AE2_LIGHT: the light AE2 1.21 theme; AE2_DARK: the dark AE2 1.21 theme; AE2_DARK_PURPLE: the dark AE2 1.21 theme with purple highlights")
    @LangKey("gtnl.gui.config.screen_theme.celestweave")
    @DefaultEnum("EQUIPMENT")
    public static EquipmentScreenTheme celestweave = EquipmentScreenTheme.EQUIPMENT;

    @Comment("Theme of the electromagnetic railgun screens; the equipment wheel follows it when no Celestweave armor is in it. EQUIPMENT: the equipment theme; DEFAULT: the ModularUI 2 default theme; AE2_LIGHT: the light AE2 1.21 theme; AE2_DARK: the dark AE2 1.21 theme; AE2_DARK_PURPLE: the dark AE2 1.21 theme with purple highlights")
    @LangKey("gtnl.gui.config.screen_theme.railgun")
    @DefaultEnum("EQUIPMENT")
    public static EquipmentScreenTheme railgun = EquipmentScreenTheme.EQUIPMENT;

    @Comment("Theme of the portal gun screens. EQUIPMENT: the equipment theme; DEFAULT: the ModularUI 2 default theme; AE2_LIGHT: the light AE2 1.21 theme; AE2_DARK: the dark AE2 1.21 theme; AE2_DARK_PURPLE: the dark AE2 1.21 theme with purple highlights")
    @LangKey("gtnl.gui.config.screen_theme.portal_gun")
    @DefaultEnum("EQUIPMENT")
    public static EquipmentScreenTheme portalGun = EquipmentScreenTheme.EQUIPMENT;

    @Comment("Theme of the wireless frequency card screen. DEFAULT: the ModularUI 2 default theme; AE2_LIGHT: the light AE2 1.21 theme; AE2_DARK: the dark AE2 1.21 theme; AE2_DARK_PURPLE: the dark AE2 1.21 theme with purple highlights")
    @LangKey("gtnl.gui.config.screen_theme.wireless_card")
    @DefaultEnum("AE2_LIGHT")
    public static ScreenTheme wirelessCard = ScreenTheme.AE2_LIGHT;

    @Comment("Theme of the ME mini portable terminal. DEFAULT: its original look with the AE2 textures; AE2_LIGHT: the light AE2 1.21 theme; AE2_DARK: the dark AE2 1.21 theme; AE2_DARK_PURPLE: the dark AE2 1.21 theme with purple highlights")
    @LangKey("gtnl.gui.config.screen_theme.mini_terminal")
    @DefaultEnum("DEFAULT")
    public static ScreenTheme miniTerminal = ScreenTheme.DEFAULT;
}
