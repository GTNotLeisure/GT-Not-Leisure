package com.science.gtnl.common.crafting.quantum.crafting.graph;

import java.util.Collection;
import java.util.Set;

import com.science.gtnl.common.crafting.quantum.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

public class MaterialClosure {

    private final AEKey output;
    private final ObjectSet<AEKey> keys;
    private final ObjectSet<AEKey> fuzzyKeys;

    public MaterialClosure(AEKey output, Collection<AEKey> keys, Collection<AEKey> fuzzyKeys) {
        this.output = output;
        this.keys = ObjectSets.unmodifiable(new ObjectOpenHashSet<>(keys));
        this.fuzzyKeys = ObjectSets.unmodifiable(new ObjectOpenHashSet<>(fuzzyKeys));
    }

    public AEKey output() {
        return this.output;
    }

    public ObjectSet<AEKey> keys() {
        return this.keys;
    }

    public Set<AEKey> fuzzyKeysOf(Collection<AEKey> wanted) {
        if (this.fuzzyKeys.isEmpty()) return Set.of();
        var result = new ObjectOpenHashSet<AEKey>();
        for (var key : wanted) {
            if (this.fuzzyKeys.contains(key)) result.add(key);
        }
        return ObjectSets.unmodifiable(result);
    }
}
