package com.science.gtnl.common.block.blocks;

import java.util.Objects;

public record MentalMathUnitDefinition(String name, long bytes, int parallelism) {

    public MentalMathUnitDefinition {
        Objects.requireNonNull(name, "name");
        if (!name.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException(
                "Unit names must contain only lowercase letters, digits and underscores");
        }
        if (bytes <= 0) {
            throw new IllegalArgumentException("Crafting storage must be positive");
        }
        if (parallelism <= 0) {
            throw new IllegalArgumentException("Crafting parallelism must be positive");
        }
    }
}
