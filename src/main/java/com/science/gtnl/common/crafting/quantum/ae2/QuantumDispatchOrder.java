package com.science.gtnl.common.crafting.quantum.ae2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.Nullable;

import com.science.gtnl.common.crafting.quantum.crafting.CraftingDispatchOrder;
import com.science.gtnl.common.crafting.quantum.crafting.CraftingPlan;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public class QuantumDispatchOrder {

    private @Nullable CraftingDispatchOrder<ICraftingPatternDetails> active;
    private List<CraftingDispatchOrder.Batch<ICraftingPatternDetails>> staged = List.of();

    public void stage(CraftingPlan plan) {
        var batches = new ArrayList<CraftingDispatchOrder.Batch<ICraftingPatternDetails>>();
        for (var batch : plan.dispatchOrder()) {
            var steps = new ArrayList<CraftingDispatchOrder.Step<ICraftingPatternDetails>>();
            for (var step : batch.steps()) {
                steps.add(
                    new CraftingDispatchOrder.Step<>(((QuantumPattern) step.pattern()).getDetails(), step.crafts()));
            }
            batches.add(new CraftingDispatchOrder.Batch<>(steps, batch.repetitions(), batch.ordered()));
        }
        this.staged = List.copyOf(batches);
    }

    public void commit(Map<ICraftingPatternDetails, Long> before, Map<ICraftingPatternDetails, Long> after) {
        if (this.active == null && this.staged.isEmpty()) return;
        var additions = new Object2LongOpenHashMap<ICraftingPatternDetails>();
        after.forEach((pattern, count) -> {
            long added = count - before.getOrDefault(pattern, 0L);
            if (added > 0) additions.put(pattern, added);
        });
        var incoming = this.staged.isEmpty() ? CraftingDispatchOrder.unordered(additions.keySet(), additions::getLong)
            : this.staged;
        validateCounts(incoming, additions);
        if (this.active == null) {
            this.active = new CraftingDispatchOrder<>(CraftingDispatchOrder.unordered(before.keySet(), before::get));
        }
        this.active.append(incoming);
    }

    public void discardStaged() {
        this.staged = List.of();
    }

    public boolean canDispatch(ICraftingPatternDetails pattern) {
        return this.active == null || this.active.limit(pattern, 1) > 0;
    }

    public long limit(ICraftingPatternDetails pattern, long maximum) {
        return this.active == null ? maximum : this.active.limit(pattern, maximum);
    }

    public void accepted(ICraftingPatternDetails pattern, long crafts) {
        if (this.active != null) this.active.accepted(pattern, crafts);
    }

    public void clear() {
        this.active = null;
        discardStaged();
    }

    public boolean isActive() {
        return this.active != null;
    }

    public NBTTagCompound writeToNBT() {
        var result = new NBTTagCompound();
        if (this.active == null) return result;
        var patterns = new ArrayList<ICraftingPatternDetails>();
        var indices = new Object2LongOpenHashMap<ICraftingPatternDetails>();
        var batches = new NBTTagList();
        for (var batch : this.active.remainingBatches()) {
            var batchTag = new NBTTagCompound();
            batchTag.setLong("repetitions", batch.repetitions());
            batchTag.setBoolean("ordered", batch.ordered());
            var steps = new NBTTagList();
            for (var step : batch.steps()) {
                if (!indices.containsKey(step.pattern())) {
                    indices.put(step.pattern(), patterns.size());
                    patterns.add(step.pattern());
                }
                var stepTag = new NBTTagCompound();
                stepTag.setInteger("pattern", (int) indices.getLong(step.pattern()));
                stepTag.setLong("crafts", step.crafts());
                steps.appendTag(stepTag);
            }
            batchTag.setTag("steps", steps);
            batches.appendTag(batchTag);
        }
        var definitions = new NBTTagList();
        for (var pattern : patterns) {
            var item = new NBTTagCompound();
            AEItemStack.create(pattern.getPattern())
                .writeToNBT(item);
            definitions.appendTag(item);
        }
        result.setTag("patterns", definitions);
        result.setTag("batches", batches);
        return result;
    }

    public void readFromNBT(NBTTagCompound data, Map<ICraftingPatternDetails, Long> tasks) {
        clear();
        if (!data.hasKey("batches", Constants.NBT.TAG_LIST)) return;
        var byItem = new Object2ObjectOpenHashMap<IAEItemStack, ICraftingPatternDetails>();
        tasks.forEach(
            (pattern, count) -> { if (count > 0) byItem.put(AEItemStack.create(pattern.getPattern()), pattern); });
        var definitions = data.getTagList("patterns", Constants.NBT.TAG_COMPOUND);
        var patterns = new ArrayList<ICraftingPatternDetails>();
        for (int i = 0; i < definitions.tagCount(); i++) {
            var item = AEItemStack.loadItemStackFromNBT(definitions.getCompoundTagAt(i));
            var pattern = item == null ? null : byItem.get(item);
            if (pattern == null) throw new IllegalArgumentException("Dispatch schedule refers to an unknown task");
            patterns.add(pattern);
        }
        var batches = new ArrayList<CraftingDispatchOrder.Batch<ICraftingPatternDetails>>();
        var saved = data.getTagList("batches", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < saved.tagCount(); i++) {
            var tag = saved.getCompoundTagAt(i);
            if (!tag.hasKey("repetitions", Constants.NBT.TAG_LONG) || !tag.hasKey("ordered", Constants.NBT.TAG_BYTE)
                || !tag.hasKey("steps", Constants.NBT.TAG_LIST))
                throw new IllegalArgumentException("Incomplete dispatch batch");
            var steps = new ArrayList<CraftingDispatchOrder.Step<ICraftingPatternDetails>>();
            var entries = tag.getTagList("steps", Constants.NBT.TAG_COMPOUND);
            for (int j = 0; j < entries.tagCount(); j++) {
                var entry = entries.getCompoundTagAt(j);
                int index = entry.getInteger("pattern");
                if (!entry.hasKey("pattern", Constants.NBT.TAG_INT) || !entry.hasKey("crafts", Constants.NBT.TAG_LONG)
                    || index < 0
                    || index >= patterns.size()) throw new IllegalArgumentException("Invalid dispatch step");
                steps.add(new CraftingDispatchOrder.Step<>(patterns.get(index), entry.getLong("crafts")));
            }
            batches
                .add(new CraftingDispatchOrder.Batch<>(steps, tag.getLong("repetitions"), tag.getBoolean("ordered")));
        }
        validateCounts(batches, tasks);
        this.active = new CraftingDispatchOrder<>(batches);
    }

    private static void validateCounts(List<CraftingDispatchOrder.Batch<ICraftingPatternDetails>> batches,
        Map<ICraftingPatternDetails, Long> tasks) {
        var counts = new Object2LongOpenHashMap<ICraftingPatternDetails>();
        for (var batch : batches) {
            for (var step : batch.steps()) {
                counts.put(
                    step.pattern(),
                    Math.addExact(
                        counts.getLong(step.pattern()),
                        Math.multiplyExact(step.crafts(), batch.repetitions())));
            }
        }
        tasks.forEach((pattern, remaining) -> {
            if (counts.removeLong(pattern) != remaining)
                throw new IllegalArgumentException("Dispatch schedule does not match remaining tasks");
        });
        if (!counts.isEmpty()) throw new IllegalArgumentException("Dispatch schedule contains additional tasks");
    }
}
