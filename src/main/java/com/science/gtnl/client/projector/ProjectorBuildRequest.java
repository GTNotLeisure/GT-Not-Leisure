package com.science.gtnl.client.projector;

import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.client.structureanimation.StructureAnimations;
import com.science.gtnl.common.packet.ProjectorBuildPacket;
import com.science.gtnl.config.MachineConfig;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ProjectorBuildRequest {

    private ProjectorBuildRequest() {}

    public static boolean canBuild() {
        ProjectorCheck check = ProjectorClient.CHECK;
        return MachineConfig.projectorBuildEnabled && check.isActive()
            && check.missing > 0
            && !StructureAnimations.ownActive();
    }

    public static void send() {
        send(ProjectorClient.location);
    }

    public static void send(int location) {
        if (!canBuild() || location == Integer.MIN_VALUE) return;
        ScienceNotLeisure.network.sendToServer(new ProjectorBuildPacket(location));
    }
}
