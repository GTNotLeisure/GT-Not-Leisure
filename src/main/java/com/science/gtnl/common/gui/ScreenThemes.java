package com.science.gtnl.common.gui;

import org.jetbrains.annotations.Nullable;

import com.gtnllib.gui.theme.ScreenTheme;
import com.science.gtnl.common.gui.equipment.EquipmentTheme;
import com.science.gtnl.config.ScreenThemeConfig;
import com.science.gtnl.utils.enums.EquipmentScreenTheme;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ScreenThemes {

    private ScreenThemes() {}

    public static @Nullable String celestweave() {
        return of(ScreenThemeConfig.celestweave);
    }

    public static @Nullable String railgun() {
        return of(ScreenThemeConfig.railgun);
    }

    public static @Nullable String portalGun() {
        return of(ScreenThemeConfig.portalGun);
    }

    public static @Nullable String wirelessCard() {
        return ScreenThemeConfig.wirelessCard.themeId;
    }

    public static @Nullable String miniTerminal() {
        return ScreenThemeConfig.miniTerminal.themeId;
    }

    public static @Nullable String of(EquipmentScreenTheme theme) {
        return switch (theme) {
            case EQUIPMENT -> EquipmentTheme.selected();
            case DEFAULT -> ScreenTheme.DEFAULT.themeId;
            case AE2_LIGHT -> ScreenTheme.AE2_LIGHT.themeId;
            case AE2_DARK -> ScreenTheme.AE2_DARK.themeId;
            case AE2_DARK_PURPLE -> ScreenTheme.AE2_DARK_PURPLE.themeId;
        };
    }
}
