package com.science.gtnl.common.gui;

import org.jetbrains.annotations.Nullable;

import com.gtnllib.gui.theme.AE2Theme;
import com.science.gtnl.common.gui.equipment.EquipmentTheme;
import com.science.gtnl.config.ScreenThemeConfig;
import com.science.gtnl.utils.enums.EquipmentScreenTheme;
import com.science.gtnl.utils.enums.ScreenTheme;

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
        return of(ScreenThemeConfig.wirelessCard);
    }

    public static @Nullable String miniTerminal() {
        return of(ScreenThemeConfig.miniTerminal);
    }

    public static @Nullable String of(ScreenTheme theme) {
        return switch (theme) {
            case AE2_LIGHT -> AE2Theme.LIGHT_ID;
            case AE2_DARK -> AE2Theme.DARK_ID;
            case DEFAULT -> null;
        };
    }

    public static @Nullable String of(EquipmentScreenTheme theme) {
        return switch (theme) {
            case EQUIPMENT -> EquipmentTheme.selected();
            case AE2_LIGHT -> AE2Theme.LIGHT_ID;
            case AE2_DARK -> AE2Theme.DARK_ID;
            case DEFAULT -> null;
        };
    }
}
