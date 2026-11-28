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

    public static final int TIMEOUT_TICKS = 300, REVERSE_LEAD = 20;

    public final Start start;
    public final long startTick;
    public final boolean reversed;
    public long placingTick = -1, doneTick = -1, lastPacketTick, forwarding = -1;
    public int processed, built;
    public boolean aborted;

    // A dismantle plays its style backwards: after a short lead that lets the meshes get ready, its clock runs from the
    // end of the build animation back to the start.
    protected StructureAnimationTask(Start start) {
        this.start = start;
        reversed = start.reversed();
        startTick = StructureAnimations.clientTicks() - (reversed ? 0 : start.elapsed());
        lastPacketTick = StructureAnimations.clientTicks();
    }

    public long now() {
        if (!reversed) return StructureAnimations.clientTicks();
        if (forwarding >= 0) return startTick + forwarding;
        return startTick + Math.max(0, length() - Math.max(0, reverseElapsed()));
    }

    public int length() {
        return start.placingTick();
    }

    public long reverseElapsed() {
        return StructureAnimations.clientTicks() - startTick - REVERSE_LEAD;
    }

    public double age(float partial) {
        if (!reversed || forwarding >= 0) return now() - startTick + (reversed ? 0 : partial);
        return Math.max(0, Math.min(length(), length() - (reverseElapsed() + partial)));
    }

    // Sounds, particles and shakes cannot play backwards, so a dismantle leaves them out.
    public boolean muted() {
        return reversed;
    }

    public boolean reverseDone() {
        return doneTick >= 0 && reverseElapsed() >= length();
    }

    public void state(int state, int processed) {
        lastPacketTick = StructureAnimations.clientTicks();
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
        int length = length();
        if (reversed) return length <= 0 ? 1
            : Math.max(0, Math.min(1, (float) (reverseElapsed() + REVERSE_LEAD) / (length + REVERSE_LEAD)));
        return length <= 0 || placingTick >= 0 ? 1 : Math.min(1, (float) (now() - startTick) / length);
    }

    // The slower of the animation and the server, so the bar neither runs ahead of what is shown nor of the build.
    public float buildProgress() {
        int cells = start.cells();
        float server = cells <= 0 ? 1 : Math.min(1, (float) built / cells);
        return Math.min(animationProgress(), server);
    }

    public void drop(int[] boxCells) {}

    // Once the server reported the end no more packets come, while a backwards animation may still have long to play.
    public boolean timedOut() {
        return doneTick < 0 && StructureAnimations.clientTicks() - lastPacketTick > TIMEOUT_TICKS;
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
