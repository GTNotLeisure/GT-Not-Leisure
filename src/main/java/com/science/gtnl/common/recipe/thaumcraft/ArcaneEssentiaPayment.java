package com.science.gtnl.common.recipe.thaumcraft;

import java.util.function.Function;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public final class ArcaneEssentiaPayment {

    public static <K> boolean matchesDelivery(Object2LongMap<K> required, long crafts, Object2LongMap<K> delivered) {
        if (crafts <= 0 || required.size() != delivered.size()) return false;
        for (var entry : Object2LongMaps.fastIterable(required)) {
            long unit = entry.getLongValue();
            if (unit <= 0 || unit > Long.MAX_VALUE / crafts || delivered.getLong(entry.getKey()) != unit * crafts)
                return false;
        }
        return true;
    }

    @FunctionalInterface
    public interface Source<K> {

        long extract(K aspect, long requested, boolean simulate);
    }

    public static <K, P> Source<K> combine(Iterable<P> providers, Function<P, ?> gridOf,
        Function<P, Source<K>> sourceOf) {
        var seen = new ReferenceOpenHashSet<>();
        var networks = new ObjectArrayList<Source<K>>();
        for (P provider : providers) {
            var grid = gridOf.apply(provider);
            if (grid != null && seen.add(grid)) networks.add(sourceOf.apply(provider));
        }
        return (aspect, requested, simulate) -> {
            long remaining = requested;
            for (var network : networks) {
                if (remaining == 0) break;
                remaining -= network.extract(aspect, remaining, simulate);
            }
            return requested - remaining;
        };
    }

    public static <K> long affordable(Object2LongMap<K> cost, Object2LongMap<K> credit, Source<K> source, long crafts) {
        for (var entry : Object2LongMaps.fastIterable(cost)) {
            K aspect = entry.getKey();
            long unit = entry.getLongValue();
            crafts = Math.min(crafts, Long.MAX_VALUE / unit);
            long required = crafts * unit;
            long held = credit.getLong(aspect);
            if (required > held) {
                long available = source.extract(aspect, required - held, true);
                crafts = (held + available) / unit;
            }
            if (crafts == 0) break;
        }
        return crafts;
    }

    public static <K> boolean pay(Object2LongMap<K> cost, Object2LongMap<K> credit, Source<K> source, long crafts) {
        if (crafts <= 0 || affordable(cost, credit, source, crafts) < crafts) return false;
        for (var entry : Object2LongMaps.fastIterable(cost)) {
            K aspect = entry.getKey();
            long held = credit.getLong(aspect);
            long missing = entry.getLongValue() * crafts - held;
            if (missing <= 0) continue;
            long taken = source.extract(aspect, missing, false);
            if (taken > 0) credit.put(aspect, held + taken);
            if (taken != missing) return false;
        }
        for (var entry : Object2LongMaps.fastIterable(cost)) {
            K aspect = entry.getKey();
            long left = credit.getLong(aspect) - entry.getLongValue() * crafts;
            if (left == 0) credit.removeLong(aspect);
            else credit.put(aspect, left);
        }
        return true;
    }
}
