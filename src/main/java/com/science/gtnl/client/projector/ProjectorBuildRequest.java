package com.science.gtnl.client.projector;

import net.minecraft.client.Minecraft;

import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.client.structureanimation.StructureAnimations;
import com.science.gtnl.common.packet.ProjectorActionPacket;
import com.science.gtnl.common.packet.ProjectorBuildPacket;
import com.science.gtnl.common.projector.ProjectorPlan;
import com.science.gtnl.config.MachineConfig;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ProjectorBuildRequest {

    public static final long CONFIRM_MILLIS = 3000;
    private static int armed = -1;
    private static long armedAt;

    private ProjectorBuildRequest() {}

    public static boolean canBuild() {
        ProjectorCheck check = ProjectorClient.CHECK;
        return MachineConfig.projectorBuildEnabled && check.isActive()
            && (check.missing > 0 || check.wrong > 0)
            && !StructureAnimations.ownActive();
    }

    public static boolean canDismantle() {
        ProjectorPlan plan = ProjectorClient.plan;
        return MachineConfig.projectorBuildEnabled && plan != null
            && plan.isPlaced()
            && !StructureAnimations.ownActive();
    }

    public static boolean armed(int action) {
        return armed == action && Minecraft.getSystemTime() - armedAt < CONFIRM_MILLIS;
    }

    // Undoing and dismantling cannot be taken back, so each needs a second click within a few seconds.
    public static void act(int action) {
        int location = ProjectorClient.location;
        if (location == Integer.MIN_VALUE) return;
        if (action != ProjectorActionPacket.CANCEL && !armed(action)) {
            armed = action;
            armedAt = Minecraft.getSystemTime();
            return;
        }
        armed = -1;
        ScienceNotLeisure.network.sendToServer(new ProjectorActionPacket(location, action));
    }

    public static void send() {
        send(ProjectorClient.location);
    }

    public static void send(int location) {
        if (!canBuild() || location == Integer.MIN_VALUE) return;
        ScienceNotLeisure.network.sendToServer(new ProjectorBuildPacket(location));
    }
}
