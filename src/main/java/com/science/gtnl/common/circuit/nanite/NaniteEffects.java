package com.science.gtnl.common.circuit.nanite;

import java.util.List;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record NaniteEffects(NaniteProfile main, List<NaniteParticipation> participants, int maxParallel,
    double lossChance) {

    public NaniteEffects {
        participants = List.copyOf(participants);
    }

    public int lockedSlots() {
        int mask = 0;
        for (NaniteParticipation participant : participants) mask |= 1 << participant.slot();
        return mask;
    }
}
