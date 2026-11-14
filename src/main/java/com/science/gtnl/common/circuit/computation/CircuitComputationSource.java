package com.science.gtnl.common.circuit.computation;

import com.gtnewhorizon.structurelib.util.Vec3Impl;

public interface CircuitComputationSource {

    long available(long tick, Vec3Impl position);

    void reset();

    void invalidate();
}
