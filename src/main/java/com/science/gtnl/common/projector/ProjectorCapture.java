package com.science.gtnl.common.projector;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.gtnewhorizon.structurelib.StructureEvent.StructureElementVisitedEvent;
import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.science.gtnl.ScienceNotLeisure;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

// Finds the blocks a built machine counts as its own by letting it run its own structure check and keeping each spot
// the check visited where the element accepts what stands there. Its own check knows its real size and tiers, whatever
// the projection is set to.
@EventBusSubscriber
public class ProjectorCapture {

    private final World world;
    private final Long2ObjectLinkedOpenHashMap<List<IStructureElement<Object>>> visited = new Long2ObjectLinkedOpenHashMap<>();

    private ProjectorCapture(World world) {
        this.world = world;
    }

    public static LongOpenHashSet blocksOf(World world, int x, int y, int z) {
        LongOpenHashSet blocks = new LongOpenHashSet();
        if (!world.blockExists(x, y, z) || StructureLibAPI.isInstrumentEnabled()) return blocks;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof IGregTechTileEntity base)
            || !(base.getMetaTileEntity() instanceof MTEMultiBlockBase machine)) return blocks;
        ProjectorCapture capture = new ProjectorCapture(world);
        StructureLibAPI.enableInstrument(capture);
        try {
            machine.checkStructure(true, base);
        } catch (RuntimeException e) {
            ScienceNotLeisure.LOG.debug("Projector capture of {} failed to finish", machine, e);
        } finally {
            StructureLibAPI.disableInstrument();
        }
        blocks.add(CoordinatePacker.pack(x, y, z));
        for (Long2ObjectLinkedOpenHashMap.Entry<List<IStructureElement<Object>>> entry : capture.visited
            .long2ObjectEntrySet()) {
            long position = entry.getLongKey();
            int px = CoordinatePacker.unpackX(position), py = CoordinatePacker.unpackY(position),
                pz = CoordinatePacker.unpackZ(position);
            if (py < 0 || py > 255 || !world.blockExists(px, py, pz)) continue;
            Block block = world.getBlock(px, py, pz);
            if (block.isAir(world, px, py, pz) || block.isReplaceable(world, px, py, pz)) continue;
            // The check walks one step past a structure of variable length; that spot does not pass.
            for (IStructureElement<Object> element : entry.getValue()) {
                if (passes(element, machine, world, px, py, pz)) {
                    blocks.add(position);
                    break;
                }
            }
        }
        // Checking elements again registered hatches a second time; a clean check sets the machine right.
        try {
            machine.checkStructure(true, base);
        } catch (RuntimeException e) {
            ScienceNotLeisure.LOG.debug("Projector capture of {} failed to reset", machine, e);
        }
        return blocks;
    }

    private static boolean passes(IStructureElement<Object> element, Object machine, World world, int x, int y, int z) {
        try {
            return element.check(machine, world, x, y, z);
        } catch (RuntimeException e) {
            ScienceNotLeisure.LOG.debug("Projector capture could not check {} {} {}", x, y, z, e);
            return false;
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void visited(StructureElementVisitedEvent event) {
        if (!(event.getInstrumentIdentifier() instanceof ProjectorCapture capture) || event.getWorld() != capture.world)
            return;
        capture.visited
            .computeIfAbsent(CoordinatePacker.pack(event.getX(), event.getY(), event.getZ()), k -> new ArrayList<>())
            .add((IStructureElement<Object>) event.getElement());
    }
}
