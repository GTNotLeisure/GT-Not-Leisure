package com.science.gtnl.utils.recipes;

import java.util.Arrays;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizon.gtnhlib.util.data.ItemId;
import com.science.gtnl.ScienceNotLeisure;

import gregtech.api.objects.OreDictItemStack;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class RecipeInputConflictResolver {

    public static final int MAX_CIRCUIT = 24;

    private static final int WILDCARD = OreDictionary.WILDCARD_VALUE;
    private static final long FLUID_FLAG = 1L << 62;

    private final String name;
    private final boolean nbtSensitive;

    private final Object2IntOpenHashMap<Object> slotIds = new Object2IntOpenHashMap<>();
    private final ObjectArrayList<Slot> slots = new ObjectArrayList<>();
    private final Long2IntOpenHashMap itemKeys = new Long2IntOpenHashMap();
    private final Object2IntOpenHashMap<NBTTagCompound> nbtIds = new Object2IntOpenHashMap<>();
    private final Long2ByteOpenHashMap overlapCache = new Long2ByteOpenHashMap();
    private final ObjectArrayList<Entry> entries = new ObjectArrayList<>();

    public RecipeInputConflictResolver(String name, boolean nbtSensitive) {
        this.name = name;
        this.nbtSensitive = nbtSensitive;
        slotIds.defaultReturnValue(-1);
        itemKeys.defaultReturnValue(-1);
    }

    public EntryBuilder recipe() {
        return new EntryBuilder();
    }

    public int add(Object[] inputs, ItemStack... outputs) {
        return recipe().items(inputs)
            .outputs(outputs)
            .add();
    }

    public int add(GTRecipe recipe) {
        return recipe(recipe).add();
    }

    public int add(GTRecipe.RecipeAssemblyLine recipe) {
        return recipe(recipe).add();
    }

    public EntryBuilder recipe(GTRecipe recipe) {
        return recipe().items((Object[]) recipe.mInputs)
            .fluids(recipe.mFluidInputs)
            .outputs(recipe.mOutputs)
            .outputFluids(recipe.mFluidOutputs);
    }

    public EntryBuilder recipe(GTRecipe.RecipeAssemblyLine recipe) {
        EntryBuilder builder = recipe();
        if (recipe.mInputs != null) {
            for (int i = 0; i < recipe.mInputs.length; i++) {
                ItemStack[] alternatives = recipe.mOreDictAlt != null && i < recipe.mOreDictAlt.length
                    ? recipe.mOreDictAlt[i]
                    : null;
                builder.alternatives(recipe.mInputs[i], alternatives);
            }
        }
        return builder.fluids(recipe.mFluidInputs)
            .outputs(recipe.mOutput);
    }

    public int size() {
        return entries.size();
    }

    public Result resolve() {
        int size = entries.size();
        int[] circuits = new int[size];
        int[] duplicateOf = markDuplicates();
        Int2ObjectOpenHashMap<IntArrayList> itemIndex = buildItemIndex(duplicateOf);

        boolean[] needsCircuit = new boolean[size];
        IntArrayList edgeFrom = new IntArrayList();
        IntArrayList edgeTo = new IntArrayList();
        int[] visited = new int[size];

        for (int a = 0; a < size; a++) {
            if (duplicateOf[a] >= 0) continue;
            Entry ea = entries.get(a);
            IntOpenHashSet candidateItems = pickCandidateItems(ea, itemIndex);
            if (candidateItems == null) continue;

            int stamp = a + 1;
            visited[a] = stamp;
            var it = candidateItems.iterator();
            while (it.hasNext()) {
                IntArrayList list = itemIndex.get(it.nextInt());
                if (list == null) continue;
                for (int i = 0, n = list.size(); i < n; i++) {
                    int b = list.getInt(i);
                    if (visited[b] == stamp) continue;
                    visited[b] = stamp;
                    if (!isCoveredBy(ea, entries.get(b))) continue;

                    if (ea.fixedCircuit == 0) needsCircuit[a] = true;
                    edgeFrom.add(a);
                    edgeTo.add(b);
                }
            }
        }

        int[] stats = assignCircuits(circuits, needsCircuit, edgeFrom, edgeTo);

        int duplicates = 0;
        for (int i = 0; i < size; i++) {
            if (duplicateOf[i] >= 0) {
                circuits[i] = circuits[duplicateOf[i]];
                duplicates++;
            }
        }
        // Circuits already present in a recipe's own inputs are not appended again.
        for (int i = 0; i < size; i++) {
            Entry e = entries.get(i);
            if (e.fixedCircuit > 0 && !e.presetCircuit) circuits[i] = 0;
        }

        ScienceNotLeisure.LOG.info(
            "[{}] {} recipes checked, {} duplicates, {} conflicts, {} recipes received circuits (max {}), {} unresolved",
            name,
            size,
            duplicates,
            edgeFrom.size(),
            stats[0],
            stats[1],
            stats[2]);
        return new Result(circuits, duplicateOf);
    }

    private int[] markDuplicates() {
        int[] duplicateOf = new int[entries.size()];
        Arrays.fill(duplicateOf, -1);
        Object2IntOpenHashMap<DuplicateKey> seen = new Object2IntOpenHashMap<>(entries.size());
        seen.defaultReturnValue(-1);
        for (int i = 0, n = entries.size(); i < n; i++) {
            Entry e = entries.get(i);
            if (e.invalid) continue;
            int first = seen.putIfAbsent(new DuplicateKey(e), i);
            if (first >= 0) duplicateOf[i] = first;
        }
        return duplicateOf;
    }

    private Int2ObjectOpenHashMap<IntArrayList> buildItemIndex(int[] duplicateOf) {
        Int2ObjectOpenHashMap<IntArrayList> index = new Int2ObjectOpenHashMap<>();
        IntOpenHashSet items = new IntOpenHashSet();
        for (int i = 0, n = entries.size(); i < n; i++) {
            Entry e = entries.get(i);
            if (duplicateOf[i] >= 0 || e.invalid) continue;
            items.clear();
            for (int slot : e.slots) items.addAll(slots.get(slot).itemKeys);
            var it = items.iterator();
            while (it.hasNext()) {
                index.computeIfAbsent(it.nextInt(), k -> new IntArrayList())
                    .add(i);
            }
        }
        return index;
    }

    // A recipe covering A must share an item with each of A's slots, so scanning the recipes that touch A's most
    // selective slot is enough.
    private IntOpenHashSet pickCandidateItems(Entry e, Int2ObjectOpenHashMap<IntArrayList> itemIndex) {
        if (e.invalid || e.slots.length == 0) return null;
        IntOpenHashSet best = null;
        long bestCost = Long.MAX_VALUE;
        for (int slot : e.slots) {
            IntOpenHashSet items = slots.get(slot).itemKeys;
            long cost = 0;
            var it = items.iterator();
            while (it.hasNext() && cost < bestCost) {
                IntArrayList list = itemIndex.get(it.nextInt());
                if (list != null) cost += list.size();
            }
            if (cost < bestCost) {
                bestCost = cost;
                best = items;
            }
        }
        return best;
    }

    // Whether the inputs supplied for B can also start A. Supplies are not split between A's slots, so this may
    // report a conflict a strict assignment would rule out, but it never misses one.
    private boolean isCoveredBy(Entry a, Entry b) {
        if (b.invalid || a.total > b.total) return false;
        if ((a.requiredSignature & ~b.signature) != 0) return false;

        int[] bSlots = b.slots;
        int[] bAmounts = b.amounts;
        for (int i = 0; i < a.slots.length; i++) {
            int slot = a.slots[i];
            long need = a.amounts[i];
            long supply = 0;
            for (int j = 0; j < bSlots.length && supply < need; j++) {
                if (overlaps(slot, bSlots[j])) supply += bAmounts[j];
            }
            if (supply < need) return false;
        }
        return true;
    }

    private boolean overlaps(int s, int t) {
        if (s == t) return true;
        long key = s < t ? ((long) s << 32) | t : ((long) t << 32) | s;
        byte cached = overlapCache.get(key);
        if (cached != 0) return cached > 0;

        Slot a = slots.get(s), b = slots.get(t);
        if (a.atoms.size() > b.atoms.size()) {
            Slot tmp = a;
            a = b;
            b = tmp;
        }
        boolean result = false;
        var it = a.atoms.iterator();
        while (it.hasNext() && !result) {
            long atom = it.nextLong();
            int itemKey = (int) (atom >>> 32);
            if ((int) atom == WILDCARD) {
                result = b.itemKeys.contains(itemKey);
            } else {
                result = b.atoms.contains(atom) || b.wildcardItems.contains(itemKey);
            }
        }
        overlapCache.put(key, (byte) (result ? 1 : -1));
        return result;
    }

    // Every covered recipe A needs a circuit different from the one on the recipe covering it, so circuit-bearing
    // recipes linked by a conflict must differ: a greedy colouring, highest degree first.
    private int[] assignCircuits(int[] circuits, boolean[] needsCircuit, IntArrayList edgeFrom, IntArrayList edgeTo) {
        int size = needsCircuit.length;
        boolean[] bearsCircuit = new boolean[size];
        for (int i = 0; i < size; i++) {
            int fixed = entries.get(i).fixedCircuit;
            circuits[i] = fixed;
            bearsCircuit[i] = fixed > 0 || needsCircuit[i];
        }

        Int2ObjectOpenHashMap<IntOpenHashSet> adjacency = new Int2ObjectOpenHashMap<>();
        int fixedClashes = 0;
        for (int i = 0, n = edgeFrom.size(); i < n; i++) {
            int a = edgeFrom.getInt(i), b = edgeTo.getInt(i);
            if (!bearsCircuit[b]) continue;
            if (!needsCircuit[a] && !needsCircuit[b]) {
                if (circuits[a] == circuits[b]) {
                    fixedClashes++;
                    warnUnresolved(a);
                }
                continue;
            }
            adjacency.computeIfAbsent(a, k -> new IntOpenHashSet())
                .add(b);
            adjacency.computeIfAbsent(b, k -> new IntOpenHashSet())
                .add(a);
        }

        IntArrayList nodes = new IntArrayList();
        for (int i = 0; i < size; i++) if (needsCircuit[i]) nodes.add(i);
        nodes.sort((x, y) -> {
            int cmp = Integer.compare(degree(adjacency, y), degree(adjacency, x));
            return cmp != 0 ? cmp : Integer.compare(x, y);
        });

        int assigned = 0, maxCircuit = 0, unresolved = fixedClashes;
        for (int i = 0, n = nodes.size(); i < n; i++) {
            int node = nodes.getInt(i);
            int used = 1;
            IntOpenHashSet neighbours = adjacency.get(node);
            if (neighbours != null) {
                var it = neighbours.iterator();
                while (it.hasNext()) {
                    int c = circuits[it.nextInt()];
                    if (c > 0 && c < 32) used |= 1 << c;
                }
            }
            int circuit = Integer.numberOfTrailingZeros(~used);
            if (circuit > MAX_CIRCUIT) {
                unresolved++;
                warnUnresolved(node);
                continue;
            }
            circuits[node] = circuit;
            assigned++;
            maxCircuit = Math.max(maxCircuit, circuit);
        }
        return new int[] { assigned, maxCircuit, unresolved };
    }

    private static int degree(Int2ObjectOpenHashMap<IntOpenHashSet> adjacency, int node) {
        IntOpenHashSet set = adjacency.get(node);
        return set == null ? 0 : set.size();
    }

    private void warnUnresolved(int index) {
        Entry e = entries.get(index);
        ScienceNotLeisure.LOG.warn("[{}] No free circuit for recipe #{} producing {}", name, index, e.description);
    }

    private int internItemKey(long raw) {
        int key = itemKeys.get(raw);
        if (key >= 0) return key;
        key = itemKeys.size();
        itemKeys.put(raw, key);
        return key;
    }

    private long atom(ItemStack stack) {
        long nbt = 0;
        NBTTagCompound tag = stack.getTagCompound();
        if (nbtSensitive && tag != null && !tag.hasNoTags()) {
            int id = nbtIds.getInt(tag);
            if (id == 0) {
                id = nbtIds.size() + 1;
                nbtIds.put(tag, id);
            }
            nbt = id;
        }
        int itemKey = internItemKey(((long) Item.getIdFromItem(stack.getItem()) << 32) | nbt);
        return ((long) itemKey << 32) | (stack.getItemDamage() & 0xFFFFFFFFL);
    }

    private long atom(FluidStack fluid) {
        return (long) internItemKey(FLUID_FLAG | fluid.getFluidID()) << 32;
    }

    private int internSlot(Object key, long[] atoms) {
        int id = slotIds.getInt(key);
        if (id >= 0) return id;
        Slot slot = new Slot();
        for (long atom : atoms) slot.addAtom(atom);
        if (slot.atoms.isEmpty()) return -1;
        id = slots.size();
        slots.add(slot);
        slotIds.put(key, id);
        return id;
    }

    private int internOreSlot(String oreName) {
        int id = slotIds.getInt(oreName);
        if (id >= 0) return id;
        List<ItemStack> ores = GTOreDictUnificator.getOres(oreName);
        return internSlot(oreName, atomsOf(ores.toArray(new ItemStack[0])));
    }

    private long[] atomsOf(ItemStack[] stacks) {
        long[] atoms = new long[stacks.length];
        int n = 0;
        for (ItemStack stack : stacks) {
            if (stack != null && stack.getItem() != null) atoms[n++] = atom(stack);
        }
        atoms = Arrays.copyOf(atoms, n);
        Arrays.sort(atoms);
        return atoms;
    }

    private static boolean isCircuit(ItemStack stack) {
        return stack != null && stack.getItem() != null && GTUtility.isAnyIntegratedCircuit(stack);
    }

    public class EntryBuilder {

        // slot << 32 | amount, sorted later so equal slots end up adjacent
        private final LongArrayList packed = new LongArrayList();
        private final ObjectArrayList<Object> outputKey = new ObjectArrayList<>();
        private int fixedCircuit;
        private boolean presetCircuit;
        private boolean invalid;
        private String description;

        /**
         * One input slot: {@link ItemStack}, {@code ItemStack[]} alternatives, {@link OreDictItemStack},
         * {@code Object[]{oreName, amount}} or {@link FluidStack}. A programmed circuit is taken as the recipe's own
         * fixed circuit.
         */
        public EntryBuilder item(Object input) {
            if (input == null) return this;
            if (input instanceof FluidStack fluid) return fluid(fluid);
            if (input instanceof ItemStack stack) {
                if (stack.getItem() == null) return markInvalid();
                if (isCircuit(stack)) {
                    fixedCircuit = stack.getItemDamage();
                    presetCircuit = false;
                    return this;
                }
                long atom = atom(stack);
                return addSlot(internSlot(atom, new long[] { atom }), stack.stackSize);
            }
            if (input instanceof OreDictItemStack ore) return addSlot(internOreSlot(ore.mOreName), ore.mAmount);
            if (input instanceof Object[]arr && arr.length == 2 && arr[0] != null && arr[1] instanceof Number n) {
                return addSlot(internOreSlot(arr[0].toString()), n.intValue());
            }
            if (input instanceof ItemStack[]arr) return alternatives(null, arr);
            return markInvalid();
        }

        public EntryBuilder items(Object... inputs) {
            if (inputs != null) for (Object input : inputs) item(input);
            return this;
        }

        /**
         * One slot accepting {@code main} or any of {@code alternatives}; the amount comes from {@code main}, or the
         * first alternative when {@code main} is null.
         */
        public EntryBuilder alternatives(ItemStack main, ItemStack[] alternatives) {
            if (alternatives == null || alternatives.length == 0) return item(main);
            if (isCircuit(main)) return item(main);

            ItemStack[] all;
            if (main == null) {
                all = alternatives;
            } else {
                all = Arrays.copyOf(alternatives, alternatives.length + 1);
                all[alternatives.length] = main;
            }
            long[] atoms = atomsOf(all);
            if (atoms.length == 0) return markInvalid();

            ItemStack amountSource = main;
            for (int i = 0; amountSource == null && i < alternatives.length; i++) amountSource = alternatives[i];
            return addSlot(
                internSlot(new AtomArrayKey(atoms), atoms),
                amountSource == null ? 1 : amountSource.stackSize);
        }

        public EntryBuilder fluid(FluidStack fluid) {
            if (fluid == null) return this;
            if (fluid.getFluid() == null) return markInvalid();
            long atom = atom(fluid);
            return addSlot(internSlot(atom, new long[] { atom }), fluid.amount);
        }

        public EntryBuilder fluids(FluidStack... fluids) {
            if (fluids != null) for (FluidStack fluid : fluids) fluid(fluid);
            return this;
        }

        public EntryBuilder outputs(ItemStack... outputs) {
            if (outputs == null) return this;
            for (ItemStack output : outputs) {
                if (output == null || output.getItem() == null) continue;
                outputKey.add(new OutputKey(ItemId.create(output), output.stackSize));
                if (description == null) description = output.getDisplayName();
            }
            return this;
        }

        public EntryBuilder outputFluids(FluidStack... outputs) {
            if (outputs == null) return this;
            for (FluidStack output : outputs) {
                if (output == null || output.getFluid() == null) continue;
                outputKey.add(new OutputKey(output.getFluid(), output.amount));
                if (description == null) description = output.getLocalizedName();
            }
            return this;
        }

        /**
         * A circuit the caller will append itself. It is kept as is and other recipes are separated from it.
         */
        public EntryBuilder presetCircuit(int circuit) {
            if (circuit <= 0) return this;
            fixedCircuit = circuit;
            presetCircuit = true;
            return this;
        }

        public int add() {
            long[] sorted = packed.toLongArray();
            Arrays.sort(sorted);

            int[] slotArray = new int[sorted.length], amountArray = new int[sorted.length];
            int groups = 0;
            long total = 0, signature = 0, required = 0;
            for (long value : sorted) {
                int slotId = (int) (value >>> 32), amount = (int) value;
                total += amount;
                if (groups > 0 && slotArray[groups - 1] == slotId) {
                    amountArray[groups - 1] += amount;
                    continue;
                }
                slotArray[groups] = slotId;
                amountArray[groups++] = amount;
                Slot slot = slots.get(slotId);
                signature |= slot.signature;
                if (slot.itemKeys.size() == 1) required |= slot.signature;
            }

            int index = entries.size();
            entries.add(
                new Entry(
                    Arrays.copyOf(slotArray, groups),
                    Arrays.copyOf(amountArray, groups),
                    total,
                    signature,
                    required,
                    outputKey,
                    fixedCircuit,
                    presetCircuit,
                    invalid,
                    description));
            return index;
        }

        private EntryBuilder addSlot(int slot, int amount) {
            if (slot < 0) return markInvalid();
            // Non-consumed inputs still have to be present.
            packed.add(((long) slot << 32) | Math.max(1, amount));
            return this;
        }

        private EntryBuilder markInvalid() {
            invalid = true;
            return this;
        }
    }

    /**
     * @param circuits    the circuit to append to each recipe, 0 for none
     * @param duplicateOf index of the first identical recipe, or -1
     */
    public record Result(int[] circuits, int[] duplicateOf) {

        public int circuit(int index) {
            return circuits[index];
        }

        public boolean isDuplicate(int index) {
            return duplicateOf[index] >= 0;
        }
    }

    private static class Slot {

        final LongOpenHashSet atoms = new LongOpenHashSet(2);
        final IntOpenHashSet itemKeys = new IntOpenHashSet(2);
        final IntOpenHashSet wildcardItems = new IntOpenHashSet(1);
        long signature;

        void addAtom(long atom) {
            if (!atoms.add(atom)) return;
            int itemKey = (int) (atom >>> 32);
            itemKeys.add(itemKey);
            if ((int) atom == WILDCARD) wildcardItems.add(itemKey);
            signature |= 1L << ((itemKey * 0x9E3779B9) >>> 26);
        }
    }

    private record Entry(int[] slots, int[] amounts, long total, long signature, long requiredSignature,
        List<Object> outputKey, int fixedCircuit, boolean presetCircuit, boolean invalid, String description) {}

    private record OutputKey(Object id, int amount) {}

    private record AtomArrayKey(long[] atoms) {

        @Override
        public boolean equals(Object o) {
            return o instanceof AtomArrayKey(long[] atoms1) && Arrays.equals(atoms, atoms1);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(atoms);
        }
    }

    private static class DuplicateKey {

        final Entry entry;
        final int hash;

        DuplicateKey(Entry e) {
            entry = e;
            int h = Arrays.hashCode(e.slots);
            h = 31 * h + Arrays.hashCode(e.amounts);
            h = 31 * h + e.outputKey.hashCode();
            hash = 31 * h + e.fixedCircuit;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof DuplicateKey k) || hash != k.hash) return false;
            Entry a = entry, b = k.entry;
            return a.fixedCircuit == b.fixedCircuit && Arrays.equals(a.slots, b.slots)
                && Arrays.equals(a.amounts, b.amounts)
                && a.outputKey.equals(b.outputKey);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
