package com.science.gtnl.common.gui;

import org.jetbrains.annotations.Nullable;

import com.gtnllib.gui.theme.AE2Theme;
import com.gtnllib.gui.theme.ScreenTheme;
import com.gtnllib.gui.theme.ae2.AE2Variant;
import com.science.gtnl.common.gui.equipment.EquipmentTheme;
import com.science.gtnl.config.ScreenThemeConfig;
import com.science.gtnl.utils.enums.EquipmentScreenTheme;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ScreenThemes {

    private ScreenThemes() {}

    public static @Nullable String celestweave() {
        return of(ScreenThemeConfig.celestweave, ScreenThemeConfig.celestweaveCustomTheme);
    }

    public static @Nullable String railgun() {
        return of(ScreenThemeConfig.railgun, ScreenThemeConfig.railgunCustomTheme);
    }

    public static @Nullable String portalGun() {
        return of(ScreenThemeConfig.portalGun, ScreenThemeConfig.portalGunCustomTheme);
    }

    public static @Nullable String wirelessCard() {
        return selected(ScreenThemeConfig.wirelessCardCustomTheme, ScreenThemeConfig.wirelessCard.themeId);
    }

    public static @Nullable String miniTerminal() {
        return selected(ScreenThemeConfig.miniTerminalCustomTheme, ScreenThemeConfig.miniTerminal.themeId);
    }

    public static @Nullable String of(EquipmentScreenTheme theme) {
        return of(theme, "");
    }

    public static @Nullable String of(EquipmentScreenTheme theme, String customTheme) {
        return selected(customTheme, switch (theme) {
            case EQUIPMENT -> EquipmentTheme.selected();
            case DEFAULT -> ScreenTheme.DEFAULT.themeId;
            case AE2_LIGHT -> ScreenTheme.AE2_LIGHT.themeId;
            case AE2_DARK -> ScreenTheme.AE2_DARK.themeId;
            case AE2_DARK_PURPLE -> ScreenTheme.AE2_DARK_PURPLE.themeId;
        });
    }

    private static @Nullable String selected(String customTheme, @Nullable String fallback) {
        String id = customTheme.trim();
        AE2Variant variant = AE2Theme.variant(id);
        return variant != null && variant.id.equals(id) ? id : fallback;
    }
}
