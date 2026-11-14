package com.science.gtnl.common.circuit.computation;

import java.util.List;

import com.gtnewhorizon.structurelib.util.Vec3Impl;

import gregtech.api.util.GTUtility;
import tectech.thing.metaTileEntity.hatch.MTEHatchDataInput;

public class DataHatchComputationSource implements CircuitComputationSource {

    private final List<MTEHatchDataInput> hatches;
    private long readyAt = Long.MIN_VALUE;
    private long lastRead = Long.MIN_VALUE;
    private long available;

    public DataHatchComputationSource(List<MTEHatchDataInput> hatches) {
        this.hatches = hatches;
    }

    @Override
    public long available(long tick, Vec3Impl position) {
        if (readyAt == Long.MIN_VALUE) readyAt = tick + 40;
        if (tick < readyAt) return 0;
        if (lastRead != Long.MIN_VALUE && tick - lastRead < 20) return available;
        lastRead = tick;
        available = 0;
        for (MTEHatchDataInput hatch : hatches) {
            if (!hatch.isValid() || hatch.q == null) continue;
            long contribution = hatch.q.contentIfNotInTrace(position);
            if (contribution > 0) available = GTUtility.addSafe(available, contribution);
        }
        return available;
    }

    @Override
    public void reset() {
        readyAt = Long.MIN_VALUE;
        invalidate();
    }

    @Override
    public void invalidate() {
        lastRead = Long.MIN_VALUE;
        available = 0;
    }
}
