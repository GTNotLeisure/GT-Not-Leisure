package com.science.gtnl.common.crafting.quantum.crafting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.ToLongFunction;

/**
 * Compressed dispatch batches shared by planning and execution adapters.
 * Only accepted pushes advance the schedule; repetitions never expand into individual crafts.
 */
public class CraftingDispatchOrder<P> {

    public record Step<P> (P pattern, long crafts) {

        public Step {
            Objects.requireNonNull(pattern, "pattern");
            if (crafts <= 0) throw new IllegalArgumentException("Invalid dispatch count");
        }
    }

    public record Batch<P> (List<Step<P>> steps, long repetitions, boolean ordered) {

        public Batch {
            steps = List.copyOf(steps);
            if (steps.isEmpty() || repetitions <= 0) throw new IllegalArgumentException("Invalid dispatch batch");
            if (!ordered) {
                var patterns = new HashSet<P>();
                for (var step : steps) {
                    if (!patterns.add(step.pattern())) throw new IllegalArgumentException("Duplicate unordered task");
                }
            }
        }
    }

    private final ArrayDeque<Batch<P>> batches = new ArrayDeque<>();
    private long[] remaining = new long[0];
    private long repetitions;
    private int cursor;

    public CraftingDispatchOrder(List<Batch<P>> batches) {
        this.batches.addAll(batches);
        startBatch();
    }

    public static <P> List<Batch<P>> unordered(Iterable<P> patterns, ToLongFunction<P> count) {
        var steps = new ArrayList<Step<P>>();
        for (var pattern : patterns) {
            long crafts = count.applyAsLong(pattern);
            if (crafts > 0) steps.add(new Step<>(pattern, crafts));
        }
        return steps.isEmpty() ? List.of() : List.of(new Batch<>(steps, 1, false));
    }

    public void append(List<Batch<P>> additions) {
        boolean idle = this.batches.isEmpty();
        this.batches.addAll(additions);
        if (idle) startBatch();
    }

    public boolean isEmpty() {
        return this.batches.isEmpty();
    }

    public long limit(P pattern, long maximum) {
        int index = index(pattern);
        return index < 0 ? 0 : Math.min(maximum, this.remaining[index]);
    }

    public void accepted(P pattern, long count) {
        int index = index(pattern);
        if (index < 0 || count <= 0 || count > this.remaining[index]) {
            throw new IllegalStateException("Push does not match dispatch schedule");
        }
        this.remaining[index] -= count;
        while (this.cursor < this.remaining.length && this.remaining[this.cursor] == 0) this.cursor++;
        if (this.cursor < this.remaining.length) return;
        if (--this.repetitions > 0) {
            resetRemaining();
        } else {
            this.batches.removeFirst();
            startBatch();
        }
    }

    /** Returns the unconsumed schedule for persistence without mutating the running state. */
    public List<Batch<P>> remainingBatches() {
        var result = new ArrayList<Batch<P>>();
        boolean first = true;
        for (var batch : this.batches) {
            if (first) {
                var tail = new ArrayList<Step<P>>();
                for (int i = 0; i < this.remaining.length; i++) {
                    if (this.remaining[i] > 0) tail.add(
                        new Step<>(
                            batch.steps()
                                .get(i)
                                .pattern(),
                            this.remaining[i]));
                }
                result.add(new Batch<>(tail, 1, batch.ordered()));
                if (this.repetitions > 1) result.add(new Batch<>(batch.steps(), this.repetitions - 1, batch.ordered()));
                first = false;
            } else result.add(batch);
        }
        return List.copyOf(result);
    }

    private int index(P pattern) {
        var batch = this.batches.peekFirst();
        if (batch == null) return -1;
        if (batch.ordered()) return Objects.equals(
            batch.steps()
                .get(this.cursor)
                .pattern(),
            pattern) ? this.cursor : -1;
        for (int i = 0; i < batch.steps()
            .size(); i++) {
            if (Objects.equals(
                batch.steps()
                    .get(i)
                    .pattern(),
                pattern)) return i;
        }
        return -1;
    }

    private void startBatch() {
        var batch = this.batches.peekFirst();
        this.repetitions = batch == null ? 0 : batch.repetitions();
        resetRemaining();
    }

    private void resetRemaining() {
        var batch = this.batches.peekFirst();
        this.remaining = batch == null ? new long[0]
            : batch.steps()
                .stream()
                .mapToLong(Step::crafts)
                .toArray();
        this.cursor = 0;
    }
}
