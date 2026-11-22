package com.science.gtnl.utils.machine.bee;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.science.gtnl.utils.machine.bee.BeeBreedingHelper.BreedingStep;
import com.science.gtnl.utils.machine.bee.BeeBreedingHelper.MutationData;

import lombok.Getter;

public class BeeBreedingPlanner {

    private static final double MIN_CHANCE_FOR_COST = 0.01D;

    private BeeBreedingPlanner() {}

    public static List<String> findReachableSpecies(Set<String> unlockedSpecies) {
        if (unlockedSpecies == null || unlockedSpecies.isEmpty()) return Collections.emptyList();
        Set<String> reachable = new HashSet<>(unlockedSpecies);
        Map<String, List<MutationData>> dependents = new HashMap<>();
        Map<MutationData, Integer> remaining = new IdentityHashMap<>();
        for (MutationData mutation : BeeBreedingHelper.getAllMutations()) {
            boolean sameParent = mutation.parent1.equals(mutation.parent2);
            remaining.put(mutation, sameParent ? 1 : 2);
            dependents.computeIfAbsent(mutation.parent1, ignored -> new ArrayList<>())
                .add(mutation);
            if (!sameParent) dependents.computeIfAbsent(mutation.parent2, ignored -> new ArrayList<>())
                .add(mutation);
        }
        ArrayDeque<String> ready = new ArrayDeque<>(unlockedSpecies);
        while (!ready.isEmpty()) {
            String parent = ready.removeFirst();
            for (MutationData mutation : dependents.getOrDefault(parent, List.of())) {
                int needed = remaining.get(mutation) - 1;
                remaining.put(mutation, needed);
                if (needed == 0 && reachable.add(mutation.result)) ready.addLast(mutation.result);
            }
        }
        reachable.removeAll(unlockedSpecies);
        List<String> result = new ArrayList<>(reachable);
        Collections.sort(result);
        return result;
    }

    public static Plan plan(String targetUID, Set<String> unlockedSpecies) {
        if (targetUID == null || targetUID.isEmpty()) return Plan.empty("");

        Set<String> unlocked = unlockedSpecies == null ? Collections.emptySet() : new HashSet<>(unlockedSpecies);
        Candidate candidate = resolve(targetUID, unlocked, new HashSet<>(), new HashMap<>());
        if (candidate == null) {
            return new Plan(
                targetUID,
                Collections.emptyList(),
                Collections.singleton(targetUID),
                Double.POSITIVE_INFINITY);
        }
        return new Plan(
            targetUID,
            new ArrayList<>(candidate.stepsByResult.values()),
            candidate.missingSpecies,
            candidate.expectedAttempts());
    }

    private static Candidate resolve(String species, Set<String> unlocked, Set<String> visiting,
        Map<String, Candidate> memo) {
        if (unlocked.contains(species)) return Candidate.empty();

        Candidate cached = memo.get(species);
        if (cached != null && Collections.disjoint(cached.stepsByResult.keySet(), visiting)) return cached;
        if (!visiting.add(species)) return null;

        List<MutationData> mutations = BeeBreedingHelper.getMutationsForUID(species);
        mutations.removeIf(
            mutation -> mutation.parent1 == null || mutation.parent1.isEmpty()
                || mutation.parent2 == null
                || mutation.parent2.isEmpty()
                || mutation.parent1.equals(species)
                || mutation.parent2.equals(species));
        mutations.sort(
            Comparator.comparing((MutationData mutation) -> mutation.parent1)
                .thenComparing(mutation -> mutation.parent2)
                .thenComparingDouble(mutation -> -mutation.chance));

        Candidate best = null;
        for (MutationData mutation : mutations) {
            Candidate parent1 = resolve(mutation.parent1, unlocked, visiting, memo);
            Candidate parent2 = resolve(mutation.parent2, unlocked, visiting, memo);
            if (parent1 == null || parent2 == null) continue;

            Candidate candidate = Candidate.combine(
                parent1,
                parent2,
                new BreedingStep(mutation.parent1, mutation.parent2, species, mutation.chance, mutation.foundations));
            if (best == null || compare(candidate, best) < 0) {
                best = candidate;
            }
        }

        visiting.remove(species);
        if (best == null) {
            best = Candidate.missing(species);
        }
        if (best.missingSpecies.isEmpty() || mutations.isEmpty()) memo.put(species, best);
        return best;
    }

    private static int compare(Candidate left, Candidate right) {
        int missing = Integer.compare(left.missingSpecies.size(), right.missingSpecies.size());
        if (missing != 0) return missing;

        int attempts = Double.compare(left.expectedAttempts(), right.expectedAttempts());
        if (attempts != 0) return attempts;

        int stepCount = Integer.compare(left.stepsByResult.size(), right.stepsByResult.size());
        if (stepCount != 0) return stepCount;
        return left.signature()
            .compareTo(right.signature());
    }

    private static class Candidate {

        private final LinkedHashMap<String, BreedingStep> stepsByResult;
        private final LinkedHashSet<String> missingSpecies;

        private Candidate(LinkedHashMap<String, BreedingStep> stepsByResult, LinkedHashSet<String> missingSpecies) {
            this.stepsByResult = stepsByResult;
            this.missingSpecies = missingSpecies;
        }

        private static Candidate empty() {
            return new Candidate(new LinkedHashMap<>(), new LinkedHashSet<>());
        }

        private static Candidate missing(String species) {
            LinkedHashSet<String> missing = new LinkedHashSet<>();
            missing.add(species);
            return new Candidate(new LinkedHashMap<>(), missing);
        }

        private static Candidate combine(Candidate parent1, Candidate parent2, BreedingStep resultStep) {
            LinkedHashMap<String, BreedingStep> steps = new LinkedHashMap<>();
            steps.putAll(parent1.stepsByResult);
            steps.putAll(parent2.stepsByResult);
            steps.put(resultStep.result, resultStep);

            LinkedHashSet<String> missing = new LinkedHashSet<>(parent1.missingSpecies);
            missing.addAll(parent2.missingSpecies);
            return new Candidate(steps, missing);
        }

        private double expectedAttempts() {
            double attempts = 0.0D;
            for (BreedingStep step : stepsByResult.values()) {
                attempts += 100.0D / Math.max(MIN_CHANCE_FOR_COST, step.chance);
            }
            return attempts;
        }

        private String signature() {
            StringBuilder signature = new StringBuilder();
            for (BreedingStep step : stepsByResult.values()) {
                signature.append(step.parent1)
                    .append('+')
                    .append(step.parent2)
                    .append('>')
                    .append(step.result)
                    .append(';');
            }
            return signature.toString();
        }
    }

    public static class Plan {

        @Getter
        private final String targetUID;
        @Getter
        private final List<BreedingStep> steps;
        @Getter
        private final Set<String> missingSpecies;
        @Getter
        private final double expectedAttempts;

        private Plan(String targetUID, List<BreedingStep> steps, Set<String> missingSpecies, double expectedAttempts) {
            this.targetUID = targetUID;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
            this.missingSpecies = Collections.unmodifiableSet(new LinkedHashSet<>(missingSpecies));
            this.expectedAttempts = expectedAttempts;
        }

        public static Plan empty(String targetUID) {
            return new Plan(targetUID, Collections.emptyList(), Collections.emptySet(), 0.0D);
        }

        public String getFirstMissingSpecies() {
            return missingSpecies.isEmpty() ? ""
                : missingSpecies.iterator()
                    .next();
        }

        public List<BreedingStep> getReadySteps(Set<String> unlockedSpecies, int limit) {
            if (limit <= 0) return Collections.emptyList();
            List<BreedingStep> ready = new ArrayList<>();
            for (BreedingStep step : steps) {
                if (unlockedSpecies.contains(step.result)) continue;
                if (unlockedSpecies.contains(step.parent1) && unlockedSpecies.contains(step.parent2)) {
                    ready.add(step);
                    if (ready.size() >= limit) break;
                }
            }
            return ready;
        }
    }
}
