package com.science.gtnl.common.recipe.gtnl.treeDiagram;

import java.util.List;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.util.data.ItemId;

@Desugar
public record CircuitItemInput(List<ItemId> alternatives, long amount, int chance, int oreDictId) {

    public CircuitItemInput {
        if (alternatives.isEmpty() || amount < 0) throw new IllegalArgumentException("Invalid circuit item input");
        alternatives = List.copyOf(alternatives);
    }
}
