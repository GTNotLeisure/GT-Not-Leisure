package com.science.gtnl.config;

import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.SimpleGuiConfig;
import com.gtnewhorizon.gtnhlib.config.SimpleGuiFactory;
import com.gtnllib.gui.theme.AE2Theme;
import com.science.gtnl.ScienceNotLeisure;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ConfigGuiFactory implements SimpleGuiFactory {

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return Screen.class;
    }

    private static Class<?>[] configClasses() {
        ScreenThemeConfig.listThemes(
            AE2Theme.variants()
                .stream()
                .map(variant -> variant.id)
                .toList());
        return ConfigRegistry.gui();
    }

    public static class Screen extends SimpleGuiConfig {

        public Screen(GuiScreen parent) throws ConfigException {
            super(parent, ScienceNotLeisure.MODID, ScienceNotLeisure.MODNAME, true, configClasses());
        }
    }
}
