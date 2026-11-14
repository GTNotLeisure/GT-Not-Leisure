package com.science.gtnl.common.circuit;

import java.util.List;
import java.util.Objects;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import com.science.gtnl.common.circuit.nanite.NaniteMaintenanceRequirement;
import com.science.gtnl.common.circuit.nanite.NaniteOutputRoll;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;

@Getter
public class CircuitWorkState {

    private static final int SCHEMA_VERSION = 1;

    private final String familyId;
    private final boolean legacy;
    private final int circuitTier;
    private final double totalWork;
    private double completedWork;
    private final double baseWorkRate;
    private final long baseEu;
    private final long requiredCu;
    private final int lockedSlots;
    private final List<NaniteMaintenanceRequirement> maintenance;
    private final NaniteOutputRoll outputRoll;
    private final ItemStack[] itemOutputs;
    private final FluidStack[] fluidOutputs;

    public CircuitWorkState(String familyId, int circuitTier, double totalWork, double baseWorkRate, long baseEu,
        long requiredCu, int lockedSlots, List<NaniteMaintenanceRequirement> maintenance, NaniteOutputRoll outputRoll,
        ItemStack[] itemOutputs, FluidStack[] fluidOutputs) {
        this(
            familyId,
            circuitTier,
            totalWork,
            baseWorkRate,
            baseEu,
            requiredCu,
            lockedSlots,
            maintenance,
            outputRoll,
            itemOutputs,
            fluidOutputs,
            false);
    }

    private CircuitWorkState(String familyId, int circuitTier, double totalWork, double baseWorkRate, long baseEu,
        long requiredCu, int lockedSlots, List<NaniteMaintenanceRequirement> maintenance, NaniteOutputRoll outputRoll,
        ItemStack[] itemOutputs, FluidStack[] fluidOutputs, boolean legacy) {
        if (familyId == null || circuitTier < 0
            || circuitTier > 14
            || !Double.isFinite(totalWork)
            || totalWork <= 0
            || totalWork > Integer.MAX_VALUE
            || !Double.isFinite(baseWorkRate)
            || baseWorkRate <= 0
            || baseEu < 0
            || requiredCu <= 0
            || (lockedSlots & 1) == 0
            || (lockedSlots & ~15) != 0) throw new IllegalArgumentException("Invalid circuit work state");
        this.familyId = familyId;
        this.legacy = legacy;
        this.circuitTier = circuitTier;
        this.totalWork = totalWork;
        this.baseWorkRate = baseWorkRate;
        this.baseEu = baseEu;
        this.requiredCu = requiredCu;
        this.lockedSlots = lockedSlots;
        this.maintenance = List.copyOf(maintenance);
        this.outputRoll = Objects.requireNonNull(outputRoll);
        this.itemOutputs = copy(itemOutputs);
        this.fluidOutputs = copy(fluidOutputs);
    }

    public static CircuitWorkState resumeLegacy(int totalTicks, int completedTicks, long baseEu, ItemStack[] items,
        FluidStack[] fluids) {
        List<ItemStack> itemOutputs = new ObjectArrayList<>();
        if (items != null)
            for (ItemStack stack : items) if (stack != null && stack.stackSize > 0) itemOutputs.add(stack);
        List<FluidStack> fluidOutputs = new ObjectArrayList<>();
        if (fluids != null)
            for (FluidStack stack : fluids) if (stack != null && stack.amount > 0) fluidOutputs.add(stack);
        CircuitWorkState work = new CircuitWorkState(
            "legacy",
            0,
            totalTicks,
            1,
            baseEu,
            1,
            15,
            List.of(),
            new NaniteOutputRoll(1, 0, 0, 0),
            itemOutputs.toArray(new ItemStack[0]),
            fluidOutputs.toArray(new FluidStack[0]),
            true);
        work.advance(Math.max(0, completedTicks));
        return work;
    }

    public double remainingWork() {
        return Math.max(0, totalWork - completedWork);
    }

    public void advance(double work) {
        if (!Double.isFinite(work) || work < 0) throw new IllegalArgumentException("Invalid circuit progress");
        completedWork = Math.min(totalWork, completedWork + work);
    }

    public ItemStack[] getItemOutputs() {
        return copy(itemOutputs);
    }

    public FluidStack[] getFluidOutputs() {
        return copy(fluidOutputs);
    }

    private static ItemStack[] copy(ItemStack[] values) {
        if (values == null) return new ItemStack[0];
        ItemStack[] result = new ItemStack[values.length];
        for (int i = 0; i < values.length; i++) result[i] = values[i].copy();
        return result;
    }

    private static FluidStack[] copy(FluidStack[] values) {
        if (values == null) return new FluidStack[0];
        FluidStack[] result = new FluidStack[values.length];
        for (int i = 0; i < values.length; i++) result[i] = values[i].copy();
        return result;
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("version", SCHEMA_VERSION);
        tag.setBoolean("legacy", legacy);
        tag.setString("family", familyId);
        tag.setInteger("tier", circuitTier);
        tag.setDouble("total", totalWork);
        tag.setDouble("completed", completedWork);
        tag.setDouble("rate", baseWorkRate);
        tag.setLong("eu", baseEu);
        tag.setLong("cu", requiredCu);
        tag.setInteger("slots", lockedSlots);
        tag.setInteger("parallels", outputRoll.parallels());
        tag.setInteger("loss", outputRoll.failedParallels());
        tag.setInteger("bonus", outputRoll.bonusParallels());
        tag.setDouble("bonusFraction", outputRoll.bonusFraction());
        NBTTagList upkeep = new NBTTagList();
        for (NaniteMaintenanceRequirement requirement : maintenance) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("fluid", requirement.fluidName());
            entry.setLong("amount", requirement.milliBucketsPerSecond());
            upkeep.appendTag(entry);
        }
        tag.setTag("maintenance", upkeep);
        NBTTagList items = new NBTTagList();
        for (ItemStack stack : itemOutputs) {
            NBTTagCompound entry = stack.writeToNBT(new NBTTagCompound());
            entry.setInteger("amount", stack.stackSize);
            items.appendTag(entry);
        }
        tag.setTag("items", items);
        NBTTagList fluids = new NBTTagList();
        for (FluidStack stack : fluidOutputs) fluids.appendTag(stack.writeToNBT(new NBTTagCompound()));
        tag.setTag("fluids", fluids);
        return tag;
    }

    public static CircuitWorkState read(NBTTagCompound tag) {
        if (tag.getInteger("version") != SCHEMA_VERSION)
            throw new IllegalArgumentException("Unknown circuit work version");
        List<NaniteMaintenanceRequirement> upkeep = new ObjectArrayList<>(3);
        NBTTagList maintenance = tag.getTagList("maintenance", 10);
        for (int i = 0; i < maintenance.tagCount(); i++) {
            NBTTagCompound entry = maintenance.getCompoundTagAt(i);
            upkeep.add(new NaniteMaintenanceRequirement(entry.getString("fluid"), entry.getLong("amount")));
        }
        List<ItemStack> items = new ObjectArrayList<>();
        NBTTagList storedItems = tag.getTagList("items", 10);
        for (int i = 0; i < storedItems.tagCount(); i++) {
            NBTTagCompound entry = storedItems.getCompoundTagAt(i);
            ItemStack stack = ItemStack.loadItemStackFromNBT(entry);
            if (stack == null || entry.getInteger("amount") <= 0)
                throw new IllegalArgumentException("Missing circuit output item");
            stack.stackSize = entry.getInteger("amount");
            items.add(stack);
        }
        List<FluidStack> fluids = new ObjectArrayList<>();
        NBTTagList storedFluids = tag.getTagList("fluids", 10);
        for (int i = 0; i < storedFluids.tagCount(); i++) {
            FluidStack stack = FluidStack.loadFluidStackFromNBT(storedFluids.getCompoundTagAt(i));
            if (stack == null || stack.amount <= 0) throw new IllegalArgumentException("Missing circuit output fluid");
            fluids.add(stack);
        }
        NaniteOutputRoll roll = new NaniteOutputRoll(
            tag.getInteger("parallels"),
            tag.getInteger("loss"),
            tag.getInteger("bonus"),
            tag.getDouble("bonusFraction"));
        CircuitWorkState state = new CircuitWorkState(
            tag.getString("family"),
            tag.getInteger("tier"),
            tag.getDouble("total"),
            tag.getDouble("rate"),
            tag.getLong("eu"),
            tag.getLong("cu"),
            tag.getInteger("slots"),
            upkeep,
            roll,
            items.toArray(new ItemStack[0]),
            fluids.toArray(new FluidStack[0]),
            tag.getBoolean("legacy"));
        state.advance(tag.getDouble("completed"));
        return state;
    }
}
