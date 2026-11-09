package com.science.gtnl.common.wireless;

import java.util.List;
import java.util.Objects;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.DimensionManager;

import com.science.gtnl.common.me.InterfaceNameLocalization;

import galacticgreg.api.enums.DimensionDef;
import gtneioreplugin.util.DimensionHelper;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class WirelessCardViewCodec {

    static void writeIcon(NBTTagCompound data, String key, ItemStack icon) {
        if (icon == null || icon.getItem() == null) return;
        // Display-only identity, never ship inventories or other machine NBT as an icon.
        ItemStack clean = new ItemStack(icon.getItem(), 1, icon.getItemDamage());
        data.setTag(key, clean.writeToNBT(new NBTTagCompound()));
    }

    static void writeDimensionName(NBTTagCompound dimensions, int dimension) {
        String id = Integer.toString(dimension);
        if (dimensions.hasKey(id)) return;
        var world = DimensionManager.getWorld(dimension);
        dimensions.setString(id, world == null ? "" : Objects.toString(DimensionDef.getDimensionName(world), ""));
    }

    public static void localizeDimensions(NBTTagCompound data) {
        NBTTagCompound dimensions = data.getCompoundTag("dimensions");
        data.setTag("dimensionKeys", dimensions.copy());
        for (Object key : dimensions.func_150296_c()) {
            String id = (String) key;
            String name = dimensions.getString(id);
            if (name.equals("Personal World " + id)) name = "personalworld";
            if (!name.isEmpty()) dimensions.setString(id, DimensionHelper.getDimLocalizedName(name));
        }
        localizePosition(data, "source", dimensions);
        localizePosition(data, "groupPosition", dimensions);
        NBTTagList rows = data.getTagList("rows", 10);
        for (int i = 0; i < rows.tagCount(); i++) localizePosition(rows.getCompoundTagAt(i), "position", dimensions);
    }

    public static void localizePosition(NBTTagCompound data, String key, NBTTagCompound dimensions) {
        String position = data.getString(key);
        int end = position.indexOf(' ');
        String name = position.startsWith("D")
            ? dimensions.getString(position.substring(1, end < 0 ? position.length() : end))
            : "";
        // Keep the numeric form for coordinate copying and client-side filtering.
        data.setString(key + "Label", name.isEmpty() ? position : name + " " + position);
    }

    static void writeInterfaceNames(NBTTagCompound data, List<WirelessInterfaceView.Name> names) {
        if (names.isEmpty()) return;
        NBTTagList entries = new NBTTagList();
        for (var name : names.subList(0, Math.min(4, names.size()))) entries.appendTag(writeInterfaceName(name));
        data.setTag("interfaceNames", entries);
        data.setInteger("interfaceNameCount", names.size());
        for (var name : names) {
            if (!name.connected()) continue;
            writeIcon(data, "linkedIcon", name.icon());
            data.setTag("linkedName", writeInterfaceName(name));
            break;
        }
        for (var name : names) {
            if (name.configurationIcon() == null) continue;
            writeIcon(data, "configurationIcon", name.configurationIcon());
            data.setTag("configurationName", writeInterfaceName(name));
            break;
        }
    }

    private static NBTTagCompound writeInterfaceName(WirelessInterfaceView.Name name) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("rawName", name.rawName());
        entry.setString("customName", name.customName());
        entry.setString("suffix", name.suffix());
        writeIcon(entry, "icon", name.icon());
        return entry;
    }

    public static void localizeInterfaceNames(NBTTagCompound row) {
        NBTTagList entries = row.getTagList("interfaceNames", 10);
        if (entries.tagCount() == 0) return;
        List<String> names = new ObjectArrayList<>();
        for (int i = 0; i < entries.tagCount(); i++) names.add(localizeInterfaceName(entries.getCompoundTagAt(i)));
        int count = row.getInteger("interfaceNameCount");
        String targetName = String.join(" / ", names) + (count > entries.tagCount() ? " … [" + count + "]" : "");
        row.setString("targetName", targetName);
        if (row.hasKey("configurationIcon", 10))
            row.setString("configurationLabel", localizeInterfaceName(row.getCompoundTag("configurationName")));
        if (row.hasKey("linkedIcon", 10))
            row.setString("linkedLabel", localizeInterfaceName(row.getCompoundTag("linkedName")));
    }

    private static String localizeInterfaceName(NBTTagCompound entry) {
        String customName = entry.getString("customName");
        if (!customName.isEmpty()) return customName
            + InterfaceNameLocalization.resolveSuffix(entry.getString("suffix"), EnumChatFormatting.AQUA);
        return InterfaceNameLocalization.localizeName(
            entry.getString("rawName"),
            entry.getString("suffix"),
            ItemStack.loadItemStackFromNBT(entry.getCompoundTag("icon")),
            EnumChatFormatting.AQUA);
    }
}
