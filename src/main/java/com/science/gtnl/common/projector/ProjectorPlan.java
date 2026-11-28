package com.science.gtnl.common.projector;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.science.gtnl.common.projector.ProjectorStructure.Choice;
import com.science.gtnl.common.structureanimation.StructureAnimationStyle;

public class ProjectorPlan {

    public static final String TAG = "Plan";
    public static final int MAX_MASTER = 64, MAX_CHANNELS = 32, MAX_CHANNEL_NAME = 64, MAX_CHANNEL_VALUE = 1024,
        MAX_OVERRIDES = 1024, MAX_HATCH_TIER = 14, DEFAULT_HATCH_TIER = 1, MAX_OFFSET = 64, MAX_CHOICES = 256,
        MAX_CHOICE_KEY = 256, MAX_STACK = 16, MAX_STACK_GAP = 64, MAX_COPIES = 64;
    public static final int CASING = 0;
    public static final String AIR = "air";

    public enum State {
        IDLE,
        FOLLOW,
        LOCKED,
        ANCHORED
    }

    public enum Display {
        BOX,
        DIFFERENCE,
        BLOCKS
    }

    public record Settings(int machine, int master, Map<String, Integer> channels, Map<Long, Integer> overrides,
        Map<String, String> choices, Map<Long, String> cellChoices) {}

    public int machine;
    public int master = 1;
    public final TreeMap<String, Integer> channels = new TreeMap<>();
    public int hatchTier = DEFAULT_HATCH_TIER;
    public final TreeMap<Long, Integer> overrides = new TreeMap<>();
    public final TreeMap<String, String> choices = new TreeMap<>();
    public final TreeMap<Long, String> cellChoices = new TreeMap<>();
    public ExtendedFacing facing = ExtendedFacing.DEFAULT;
    public State state = State.IDLE;
    public int dimension, x, y, z;
    public boolean groundSnap = true, centerPivot;
    public int verticalOffset;
    public Display display = Display.DIFFERENCE;
    public StructureAnimationStyle buildStyle = StructureAnimationStyle.REVEAL;
    // Copies along world X, Y and Z; a negative count stacks towards the negative side, and -1 means the same as 1.
    public final int[] stackCount = { 1, 1, 1 };
    public final int[] stackGap = new int[3];
    private Settings settings;

    public boolean hasMachine() {
        return machine > 0;
    }

    public boolean isPlaced() {
        return state == State.LOCKED || state == State.ANCHORED;
    }

    public Settings settings() {
        Settings last = settings;
        if (last != null && last.machine() == machine
            && last.master() == master
            && last.channels()
                .equals(channels)
            && last.overrides()
                .equals(overrides)
            && last.choices()
                .equals(choices)
            && last.cellChoices()
                .equals(cellChoices))
            return last;
        return settings = new Settings(
            machine,
            master,
            Collections.unmodifiableMap(new TreeMap<>(channels)),
            Collections.unmodifiableMap(new TreeMap<>(overrides)),
            Collections.unmodifiableMap(new TreeMap<>(choices)),
            Collections.unmodifiableMap(new TreeMap<>(cellChoices)));
    }

    public void selectMachine(int id) {
        if (machine == id) return;
        machine = id;
        master = 1;
        channels.clear();
        overrides.clear();
        choices.clear();
        cellChoices.clear();
        clearStack();
    }

    public static String blockKey(ItemStack stack) {
        if (stack == null) return AIR;
        Object name = Item.itemRegistry.getNameForObject(stack.getItem());
        return name + "@" + stack.getItemDamage();
    }

    public String chosenBlock(Choice choice) {
        return choices.getOrDefault(choice.key(), blockKey(choice.base()));
    }

    public void chooseBlock(Choice choice, String blockKey) {
        if (blockKey.equals(blockKey(choice.base()))) choices.remove(choice.key());
        else choices.put(choice.key(), blockKey);
    }

    public void setChannel(String channel, int value) {
        if (value == 0) channels.remove(channel);
        else channels.put(channel, value);
    }

    public static int copiesOf(int count) {
        return Math.max(1, Math.abs(count));
    }

    public static int copies(int[] counts) {
        return copiesOf(counts[0]) * copiesOf(counts[1]) * copiesOf(counts[2]);
    }

    public int copies() {
        return copies(stackCount);
    }

    public boolean isStacked() {
        return copies() > 1;
    }

    public boolean setStack(int axis, int count, int gap) {
        int[] counts = stackCount.clone();
        counts[axis] = clamp(count, -MAX_STACK, MAX_STACK);
        if (Math.abs(counts[axis]) <= 1) counts[axis] = 1;
        if (copies(counts) > MAX_COPIES) return false;
        stackCount[axis] = counts[axis];
        stackGap[axis] = clamp(gap, 0, MAX_STACK_GAP);
        return true;
    }

    public void clearStack() {
        for (int axis = 0; axis < 3; axis++) {
            stackCount[axis] = 1;
            stackGap[axis] = 0;
        }
    }

    // World offsets of every copy, three per copy, the first copy at no offset. The bounds are those of one copy.
    public int[] stackOffsets(int[] bounds) {
        int nx = copiesOf(stackCount[0]), ny = copiesOf(stackCount[1]), nz = copiesOf(stackCount[2]);
        int[] step = new int[3];
        for (int axis = 0; axis < 3; axis++)
            step[axis] = Integer.signum(stackCount[axis]) * (bounds[axis + 3] - bounds[axis] + 1 + stackGap[axis]);
        int[] offsets = new int[nx * ny * nz * 3];
        int i = 0;
        for (int iy = 0; iy < ny; iy++) {
            for (int iz = 0; iz < nz; iz++) {
                for (int ix = 0; ix < nx; ix++) {
                    offsets[i++] = ix * step[0];
                    offsets[i++] = iy * step[1];
                    offsets[i++] = iz * step[2];
                }
            }
        }
        return offsets;
    }

    public static int[] stackedBounds(int[] bounds, int[] offsets) {
        int[] all = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
            Integer.MIN_VALUE };
        for (int i = 0; i < offsets.length; i += 3) {
            for (int axis = 0; axis < 3; axis++) {
                all[axis] = Math.min(all[axis], bounds[axis] + offsets[i + axis]);
                all[axis + 3] = Math.max(all[axis + 3], bounds[axis + 3] + offsets[i + axis]);
            }
        }
        return all;
    }

    public void clear() {
        selectMachine(0);
        state = State.IDLE;
    }

    public ProjectorPlan copy() {
        ProjectorPlan copy = new ProjectorPlan();
        copy.readFromNBT(writeToNBT());
        return copy;
    }

    public static long pack(int a, int b, int c) {
        return ((long) (a & 0x1FFFFF) << 42) | ((long) (b & 0x1FFFFF) << 21) | (c & 0x1FFFFF);
    }

    public static int unpackA(long packed) {
        return (int) (packed << 1 >> 43);
    }

    public static int unpackB(long packed) {
        return (int) (packed << 22 >> 43);
    }

    public static int unpackC(long packed) {
        return (int) (packed << 43 >> 43);
    }

    public static ProjectorPlan read(ItemStack stack) {
        ProjectorPlan plan = new ProjectorPlan();
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        if (tag != null && tag.hasKey(TAG, Constants.NBT.TAG_COMPOUND)) plan.readFromNBT(tag.getCompoundTag(TAG));
        return plan;
    }

    public void write(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setTag(TAG, writeToNBT());
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Machine", machine);
        tag.setInteger("Master", master);
        NBTTagCompound channelTag = new NBTTagCompound();
        for (Map.Entry<String, Integer> entry : channels.entrySet())
            channelTag.setInteger(entry.getKey(), entry.getValue());
        tag.setTag("Channels", channelTag);
        tag.setInteger("HatchTier", hatchTier);
        NBTTagList overrideList = new NBTTagList();
        for (Map.Entry<Long, Integer> entry : overrides.entrySet()) {
            if (overrideList.tagCount() >= MAX_OVERRIDES) break;
            NBTTagCompound overrideTag = new NBTTagCompound();
            overrideTag.setLong("Pos", entry.getKey());
            overrideTag.setInteger("Id", entry.getValue());
            overrideList.appendTag(overrideTag);
        }
        tag.setTag("Overrides", overrideList);
        NBTTagList choiceList = new NBTTagList();
        for (Map.Entry<String, String> entry : choices.entrySet()) {
            if (choiceList.tagCount() >= MAX_CHOICES) break;
            NBTTagCompound choiceTag = new NBTTagCompound();
            choiceTag.setString("Group", entry.getKey());
            choiceTag.setString("Block", entry.getValue());
            choiceList.appendTag(choiceTag);
        }
        tag.setTag("Choices", choiceList);
        NBTTagList cellChoiceList = new NBTTagList();
        for (Map.Entry<Long, String> entry : cellChoices.entrySet()) {
            if (cellChoiceList.tagCount() >= MAX_OVERRIDES) break;
            NBTTagCompound cellTag = new NBTTagCompound();
            cellTag.setLong("Pos", entry.getKey());
            cellTag.setString("Block", entry.getValue());
            cellChoiceList.appendTag(cellTag);
        }
        tag.setTag("CellChoices", cellChoiceList);
        tag.setInteger("Facing", facing.getIndex());
        tag.setString("State", state.name());
        tag.setInteger("Dim", dimension);
        tag.setInteger("X", x);
        tag.setInteger("Y", y);
        tag.setInteger("Z", z);
        tag.setBoolean("Ground", groundSnap);
        tag.setBoolean("Center", centerPivot);
        tag.setInteger("Offset", verticalOffset);
        tag.setString("Display", display.name());
        tag.setString("BuildStyle", buildStyle.name());
        tag.setIntArray("StackCount", stackCount.clone());
        tag.setIntArray("StackGap", stackGap.clone());
        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        machine = Math.max(0, tag.getInteger("Machine"));
        master = clamp(tag.getInteger("Master"), 1, MAX_MASTER);
        channels.clear();
        NBTTagCompound channelTag = tag.getCompoundTag("Channels");
        for (Object key : channelTag.func_150296_c()) {
            String name = (String) key;
            if (channels.size() >= MAX_CHANNELS || name.isEmpty() || name.length() > MAX_CHANNEL_NAME) continue;
            channels.put(name, clamp(channelTag.getInteger(name), 0, MAX_CHANNEL_VALUE));
        }
        hatchTier = tag.hasKey("HatchTier") ? clamp(tag.getInteger("HatchTier"), 0, MAX_HATCH_TIER)
            : DEFAULT_HATCH_TIER;
        overrides.clear();
        NBTTagList overrideList = tag.getTagList("Overrides", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < overrideList.tagCount() && overrides.size() < MAX_OVERRIDES; i++) {
            NBTTagCompound overrideTag = overrideList.getCompoundTagAt(i);
            overrides.put(overrideTag.getLong("Pos"), Math.max(CASING, overrideTag.getInteger("Id")));
        }
        choices.clear();
        NBTTagList choiceList = tag.getTagList("Choices", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < choiceList.tagCount() && choices.size() < MAX_CHOICES; i++) {
            NBTTagCompound choiceTag = choiceList.getCompoundTagAt(i);
            String group = choiceTag.getString("Group"), block = choiceTag.getString("Block");
            if (group.isEmpty() || block.isEmpty()
                || group.length() > MAX_CHOICE_KEY
                || block.length() > MAX_CHOICE_KEY) continue;
            choices.put(group, block);
        }
        cellChoices.clear();
        NBTTagList cellChoiceList = tag.getTagList("CellChoices", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < cellChoiceList.tagCount() && cellChoices.size() < MAX_OVERRIDES; i++) {
            NBTTagCompound cellTag = cellChoiceList.getCompoundTagAt(i);
            String block = cellTag.getString("Block");
            if (block.isEmpty() || block.length() > MAX_CHOICE_KEY) continue;
            cellChoices.put(cellTag.getLong("Pos"), block);
        }
        facing = ExtendedFacing.byIndex(tag.getInteger("Facing"));
        state = parse(State.class, tag.getString("State"), State.IDLE);
        dimension = tag.getInteger("Dim");
        x = tag.getInteger("X");
        y = clamp(tag.getInteger("Y"), 0, 255);
        z = tag.getInteger("Z");
        groundSnap = !tag.hasKey("Ground") || tag.getBoolean("Ground");
        centerPivot = tag.getBoolean("Center");
        verticalOffset = clamp(tag.getInteger("Offset"), -MAX_OFFSET, MAX_OFFSET);
        display = parse(Display.class, tag.getString("Display"), Display.DIFFERENCE);
        buildStyle = parse(StructureAnimationStyle.class, tag.getString("BuildStyle"), StructureAnimationStyle.REVEAL);
        clearStack();
        int[] counts = tag.getIntArray("StackCount"), gaps = tag.getIntArray("StackGap");
        for (int axis = 0; axis < 3; axis++) {
            if (!setStack(axis, axis < counts.length ? counts[axis] : 1, axis < gaps.length ? gaps[axis] : 0)) {
                clearStack();
                break;
            }
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        for (E value : type.getEnumConstants()) {
            if (value.name()
                .equals(name)) return value;
        }
        return fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
