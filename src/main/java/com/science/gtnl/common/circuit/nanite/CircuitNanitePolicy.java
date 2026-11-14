package com.science.gtnl.common.circuit.nanite;

import java.util.Comparator;
import java.util.List;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class CircuitNanitePolicy {

    public NaniteEffects select(NaniteProfile[] slots, int requiredTier, int actualParallel) {
        if (slots.length != 4 || slots[0] == null || slots[0].tier() < requiredTier || actualParallel <= 0) return null;
        NaniteProfile main = slots[0];
        List<Integer> auxiliary = new ObjectArrayList<>(3);
        for (int slot = 1; slot < slots.length; slot++) {
            if (slots[slot] != null && slots[slot].tier() >= requiredTier) auxiliary.add(slot);
        }
        auxiliary.sort(
            Comparator.<Integer>comparingInt(slot -> slots[slot].parallel())
                .reversed()
                .thenComparingInt(Integer::intValue));
        int capacity = main.parallel();
        List<NaniteParticipation> participants = new ObjectArrayList<>(4);
        participants.add(new NaniteParticipation(0, main.tier(), Math.min(capacity, actualParallel)));
        for (int rank = 0; rank < auxiliary.size() && capacity < actualParallel; rank++) {
            int slot = auxiliary.get(rank);
            int contribution = slots[slot].parallel() >> (rank + 1);
            if (contribution == 0) continue;
            participants.add(
                new NaniteParticipation(slot, slots[slot].tier(), Math.min(contribution, actualParallel - capacity)));
            capacity = (int) Math.min(Integer.MAX_VALUE, (long) capacity + contribution);
        }
        double cancellation = NaniteMaintenancePolicy.lossCancellation(main.tier());
        double loss = main.branch() == NaniteBranch.STABLE ? 0 : Math.max(0.30, main.lossChance()) - cancellation;
        return new NaniteEffects(main, participants, capacity, Math.max(0, loss));
    }
}
