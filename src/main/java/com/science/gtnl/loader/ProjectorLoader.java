package com.science.gtnl.loader;

import static com.science.gtnl.ScienceNotLeisure.RESOURCE_ROOT_ID;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

import com.science.gtnl.client.projector.ProjectorInput;
import com.science.gtnl.client.structureanimation.StructureAnimations;
import com.science.gtnl.common.command.ProjectorSimulationCommand;
import com.science.gtnl.common.projector.ProjectorServer;
import com.science.gtnl.common.projector.ProjectorSimulationWorld;
import com.science.gtnl.common.render.item.JsonItemRenderer;
import com.science.gtnl.common.render.model.JsonItemModel;

import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;

public class ProjectorLoader implements IContentLoader {

    @Override
    public String name() {
        return "projector";
    }

    @Override
    public void init() {
        GregTechAPI.addDummyWorld(ProjectorSimulationWorld.class);
    }

    @Override
    public void serverStarting(FMLServerStartingEvent event) {
        if (!(boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment")) return;
        event.registerServerCommand(new ProjectorSimulationCommand());
        ProjectorSimulationCommand.scheduleProbe();
    }

    @Override
    public void serverStopping() {
        ProjectorServer.clear();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPreInit() {
        ProjectorInput.register();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientInit() {
        StructureAnimations.registerStyles();
        JsonItemModel model = new JsonItemModel(RESOURCE_ROOT_ID + ":item/multiblock_projector");
        MinecraftForge.EVENT_BUS.register(model);
        MinecraftForgeClient.registerItemRenderer(
            ItemLoader.multiblockProjector,
            new JsonItemRenderer(model::get, JsonItemRenderer.ITEM_ENTITY_SCALE));
    }
}
