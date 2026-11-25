package com.science.gtnl.common.crafting.quantum.crafting.graph;

import java.util.Set;

import com.science.gtnl.common.crafting.quantum.crafting.CraftBranchFailure;
import com.science.gtnl.common.crafting.quantum.crafting.CraftingCalculation;
import com.science.gtnl.common.crafting.quantum.crafting.inv.ChildCraftingSimulationState;
import com.science.gtnl.common.crafting.quantum.crafting.inv.CraftingSimulationState;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

public class LocalGraphPlanner {

    private final CraftingCalculation calculation;
    private final CraftingGraph graph;

    public LocalGraphPlanner(CraftingCalculation calculation, CraftingGraph graph) {
        this.calculation = calculation;
        this.graph = graph;
    }

    public boolean plan(CraftingSimulationState inventory, long requestedAmount) throws InterruptedException {
        var propagation = new DemandPropagation(this.calculation);
        propagation.propagate(this.graph, new ChildCraftingSimulationState(inventory));
        if (this.graph.requiresLegacyFallback()) return false;
        var components = new IntOpenHashSet();
        var discoveredDemands = new Object2LongOpenHashMap<CraftingGraphNode>();
        boolean local = false;
        try {
            for (var node : this.graph.getTopologicalOrder()) {
                this.calculation.handlePausing();
                int componentId = node.getLocalComponentId();
                if (componentId >= 0 || node.isLocalUnit()) discoveredDemands.put(node, node.getDemandAmount());
                if (componentId >= 0) {
                    if (components.add(componentId)) {
                        var component = this.graph.getTopology()
                            .getComponents()
                            .get(componentId);
                        var plan = this.calculation.previewLocalComponent(component.nodes(), inventory);
                        this.graph.setLocalComponentPlan(componentId, plan);
                    }
                    local = true;
                } else if (node.isLocalUnit() && node.getDemandAmount() > 0) {
                    var plan = this.calculation.previewLocalUnit(
                        node.getWhat(),
                        node.getPatternCandidates(),
                        node.getDemandAmount(),
                        Set.of(node.getWhat()),
                        inventory);
                    node.setPlannedBoundaryDemands(plan.boundaryDemands());
                    this.graph.setLocalUnitPlan(node, plan);
                    local = true;
                }
            }
        } catch (CraftBranchFailure failure) {
            return false;
        }
        if (local) {
            for (var node : this.graph.getAllNodes()) node.resetPlanningAmounts();
            var root = this.graph.getRootNode();
            if (root == null) throw new IllegalStateException("Graph has no root node");
            root.setDemandAmount(requestedAmount);
            propagation.propagate(this.graph, null);
            for (var demand : discoveredDemands.object2LongEntrySet()) {
                if (demand.getKey()
                    .getDemandAmount() != demand.getLongValue()) return false;
            }
        }
        this.calculation.recordPerformanceCount("localComponents", components.size());
        return !this.graph.requiresLegacyFallback();
    }
}
