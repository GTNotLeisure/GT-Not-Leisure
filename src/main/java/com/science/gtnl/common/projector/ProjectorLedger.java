package com.science.gtnl.common.projector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;

import it.unimi.dsi.fastutil.ints.IntArrayList;

// What each projector build placed, per player and per spot a projection was placed at, so a build can be undone or
// the whole structure taken down without touching blocks the player did not place with it.
public class ProjectorLedger extends WorldSavedData {

    public static final String NAME = "gtnl_projector_ledger";
    public static final int MAX_SITES = 16, MAX_BUILDS = 16, MAX_CELLS = 400_000;

    // One build: for every placed block its position, the item it was placed from, and what it was right after.
    public static class Build {

        public final IntArrayList x = new IntArrayList(), y = new IntArrayList(), z = new IntArrayList(),
            item = new IntArrayList(), damage = new IntArrayList(), block = new IntArrayList(),
            meta = new IntArrayList(), machine = new IntArrayList();

        public int size() {
            return x.size();
        }

        public void add(int px, int py, int pz, int itemId, int itemDamage, int blockId, int blockMeta, int machineId) {
            x.add(px);
            y.add(py);
            z.add(pz);
            item.add(itemId);
            damage.add(itemDamage);
            block.add(blockId);
            meta.add(blockMeta);
            machine.add(machineId);
        }

        public void addFrom(Build other, int i) {
            add(
                other.x.getInt(i),
                other.y.getInt(i),
                other.z.getInt(i),
                other.item.getInt(i),
                other.damage.getInt(i),
                other.block.getInt(i),
                other.meta.getInt(i),
                other.machine.getInt(i));
        }

        public void addAll(Build other) {
            for (int i = 0; i < other.size(); i++) addFrom(other, i);
        }

        public NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setIntArray("X", x.toIntArray());
            tag.setIntArray("Y", y.toIntArray());
            tag.setIntArray("Z", z.toIntArray());
            tag.setIntArray("Item", item.toIntArray());
            tag.setIntArray("Damage", damage.toIntArray());
            tag.setIntArray("Block", block.toIntArray());
            tag.setIntArray("Meta", meta.toIntArray());
            tag.setIntArray("Machine", machine.toIntArray());
            return tag;
        }

        public static Build read(NBTTagCompound tag) {
            Build build = new Build();
            int[] xs = tag.getIntArray("X"), ys = tag.getIntArray("Y"), zs = tag.getIntArray("Z"),
                items = tag.getIntArray("Item"), damages = tag.getIntArray("Damage"), blocks = tag.getIntArray("Block"),
                metas = tag.getIntArray("Meta"), machines = tag.getIntArray("Machine");
            int size = xs.length;
            if (ys.length != size || zs.length != size
                || items.length != size
                || damages.length != size
                || blocks.length != size
                || metas.length != size
                || machines.length != size) return build;
            for (int i = 0; i < size; i++)
                build.add(xs[i], ys[i], zs[i], items[i], damages[i], blocks[i], metas[i], machines[i]);
            return build;
        }
    }

    public record Site(UUID owner, int dimension, int x, int y, int z, int machine) {

        public String key() {
            return owner + "|" + dimension + "|" + x + "|" + y + "|" + z + "|" + machine;
        }

        public static Site of(UUID owner, ProjectorPlan plan) {
            return new Site(owner, plan.dimension, plan.x, plan.y, plan.z, plan.machine);
        }
    }

    private final Map<String, Site> sites = new LinkedHashMap<>();
    private final Map<String, List<Build>> builds = new LinkedHashMap<>();

    public ProjectorLedger(String name) {
        super(name);
    }

    public static ProjectorLedger get() {
        World world = DimensionManager.getWorld(0);
        if (world == null) return null;
        ProjectorLedger ledger = (ProjectorLedger) world.mapStorage.loadData(ProjectorLedger.class, NAME);
        if (ledger == null) {
            ledger = new ProjectorLedger(NAME);
            world.mapStorage.setData(NAME, ledger);
        }
        return ledger;
    }

    // A running build writes straight into the ledger, so the blocks it placed are saved with the world even when the
    // server stops before the build ends.
    public static Build begin(Site site) {
        ProjectorLedger ledger = get();
        Build build = new Build();
        if (ledger == null) return build;
        String key = site.key();
        List<Build> list = ledger.builds.remove(key);
        if (list == null) list = new ArrayList<>();
        list.add(build);
        while (list.size() > MAX_BUILDS) list.remove(0);
        ledger.sites.remove(key);
        ledger.sites.put(key, site);
        ledger.builds.put(key, list);
        ledger.trim(site.owner());
        ledger.markDirty();
        return build;
    }

    public static void end(Site site, Build build) {
        ProjectorLedger ledger = get();
        if (ledger == null) return;
        List<Build> list = ledger.builds.get(site.key());
        if (list != null && build.size() == 0) {
            list.remove(build);
            if (list.isEmpty()) ledger.forget(site);
        }
        ledger.markDirty();
    }

    public static void touch() {
        ProjectorLedger ledger = get();
        if (ledger != null) ledger.markDirty();
    }

    public List<Build> builds(Site site) {
        List<Build> list = builds.get(site.key());
        return list == null ? new ArrayList<>() : list;
    }

    // What an undo could not take down stays as the last build, so it can be undone later.
    public void replaceLast(Site site, Build rest) {
        List<Build> list = builds.get(site.key());
        if (list == null || list.isEmpty()) return;
        if (rest.size() == 0) {
            removeLast(site);
            return;
        }
        list.set(list.size() - 1, rest);
        markDirty();
    }

    public void removeLast(Site site) {
        List<Build> list = builds.get(site.key());
        if (list == null || list.isEmpty()) return;
        list.remove(list.size() - 1);
        if (list.isEmpty()) forget(site);
        markDirty();
    }

    public void forget(Site site) {
        sites.remove(site.key());
        builds.remove(site.key());
        markDirty();
    }

    // Oldest spots go first once a player has too many, or too many recorded blocks in all.
    private void trim(UUID owner) {
        int count = 0;
        long cells = 0;
        for (Map.Entry<String, Site> entry : sites.entrySet()) {
            if (!entry.getValue()
                .owner()
                .equals(owner)) continue;
            count++;
            for (Build build : builds.get(entry.getKey())) cells += build.size();
        }
        Iterator<Map.Entry<String, Site>> iterator = sites.entrySet()
            .iterator();
        while ((count > MAX_SITES || cells > MAX_CELLS) && count > 1 && iterator.hasNext()) {
            Map.Entry<String, Site> entry = iterator.next();
            if (!entry.getValue()
                .owner()
                .equals(owner)) continue;
            iterator.remove();
            for (Build build : builds.remove(entry.getKey())) cells -= build.size();
            count--;
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        sites.clear();
        builds.clear();
        NBTTagList list = tag.getTagList("Sites", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            UUID owner;
            try {
                owner = UUID.fromString(entry.getString("Owner"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            Site site = new Site(
                owner,
                entry.getInteger("Dim"),
                entry.getInteger("X"),
                entry.getInteger("Y"),
                entry.getInteger("Z"),
                entry.getInteger("Machine"));
            List<Build> siteBuilds = new ArrayList<>();
            NBTTagList buildList = entry.getTagList("Builds", Constants.NBT.TAG_COMPOUND);
            for (int b = 0; b < buildList.tagCount(); b++) {
                Build build = Build.read(buildList.getCompoundTagAt(b));
                if (build.size() > 0) siteBuilds.add(build);
            }
            if (siteBuilds.isEmpty()) continue;
            sites.put(site.key(), site);
            builds.put(site.key(), siteBuilds);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, Site> entry : sites.entrySet()) {
            Site site = entry.getValue();
            NBTTagCompound siteTag = new NBTTagCompound();
            siteTag.setString(
                "Owner",
                site.owner()
                    .toString());
            siteTag.setInteger("Dim", site.dimension());
            siteTag.setInteger("X", site.x());
            siteTag.setInteger("Y", site.y());
            siteTag.setInteger("Z", site.z());
            siteTag.setInteger("Machine", site.machine());
            NBTTagList buildList = new NBTTagList();
            for (Build build : builds.get(entry.getKey())) buildList.appendTag(build.write());
            siteTag.setTag("Builds", buildList);
            list.appendTag(siteTag);
        }
        tag.setTag("Sites", list);
    }
}
