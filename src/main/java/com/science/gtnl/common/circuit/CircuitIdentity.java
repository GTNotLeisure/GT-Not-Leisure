package com.science.gtnl.common.circuit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.science.gtnl.api.mixinHelper.GTNLNBTTagList;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class CircuitIdentity {

    private CircuitIdentity() {}

    public static String item(ItemStack stack) {
        return Item.itemRegistry.getNameForObject(stack.getItem()) + ":"
            + stack.getItemDamage()
            + ":"
            + nbt(stack.getTagCompound());
    }

    public static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                result.append(Character.forDigit((b >>> 4) & 15, 16));
                result.append(Character.forDigit(b & 15, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static String nbt(NBTBase tag) {
        if (tag == null) return "";
        StringBuilder result = new StringBuilder().append(tag.getId())
            .append(':');
        if (tag instanceof NBTTagCompound compound) {
            List<String> keys = new ObjectArrayList<>();
            for (Object key : compound.func_150296_c()) keys.add((String) key);
            Collections.sort(keys);
            for (String key : keys) {
                String value = nbt(compound.getTag(key));
                result.append(key.length())
                    .append(':')
                    .append(key)
                    .append(value.length())
                    .append(':')
                    .append(value);
            }
        } else if (tag instanceof NBTTagList list) {
            for (NBTBase element : (GTNLNBTTagList) list) {
                String value = nbt(element);
                result.append(value.length())
                    .append(':')
                    .append(value);
            }
        } else {
            result.append(tag);
        }
        return result.toString();
    }
}
