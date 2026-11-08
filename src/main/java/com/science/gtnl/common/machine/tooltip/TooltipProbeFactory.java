package com.science.gtnl.common.machine.tooltip;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;

public class TooltipProbeFactory {

    private static final Map<Class<?>, Object> LOCKS = new ConcurrentHashMap<>();

    private TooltipProbeFactory() {}

    public static <T extends MTEEnhancedMultiBlockBase<T>, R> R withProbe(T source, Function<T, R> read) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(read);
        synchronized (LOCKS.computeIfAbsent(source.getClass(), ignored -> new Object())) {
            return read.apply(create(source));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends MTEEnhancedMultiBlockBase<T>> T create(T source) {
        IGregTechTileEntity sourceBase = source.getBaseMetaTileEntity();
        if (sourceBase == null || sourceBase.getMetaTileID() <= 0) {
            throw new IllegalArgumentException("A tooltip probe requires a registered machine identity");
        }
        BaseMetaTileEntity base = new BaseMetaTileEntity();
        base.setMetaTileID((short) sourceBase.getMetaTileID());
        IMetaTileEntity copy = source.newMetaEntity(base);
        if (copy == null || copy == source || copy.getClass() != source.getClass()) {
            throw new IllegalStateException("newMetaEntity must create a separate instance of the same machine type");
        }
        IGregTechTileEntity copyBase = copy.getBaseMetaTileEntity();
        if (copyBase == sourceBase || copyBase != null && copyBase != base) {
            throw new IllegalStateException("A tooltip probe must not reuse an existing machine base");
        }
        copy.setBaseMetaTileEntity(base);
        return (T) copy;
    }
}
