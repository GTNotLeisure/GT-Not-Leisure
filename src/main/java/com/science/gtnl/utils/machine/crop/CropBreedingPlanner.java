package com.science.gtnl.utils.machine.crop;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizon.cropsnh.api.ICropCard;
import com.gtnewhorizon.cropsnh.api.ICropMutation;
import com.gtnewhorizon.cropsnh.farming.registries.MutationRegistry;
import com.gtnewhorizon.cropsnh.farming.requirements.breeding.WorldOnlyBreedingRequirement;

public class CropBreedingPlanner {

    private static volatile List<ICropMutation> registryMutations;

    private CropBreedingPlanner() {}

    public static Plan plan(String target, Set<String> archive) {
        List<ICropMutation> mutations = registryMutations;
        if (mutations == null) {
            mutations = machineMutations(MutationRegistry.instance.getDeterministicMutations());
            if (!mutations.isEmpty()) registryMutations = mutations;
        }
        return search(target, archive, mutations);
    }

    public static Plan plan(String target, Set<String> archive, Collection<ICropMutation> recipes) {
        return search(target, archive, machineMutations(recipes));
    }

    private static List<ICropMutation> machineMutations(Collection<ICropMutation> recipes) {
        List<ICropMutation> mutations = new ArrayList<>(recipes);
        mutations.removeIf(
            m -> m.getParents()
                .isEmpty()
                || m.getRequirements()
                    .stream()
                    .anyMatch(WorldOnlyBreedingRequirement.class::isInstance));
        mutations.sort(Comparator.comparing(CropBreedingPlanner::signature));
        return Collections.unmodifiableList(mutations);
    }

    private static Plan search(String target, Set<String> archive, List<ICropMutation> mutations) {
        if (archive.isEmpty()) return new Plan(Collections.emptyList(), Collections.emptyList());
        Map<String, Candidate> candidates = new HashMap<>();
        for (String id : archive) candidates.put(id, new Candidate());
        Map<String, List<ICropMutation>> dependents = new HashMap<>();
        ArrayDeque<ICropMutation> queue = new ArrayDeque<>();
        Set<ICropMutation> queued = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ICropMutation mutation : mutations) {
            if (archive.contains(
                mutation.getOutput()
                    .getId()))
                continue;
            for (ICropCard parent : mutation.getParents()) {
                dependents.computeIfAbsent(parent.getId(), id -> new ArrayList<>())
                    .add(mutation);
            }
            if (queued.add(mutation)) queue.addLast(mutation);
        }
        while (!queue.isEmpty()) {
            ICropMutation mutation = queue.removeFirst();
            queued.remove(mutation);
            String result = mutation.getOutput()
                .getId();
            Candidate next = resolve(mutation, candidates);
            if (next == null) continue;
            Candidate old = candidates.get(result);
            if (old != null && next.cost >= old.cost) continue;
            candidates.put(result, next);
            for (ICropMutation dependent : dependents.getOrDefault(result, Collections.emptyList())) {
                if (queued.add(dependent)) queue.addLast(dependent);
            }
        }

        Candidate best = candidates.get(target);
        List<String> reachable = new ArrayList<>(candidates.keySet());
        reachable.removeAll(archive);
        Collections.sort(reachable);
        return new Plan(best == null ? Collections.emptyList() : new ArrayList<>(best.steps.values()), reachable);
    }

    private static Candidate resolve(ICropMutation mutation, Map<String, Candidate> candidates) {
        String output = mutation.getOutput()
            .getId();
        for (ICropCard parent : mutation.getParents()) {
            Candidate source = candidates.get(parent.getId());
            if (source == null || parent.getId()
                .equals(output) || source.steps.containsKey(output)) return null;
        }
        Candidate result = new Candidate();
        for (ICropCard parent : mutation.getParents()) result.steps.putAll(candidates.get(parent.getId()).steps);
        result.steps.put(output, mutation);
        Map<String, ICropMutation> pending = new LinkedHashMap<>(result.steps);
        result.steps.clear();
        while (!pending.isEmpty()) {
            boolean added = false;
            var iterator = pending.entrySet()
                .iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                boolean ready = true;
                for (ICropCard parent : entry.getValue()
                    .getParents()) {
                    Candidate source = candidates.get(parent.getId());
                    if (source != null && source.steps.isEmpty()) continue;
                    if (pending.containsKey(parent.getId())) {
                        ready = false;
                        break;
                    }
                }
                if (!ready) continue;
                result.steps.put(entry.getKey(), entry.getValue());
                iterator.remove();
                added = true;
            }
            if (!added) return null;
        }
        for (ICropMutation step : result.steps.values())
            result.cost += CropBreedingRecipeRules.baseDuration(step.getOutput());
        return result;
    }

    private static String signature(ICropMutation mutation) {
        StringBuilder result = new StringBuilder(
            mutation.getOutput()
                .getId());
        for (ICropCard parent : mutation.getParents()) result.append('|')
            .append(parent.getId());
        return result.toString();
    }

    private static class Candidate {

        private final Map<String, ICropMutation> steps = new LinkedHashMap<>();
        private long cost;
    }

    public static class Plan {

        public final List<ICropMutation> steps;
        public final List<String> reachable;

        private Plan(List<ICropMutation> steps, List<String> reachable) {
            this.steps = Collections.unmodifiableList(steps);
            this.reachable = Collections.unmodifiableList(reachable);
        }
    }
}
