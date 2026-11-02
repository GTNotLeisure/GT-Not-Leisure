package com.science.gtnl.common.gui;

import org.jetbrains.annotations.Nullable;

import com.gtnllib.gui.theme.AE2Theme;
import com.science.gtnl.common.gui.equipment.EquipmentTheme;
import com.science.gtnl.config.ClientConfig;
import com.science.gtnl.utils.enums.EquipmentScreenTheme;
import com.science.gtnl.utils.enums.ScreenTheme;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ScreenThemes {

    private ScreenThemes() {}

    public static @Nullable String celestweave() {
        return of(ClientConfig.celestweaveScreenTheme);
    }

    public static @Nullable String railgun() {
        return of(ClientConfig.railgunScreenTheme);
    }

    public static @Nullable String portalGun() {
        return of(ClientConfig.portalGunScreenTheme);
    }

    public static @Nullable String wirelessCard() {
        return of(ClientConfig.wirelessCardScreenTheme);
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
