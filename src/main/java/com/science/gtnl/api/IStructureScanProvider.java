package com.science.gtnl.api;

import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

/**
 * Supplies tier information without constructing a machine in a scan world.
 *
 * <p>
 * Implementations must expose a compiled {@link StructureDefinition} whose elements describe every material used by the
 * machine, including optional pieces and tier variants. Channel ranges must be declared on the structure definition.
 * The scanner indexes those elements before using this provider, so no world placement is needed to discover materials.
 * </p>
 */
public interface IStructureScanProvider extends IConstructable {

    /**
     * Returns the maximum trigger stack size that changes the constructed structure.
     *
     * <p>
     * This is the trigger tier, which may differ from the actual structure height when a channel uses an offset.
     * Implementations must not access a world or modify machine state.
     * </p>
     *
     * @return maximum trigger tier, or a nonpositive value to use the original world-based estimator
     */
    int getStructureScanTier();
}
