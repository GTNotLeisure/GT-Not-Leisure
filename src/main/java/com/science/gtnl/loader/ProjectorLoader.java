package com.science.gtnl.loader;

import net.minecraft.launchwrapper.Launch;

import com.science.gtnl.client.projector.ProjectorInput;
import com.science.gtnl.client.structureanimation.StructureAnimations;
import com.science.gtnl.common.command.ProjectorSimulationCommand;
import com.science.gtnl.common.projector.ProjectorBuilder;
import com.science.gtnl.common.projector.ProjectorSimulationWorld;

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
        ProjectorBuilder.clear();
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
    }
}
