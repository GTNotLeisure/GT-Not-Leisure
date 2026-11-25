package com.science.gtnl.common.block.blocks;

import java.util.Objects;
import java.util.regex.Pattern;

public record MentalMathUnitDefinition(String name, long bytes, int parallelism) {

    private static final Pattern UNIT_NAME = Pattern.compile("[a-z0-9_]+");

    public MentalMathUnitDefinition {
        Objects.requireNonNull(name, "name");
        if (!UNIT_NAME.matcher(name)
            .matches()) {
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
