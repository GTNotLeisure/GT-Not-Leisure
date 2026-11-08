package com.science.gtnl.common.part.universal;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import org.jetbrains.annotations.NotNull;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEFluidStack;

public class FluidHandlerInventory implements IMEInventory<IAEFluidStack> {

    private final IFluidHandler handler;
    private final ForgeDirection side;

    public FluidHandlerInventory(IFluidHandler handler, ForgeDirection side) {
        this.handler = handler;
        this.side = side == null ? ForgeDirection.UNKNOWN : side;
    }

    @Override
    public IAEFluidStack injectItems(IAEFluidStack input, Actionable action, BaseActionSource src) {
        if (input == null || input.getFluid() == null || input.getStackSize() <= 0) return input;

        final FluidStack request = input.getFluidStack();
        if (request == null || request.amount <= 0) return input;

        final int filled = this.handler.fill(this.side, request, action == Actionable.MODULATE);
        if (filled <= 0) return input;
        if (filled >= input.getStackSize()) return null;

        final IAEFluidStack leftover = input.copy();
        leftover.setStackSize(input.getStackSize() - filled);
        return leftover;
    }

    @Override
    public IAEFluidStack extractItems(IAEFluidStack request, Actionable action, BaseActionSource src) {
        if (request == null || request.getFluid() == null || request.getStackSize() <= 0) return null;

        final FluidStack wanted = request.getFluidStack();
        if (wanted == null || wanted.amount <= 0) return null;

        final FluidStack drained = this.handler.drain(this.side, wanted, action == Actionable.MODULATE);
        if (drained == null || drained.amount <= 0) return null;

        final IAEFluidStack result = request.copy();
        result.setStackSize(drained.amount);
        return result;
    }

    @Override
    public IItemList<IAEFluidStack> getAvailableItems(IItemList<IAEFluidStack> out, int iteration) {
        final FluidTankInfo[] tanks;
        try {
            tanks = this.handler.getTankInfo(this.side);
        } catch (RuntimeException ignored) {
            // some 1.7.10 handlers throw instead of returning null
            return out;
        }
        if (tanks == null) return out;

        for (FluidTankInfo tank : tanks) {
            if (tank == null || tank.fluid == null || tank.fluid.getFluid() == null || tank.fluid.amount <= 0) continue;
            if (this.handler.drain(this.side, 1, false) == null) continue;
            final IAEFluidStack stack = AEFluidStack.create(tank.fluid);
            if (stack != null && stack.getStackSize() > 0) out.addStorage(stack);
        }
        return out;
    }

    @NotNull
    @Override
    public IAEStackType<IAEFluidStack> getStackType() {
        return FLUID_STACK_TYPE;
    }

    @Override
    public String toString() {
        return "FluidHandlerInventory[" + this.handler.getClass()
            .getName() + '@' + this.side + ']';
    }
}
