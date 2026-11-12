package com.science.gtnl.loader;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;

import com.science.gtnl.common.enchantment.MajoBroomEnchantment;
import com.science.gtnl.common.enchantment.MajoBroomEnchantment.Skill;
import com.science.gtnl.config.ItemConfig;

public class EnchantmentLoader {

    public static Enchantment landSkills;
    public static Enchantment airSkills;
    public static Enchantment hovering;
    public static Enchantment seabreeze;

    public static void registry() {
        if (landSkills != null) return;
        ItemConfig.Broom config = ItemConfig.broom;
        int[] ids = { config.landSkillsEnchantmentId, config.airSkillsEnchantmentId, config.hoveringEnchantmentId,
            config.seabreezeEnchantmentId };
        Set<Integer> assigned = new HashSet<>();
        for (int id : ids) {
            if (id < 0 || id >= Enchantment.enchantmentsList.length
                || !assigned.add(id)
                || Enchantment.enchantmentsList[id] != null) {
                throw new IllegalStateException(
                    "Broom enchantment ID " + id
                        + " is unavailable. Set four unique unused IDs in config/GTNotLeisure/item.cfg, item.broom. Client and server IDs must match.");
            }
        }
        landSkills = register(config.landSkillsEnchantmentId, Skill.LAND_SKILLS);
        airSkills = register(config.airSkillsEnchantmentId, Skill.AIR_SKILLS);
        hovering = register(config.hoveringEnchantmentId, Skill.HOVERING);
        seabreeze = register(config.seabreezeEnchantmentId, Skill.SEABREEZE);
    }

    private static Enchantment register(int id, Skill skill) {
        Enchantment enchantment = new MajoBroomEnchantment(id, skill);
        Enchantment.addToBookList(enchantment);
        return enchantment;
    }

    public static int getLevel(Enchantment enchantment, ItemStack stack) {
        if (enchantment == null || stack == null) return 0;
        return Math.max(
            0,
            Math.min(enchantment.getMaxLevel(), EnchantmentHelper.getEnchantmentLevel(enchantment.effectId, stack)));
    }

    public static int getFlightDurationTicks(ItemStack stack) {
        int level = getLevel(hovering, stack);
        return ItemConfig.broom.flightDurationSeconds * 20 * (4 + level) / 4;
    }
}
