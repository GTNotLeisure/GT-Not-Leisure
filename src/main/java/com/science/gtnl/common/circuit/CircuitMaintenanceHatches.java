package com.science.gtnl.common.circuit;

import java.util.List;
import java.util.Set;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.science.gtnl.common.circuit.CircuitResourceLedger.FluidCharge;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.item.AEFluidStack;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.common.tileentities.machines.MTEHatchInputME;
import it.unimi.dsi.fastutil.objects.Reference2DoubleMap;
import it.unimi.dsi.fastutil.objects.Reference2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public class CircuitMaintenanceHatches {

    private final List<MTEHatchInput> hatches;

    public CircuitMaintenanceHatches(List<MTEHatchInput> hatches) {
        this.hatches = hatches;
    }

    public String missingFluid(List<FluidCharge> charges) {
        Reference2DoubleMap<IEnergyGrid> reservedPower = new Reference2DoubleOpenHashMap<>();
        for (FluidCharge charge : charges) {
            Fluid fluid = FluidRegistry.getFluid(charge.fluidName());
            if (fluid == null) return charge.fluidName();
            long remaining = charge.amount();
            Set<IMEMonitor<IAEFluidStack>> visited = new ReferenceOpenHashSet<>();
            for (MTEHatchInput hatch : hatches) {
                if (!hatch.isValid()) continue;
                FluidStack request = new FluidStack(fluid, (int) Math.min(Integer.MAX_VALUE, remaining));
                int available = available(hatch, request);
                if (available <= 0) continue;
                if (hatch instanceof MTEHatchInputME networkHatch) {
                    try {
                        AENetworkProxy proxy = networkHatch.getProxy();
                        IMEMonitor<IAEFluidStack> storage = proxy.getStorage()
                            .getFluidInventory();
                        if (!visited.add(storage)) continue;
                        available = sharedAvailability(storage, request, available);
                        IEnergyGrid energy = proxy.getEnergy();
                        int amountPerUnit = AEFluidStack.create(request)
                            .getAmountPerUnit();
                        double previous = reservedPower.getDouble(energy);
                        double required = Math.ceil(available / (double) amountPerUnit);
                        double supply = energy
                            .extractAEPower(previous + required, Actionable.SIMULATE, PowerMultiplier.CONFIG);
                        available = (int) Math
                            .min(available, Math.max(0, Math.floor(supply - previous)) * amountPerUnit);
                        reservedPower.put(energy, previous + Math.ceil(available / (double) amountPerUnit));
                    } catch (GridAccessException e) {
                        continue;
                    }
                }
                remaining -= Math.min(remaining, available);
                if (remaining == 0) break;
            }
            if (remaining > 0) return charge.fluidName();
        }
        return "";
    }

    private int sharedAvailability(IMEMonitor<IAEFluidStack> storage, FluidStack request, int available) {
        // Recipe preparation mutates one shared input view. Do not count an untouched ME alias a second time.
        for (MTEHatchInput hatch : hatches) {
            if (!(hatch instanceof MTEHatchInputME networkHatch) || !hatch.isValid()) continue;
            try {
                if (networkHatch.getProxy()
                    .getStorage()
                    .getFluidInventory() != storage) continue;
                FluidStack simulated = hatch.drain(ForgeDirection.UNKNOWN, request, false);
                if (simulated != null && simulated.isFluidEqual(request))
                    available = Math.min(available, Math.max(0, simulated.amount));
            } catch (GridAccessException ignored) {
                return 0;
            }
        }
        return available;
    }

    private static int available(MTEHatchInput hatch, FluidStack request) {
        FluidStack simulated = hatch.drain(ForgeDirection.UNKNOWN, request, false);
        return simulated != null && simulated.isFluidEqual(request)
            ? Math.max(0, Math.min(request.amount, simulated.amount))
            : 0;
    }
}
