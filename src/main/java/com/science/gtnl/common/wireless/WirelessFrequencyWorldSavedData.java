package com.science.gtnl.common.wireless;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.DimensionManager;

import com.science.gtnl.common.wireless.WirelessChannelManager.Address;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public class WirelessFrequencyWorldSavedData extends WorldSavedData {

    public static final String DATA_NAME = "GTNL_WirelessFrequencies";
    private static WirelessFrequencyWorldSavedData instance;

    private final Map<UUID, Frequency> frequencies = new Object2ObjectOpenHashMap<>();
    private final Map<Address, UUID> legacy = new Object2ObjectOpenHashMap<>();
    private final Map<UUID, Map<Address, GroupName>> groupNames = new Object2ObjectOpenHashMap<>();

    public record Frequency(UUID creator, long epoch) {}

    public record GroupName(String name, int meta) {}

    public WirelessFrequencyWorldSavedData() {
        super(DATA_NAME);
    }

    public WirelessFrequencyWorldSavedData(String name) {
        super(name);
    }

    static WirelessFrequencyWorldSavedData get() {
        if (instance != null) return instance;
        World world = DimensionManager.getWorld(0);
        MapStorage storage = world == null ? null : world.mapStorage;
        if (storage == null) return null;
        instance = (WirelessFrequencyWorldSavedData) storage.loadData(WirelessFrequencyWorldSavedData.class, DATA_NAME);
        if (instance == null) {
            instance = new WirelessFrequencyWorldSavedData();
            storage.setData(DATA_NAME, instance);
        }
        return instance;
    }

    static void unload() {
        instance = null;
    }

    Frequency frequency(UUID id) {
        return frequencies.get(id);
    }

    long create(UUID id, UUID creator) {
        frequencies.put(id, new Frequency(creator, 0));
        markDirty();
        return 0;
    }

    boolean accept(UUID id, long epoch) {
        Frequency known = frequencies.get(id);
        if (known != null && epoch <= known.epoch()) return epoch == known.epoch();
        // A copy nobody has recorded yet, or one newer than the record, is the first evidence of this frequency.
        frequencies.put(id, new Frequency(known == null ? null : known.creator(), epoch));
        markDirty();
        return true;
    }

    long adopt(UUID id) {
        Frequency known = frequencies.get(id);
        long epoch = known == null ? 1 : known.epoch() + 1;
        frequencies.put(id, new Frequency(known == null ? null : known.creator(), epoch));
        markDirty();
        return epoch;
    }

    void setCreator(UUID id, UUID creator) {
        Frequency known = frequencies.get(id);
        frequencies.put(id, new Frequency(creator, known == null ? 0 : known.epoch()));
        markDirty();
    }

    UUID legacy(Address position) {
        return legacy.get(position);
    }

    boolean migrated(UUID id) {
        return legacy.containsValue(id);
    }

    void mapLegacy(Address position, UUID id) {
        if (id.equals(legacy.put(position, id))) return;
        markDirty();
    }

    GroupName groupName(UUID frequency, Address machine) {
        var names = groupNames.get(frequency);
        return names == null ? null : names.get(machine);
    }

    void setGroupName(UUID frequency, Address machine, int meta, String name) {
        var names = groupNames.computeIfAbsent(frequency, ignored -> new Object2ObjectOpenHashMap<>());
        GroupName next = name.isEmpty() ? null : new GroupName(name, meta);
        GroupName previous = next == null ? names.remove(machine) : names.put(machine, next);
        if (names.isEmpty()) groupNames.remove(frequency);
        if (!Objects.equals(previous, next)) markDirty();
    }

    void moveGroupNames(Address source, Address target, int meta) {
        var networks = groupNames.values()
            .iterator();
        while (networks.hasNext()) {
            var names = networks.next();
            GroupName moved = names.remove(source);
            GroupName replaced = names.remove(target);
            if (moved == null && replaced == null) continue;
            if (moved != null && moved.meta() == meta) names.put(target, moved);
            if (names.isEmpty()) networks.remove();
            markDirty();
        }
    }

    void forgetGroupNames(UUID frequency, BiPredicate<Address, Integer> removed) {
        var names = groupNames.get(frequency);
        if (names == null || !names.entrySet()
            .removeIf(
                entry -> removed.test(
                    entry.getKey(),
                    entry.getValue()
                        .meta())))
            return;
        if (names.isEmpty()) groupNames.remove(frequency);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        frequencies.clear();
        legacy.clear();
        groupNames.clear();
        NBTTagList named = tag.getTagList("groupNames", 10);
        for (int i = 0; i < named.tagCount(); i++) {
            NBTTagCompound entry = named.getCompoundTagAt(i);
            UUID id = WirelessFrequencies.uuid(entry, "frequency");
            Address position = WirelessLinkData.readPosition(entry);
            String name = WirelessLinkPersistence.cleanName(entry.getString("name"));
            if (id != null && position != null && !name.isEmpty())
                groupNames.computeIfAbsent(id, ignored -> new Object2ObjectOpenHashMap<>())
                    .put(position, new GroupName(name, entry.getInteger("meta")));
        }
        NBTTagList list = tag.getTagList("frequencies", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            UUID id = WirelessFrequencies.uuid(entry, "id");
            if (id != null)
                frequencies.put(id, new Frequency(WirelessFrequencies.uuid(entry, "creator"), entry.getLong("epoch")));
        }
        NBTTagList positions = tag.getTagList("legacy", 10);
        for (int i = 0; i < positions.tagCount(); i++) {
            NBTTagCompound entry = positions.getCompoundTagAt(i);
            Address position = WirelessLinkData.readPosition(entry);
            UUID id = WirelessFrequencies.uuid(entry, "id");
            if (position != null && id != null) legacy.put(position, id);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        frequencies.forEach((id, frequency) -> {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("id", id.toString());
            if (frequency.creator() != null) entry.setString(
                "creator",
                frequency.creator()
                    .toString());
            entry.setLong("epoch", frequency.epoch());
            list.appendTag(entry);
        });
        tag.setTag("frequencies", list);
        NBTTagList positions = new NBTTagList();
        legacy.forEach((position, id) -> {
            NBTTagCompound entry = WirelessLinkData.writePosition(new NBTTagCompound(), position);
            entry.setString("id", id.toString());
            positions.appendTag(entry);
        });
        tag.setTag("legacy", positions);
        NBTTagList named = new NBTTagList();
        groupNames.forEach((id, names) -> names.forEach((position, name) -> {
            NBTTagCompound entry = WirelessLinkData.writePosition(new NBTTagCompound(), position);
            entry.setString("frequency", id.toString());
            entry.setInteger("meta", name.meta());
            entry.setString("name", name.name());
            named.appendTag(entry);
        }));
        tag.setTag("groupNames", named);
    }
}
