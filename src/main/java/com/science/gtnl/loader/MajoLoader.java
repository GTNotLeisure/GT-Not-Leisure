package com.science.gtnl.loader;

import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

import com.science.gtnl.client.MajoBroomInputHandler;
import com.science.gtnl.common.entity.EntityElainaBroom;
import com.science.gtnl.common.entity.EntityMajoBroom;
import com.science.gtnl.common.entity.MajoBroomUpdateHandler;
import com.science.gtnl.common.item.items.armor.MajoArmorHandler;
import com.science.gtnl.common.render.entity.MajoBroomRender;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import thaumcraft.client.renderers.item.ItemWandRenderer;

public class MajoLoader implements IContentLoader {

    @Override
    public String name() {
        return "majo";
    }

    @Override
    public void preInit() {
        EnchantmentLoader.registry();
        MinecraftForge.EVENT_BUS.register(new MajoArmorHandler());
        MinecraftForge.EVENT_BUS.register(new MajoBroomUpdateHandler());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPreInit() {
        MajoBroomInputHandler.register();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientInit() {
        RenderingRegistry.registerEntityRenderingHandler(EntityMajoBroom.class, new MajoBroomRender());
        RenderingRegistry.registerEntityRenderingHandler(EntityElainaBroom.class, new MajoBroomRender());
        MinecraftForgeClient.registerItemRenderer(ItemLoader.majoWand, new ItemWandRenderer());
    }
}
