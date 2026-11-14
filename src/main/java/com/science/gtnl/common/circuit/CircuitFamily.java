package com.science.gtnl.common.circuit;

import java.util.List;
import java.util.Objects;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.util.data.ItemId;

@Desugar
public record CircuitFamily(String id, ItemId unlockItem, int unlockTier, List<ItemId> members) {

    public CircuitFamily {
        Objects.requireNonNull(id);
        Objects.requireNonNull(unlockItem);
        if (unlockTier < 0 || unlockTier > 14 || members.isEmpty()) {
            throw new IllegalArgumentException("Invalid circuit family: " + id);
        }
        members = List.copyOf(members);
    }

    public int requiredItems() {
        int amount = 524_288;
        for (int tier = 0; tier < unlockTier; tier++) amount = amount * 3 / 4;
        return amount;
    }
}
