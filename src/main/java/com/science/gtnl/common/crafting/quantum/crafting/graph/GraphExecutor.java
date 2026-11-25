package com.science.gtnl.common.crafting.quantum.crafting.graph;

import java.util.Objects;
import java.util.Set;

import com.science.gtnl.common.crafting.quantum.api.stacks.GenericStack;
import com.science.gtnl.common.crafting.quantum.api.stacks.KeyAmounts;
import com.science.gtnl.common.crafting.quantum.api.stacks.KeyCounter;
import com.science.gtnl.common.crafting.quantum.crafting.CraftBranchFailure;
import com.science.gtnl.common.crafting.quantum.crafting.CraftingCalculation;
import com.science.gtnl.common.crafting.quantum.crafting.inv.ChildCraftingSimulationState;
import com.science.gtnl.common.crafting.quantum.crafting.inv.CraftingSimulationState;

import appeng.api.config.Actionable;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

public class GraphExecutor {

    private final CraftingCalculation calc;
    private final CraftingGraph graph;

    public GraphExecutor(CraftingCalculation calc, CraftingGraph graph) {
        this.calc = calc;
        this.graph = graph;
    }

    public void applyGraph(CraftingSimulationState inv) throws InterruptedException {
        var order = graph.getTopologicalOrder();
        var committedComponents = new IntOpenHashSet();
        for (int nodeIndex = order.size() - 1; nodeIndex >= 0; nodeIndex--) {
            var node = order.get(nodeIndex);
            calc.handlePausing();
            if (node.getLocalComponentId() >= 0) {
                if (committedComponents.add(node.getLocalComponentId()))
                    applyLocalComponent(node.getLocalComponentId(), inv);
                continue;
            }
            if (node.isExternalLeaf()) {
                boolean root = node == graph.getRootNode();
                inv.addStackBytes(node.getWhat(), 1, node.getDemandAmount());
                long extracted = root ? 0 : inv.extract(node.getWhat(), node.getDemandAmount(), Actionable.MODULATE);
                long missing = node.getDemandAmount() - extracted;
                if (missing > 0) calc.addMissing(node.getWhat(), missing);
                continue;
            }
            if (node.getCraftTimes() == 0 && !hasCycleSeed(node)) continue;
            if (node.isLocalUnit()) applyLocalUnit(node, inv);
            else applyNode(node, inv);
        }
        var root = graph.getRootNode();
        if (root == null) {
            throw new IllegalStateException("Graph has no root node");
        }
        if (root.getLocalComponentId() < 0 && !root.isExternalLeaf() && root.getCraftTimes() >= 0) {
            // No parent edge owns the root native/emitter output stack.
            inv.addStackBytes(root.getWhat(), root.getDemandAmount(), 1);
        }
    }

    private void applyLocalComponent(int componentId, CraftingSimulationState inventory) {
        var plan = graph.getLocalComponentPlan(componentId);
        if (plan == null) throw new IllegalStateException("Recursive component has no local plan");
        var marker = calc.createCalculationMarker();
        var transaction = new ChildCraftingSimulationState(inventory);
        try {
            var missing = extractBoundaries(plan.boundaryDemands(), transaction);
            plan.commit(calc, transaction);
            transaction.applyDiff(inventory);
            missing.forEach(calc::addMissing);
        } catch (RuntimeException | Error failure) {
            calc.restoreCalculationMarker(marker);
            throw failure;
        }
        var nodes = graph.getTopology()
            .getComponents()
            .get(componentId)
            .nodes();
        for (var entry : plan.entries()) {
            var node = nodes.get(entry.nodeIndex());
            node.setCraftTimes(Math.addExact(node.getCraftTimes(), entry.craftTimes()));
        }
    }

    private void applyLocalUnit(CraftingGraphNode node, CraftingSimulationState inventory) throws InterruptedException {
        try {
            var plan = graph.getLocalUnitPlan(node);
            if (plan == null || !plan.canApplyTo(inventory) || plan.craftTimes() != node.getCraftTimes()) {
                plan = calc.previewLocalUnit(
                    node.getWhat(),
                    node.getPatternCandidates(),
                    node.getDemandAmount(),
                    Set.of(node.getWhat()),
                    inventory);
            }
            var boundaries = plan.boundaryDemands();
            var planned = node.getPlannedBoundaryDemands();
            if (planned == null) throw new IllegalStateException("Local unit has no planned boundary");
            for (var boundary : boundaries) {
                if (boundary.getLongValue() > planned.get(boundary.getKey())) {
                    throw new IllegalStateException("Local crafting boundary grew for " + node.getWhat());
                }
            }
            var marker = calc.createCalculationMarker();
            var transaction = new ChildCraftingSimulationState(inventory);
            try {
                var missing = extractBoundaries(boundaries, transaction);
                plan.commit(calc, transaction);
                transaction.applyDiff(inventory);
                missing.forEach(calc::addMissing);
            } catch (RuntimeException | Error failure) {
                calc.restoreCalculationMarker(marker);
                throw failure;
            }
            node.setCraftTimes(plan.craftTimes());
        } catch (CraftBranchFailure failure) {
            throw new IllegalStateException("Local crafting unit failed for " + node.getWhat(), failure);
        }
    }

    private static KeyCounter extractBoundaries(KeyCounter boundaries, CraftingSimulationState inventory) {
        var missing = KeyCounter.checked();
        for (var boundary : boundaries) {
            long extracted = inventory.extract(boundary.getKey(), boundary.getLongValue(), Actionable.MODULATE);
            inventory.addStackBytes(boundary.getKey(), extracted, 1);
            if (extracted < boundary.getLongValue())
                missing.add(boundary.getKey(), boundary.getLongValue() - extracted);
        }
        return missing;
    }

    private void applyNode(CraftingGraphNode node, CraftingSimulationState inv) throws InterruptedException {
        long times = node.getCraftTimes();
        var pattern = node.getPattern();

        if (node.isEmitter()) {
            applyEmitter(node, inv, times, node == graph.getRootNode());
            return;
        }

        var sccPlan = graph.getTopology()
            .isCyclic(node)
                ? graph.getSccPlan(
                    graph.getTopology()
                        .getComponentId(node))
                : null;
        boolean ring = sccPlan != null && sccPlan.ring();
        CraftingGraphEdge cycleCut = null;
        for (var edge : node.getInputs()) {
            if (cycleCut == null && edge.cycleCut()) cycleCut = edge;
            long needed = edge.cycleCut() ? edge.seedDemand()
                : KeyAmounts.multiply(edge.inputKey(), edge.amountPerCraft(), times);
            if (!edge.cycleCut() && (edge.producer() == null || edge.producer()
                .getLocalComponentId() < 0)) {
                inv.addStackBytes(edge.inputKey(), edge.amountPerCraft(), times);
            }
            if (edge.cycleCut() && needed == 0) {
                continue;
            }
            long stockedSeed = edge.cycleCut() ? inv.extract(edge.inputKey(), needed, Actionable.SIMULATE) : 0;
            if (edge.cycleCut() && !ring && stockedSeed < needed) {
                calc.craftGraphSeed(edge.inputKey(), needed - stockedSeed, pattern, inv);
            }
            long got = inv.extract(edge.inputKey(), needed, Actionable.MODULATE);
            long shortfall = needed - got;
            if (edge.cycleCut()) {
                inv.addStackBytes(edge.inputKey(), got, 1);
                edge.recordSeedResult(got, shortfall);
                if (ring) {
                    // A ring plans its reserve itself. A seed it lacks is a recursive missing seed, which keeps it
                    // missing although the ring crafts more of it.
                    if (shortfall > 0) {
                        calc.recordGraphRecursiveSeed(node.getWhat(), edge.inputKey(), needed, got, shortfall, 0);
                    }
                } else {
                    calc.recordGraphRecursiveSeed(
                        node.getWhat(),
                        edge.inputKey(),
                        needed,
                        got,
                        shortfall,
                        edge.reserveDemand());
                }
            } else if (shortfall > 0) {
                calc.addMissing(edge.inputKey(), shortfall);
            }
        }

        for (GenericStack output : node.getOutputs()) {
            long outputAmount = KeyAmounts.multiply(output.what(), output.amount(), times);
            if (ring && output.what()
                .equals(node.getWhat())) {
                // A node of the ring applied before this one already took its share by a cut input
                outputAmount = Math.max(
                    0,
                    outputAmount - Objects.requireNonNull(sccPlan)
                        .takenByCycle(node));
            } else if (cycleCut != null && output.what()
                .equals(node.getWhat())) {
                    if (sccPlan != null) {
                        // Missing seed is already recorded separately. As with other missing inputs, downstream
                        // accounting assumes it will be supplied; otherwise the same shortage is charged twice.
                        outputAmount = sccPlan.plannedOutput(node);
                    }
                }
            inv.insert(output.what(), outputAmount, Actionable.MODULATE);
        }

        inv.addCrafting(pattern, times);
        inv.addBytes(times);

        if ((ring || cycleCut != null) && node == graph.getRootNode()) {
            // The ring takes back what was crafted of the requested item beyond the request; the CPU keeps it
            long crafted = KeyAmounts.multiply(node.getWhat(), node.getOutputPerCraft(), times);
            calc.addGraphIntermediateFinalOutput(Math.max(0, crafted - node.getDemandAmount()));
        }
    }

    private static boolean hasCycleSeed(CraftingGraphNode node) {
        for (var edge : node.getInputs()) {
            if (edge.cycleCut() && edge.seedDemand() > 0) return true;
        }
        return false;
    }

    private void applyEmitter(CraftingGraphNode node, CraftingSimulationState inv, long amount, boolean root) {
        long extracted = root ? 0 : inv.extract(node.getWhat(), amount, Actionable.MODULATE);
        long emitted = amount - extracted;
        if (emitted > 0) {
            inv.emitItems(node.getWhat(), emitted);
        }
        // Downstream graph consumers still extract their input from the simulation inventory. Reinsert the fulfilled
        // amount for them; at the root, the extraction/emission itself is the final fulfillment.
        if (!node.getParents()
            .isEmpty()) {
            inv.insert(node.getWhat(), amount, Actionable.MODULATE);
        }
    }
}
