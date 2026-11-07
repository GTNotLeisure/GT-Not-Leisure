package com.science.gtnl.common.machine.tooltip;

import java.util.List;

public record StructureReadResult(StructureSummary summary, List<String> problems) {

    public StructureReadResult {
        problems = List.copyOf(problems);
    }
}
