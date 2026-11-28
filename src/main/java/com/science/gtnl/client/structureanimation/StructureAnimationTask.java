package com.science.gtnl.client.structureanimation;

import net.minecraft.client.Minecraft;

import com.science.gtnl.common.packet.StructureAnimationStartPacket.Start;
import com.science.gtnl.common.packet.StructureAnimationStatePacket;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * One running build animation: its clock, which stops while the game is paused, and the server's reports on the
 * build. Subclasses give the timeline and the drawing.
 */
@SideOnly(Side.CLIENT)
public abstract class StructureAnimationTask {

    public static final int TIMEOUT_TICKS = 300;

    public final Start start;
    public final long startTick;
    public long placingTick = -1, doneTick = -1, lastPacketTick;
    public int processed, built;
    public boolean aborted;

    protected StructureAnimationTask(Start start) {
        this.start = start;
        startTick = StructureAnimations.clientTicks() - start.elapsed();
        lastPacketTick = StructureAnimations.clientTicks();
    }

    public long now() {
        return StructureAnimations.clientTicks();
    }

    public double age(float partial) {
        return now() - startTick + partial;
    }

    public void state(int state, int processed) {
        lastPacketTick = now();
        switch (state) {
            case StructureAnimationStatePacket.PLACING -> {
                if (placingTick < 0) placingTick = now();
            }
            case StructureAnimationStatePacket.PROGRESS -> this.processed = Math.max(this.processed, processed);
            case StructureAnimationStatePacket.BUILT -> built = Math.max(built, processed);
            case StructureAnimationStatePacket.DONE -> {
                this.processed = Integer.MAX_VALUE;
                built = Integer.MAX_VALUE;
                doneTick = now();
                if (placingTick < 0) placingTick = now();
            }
            default -> aborted = true;
        }
    }

    public float animationProgress() {
        int length = start.placingTick();
        return length <= 0 || placingTick >= 0 ? 1 : Math.min(1, (float) (now() - startTick) / length);
    }

    // The slower of the animation and the server, so the bar neither runs ahead of what is shown nor of the build.
    public float buildProgress() {
        int cells = start.cells();
        float server = cells <= 0 ? 1 : Math.min(1, (float) built / cells);
        return Math.min(animationProgress(), server);
    }

    public void drop(int[] boxCells) {}

    public boolean timedOut() {
        return now() - lastPacketTick > TIMEOUT_TICKS;
    }

    /** Whether the task must be dropped at once. */
    public boolean expired(Minecraft minecraft) {
        return aborted || timedOut()
            || minecraft.theWorld == null
            || minecraft.theWorld.provider.dimensionId != start.dimension();
    }

    /** Whether the task has played out and can be dropped. */
    public abstract boolean finished();

    /** Called every client tick while the game runs and the task has not expired. */
    protected abstract void tick(Minecraft minecraft);

    /** Draws the task; the caller has pushed the matrix and all GL attributes. */
    protected abstract void render(Minecraft minecraft, double age, float partial, double cameraX, double cameraY,
        double cameraZ);

    /** How strongly the task wants the screen glow this frame; 0 for none. */
    public float glow(double age) {
        return 0;
    }

    /** Draws the shapes that feed the screen glow, relative to the camera. */
    protected void drawGlow(double cameraX, double cameraY, double cameraZ) {}

    /** Frees display lists, meshes, sounds and anything else the task holds. */
    public abstract void release();
}
