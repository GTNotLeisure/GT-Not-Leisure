package com.science.gtnl.common.enchantment;

import java.util.Locale;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.enderio.core.api.common.enchant.IAdvancedEnchant;
import com.science.gtnl.common.item.items.ItemMajoBroom;

public class MajoBroomEnchantment extends Enchantment implements IAdvancedEnchant {

    public enum Skill {
        LAND_SKILLS,
        AIR_SKILLS,
        HOVERING,
        SEABREEZE
    }

    private final Skill skill;

    public MajoBroomEnchantment(int id, Skill skill) {
        super(id, skill == Skill.SEABREEZE ? 2 : skill == Skill.HOVERING ? 5 : 10, EnumEnchantmentType.all);
        this.skill = skill;
        setName(
            "gtnl." + skill.name()
                .toLowerCase(Locale.ROOT));
    }

    @Override
    public int getMaxLevel() {
        return switch (skill) {
            case LAND_SKILLS, AIR_SKILLS -> 3;
            case HOVERING -> 4;
            case SEABREEZE -> 1;
        };
    }

    @Override
    public int getMinEnchantability(int level) {
        if (skill == Skill.SEABREEZE) return 20;
        return 1 + (level - 1) * (skill == Skill.HOVERING ? 8 : 10);
    }

    @Override
    public int getMaxEnchantability(int level) {
        return getMinEnchantability(level) + 25;
    }

    @Override
    public boolean canApply(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemMajoBroom;
    }

    @Override
    public boolean canApplyTogether(Enchantment other) {
        return super.canApplyTogether(other)
            && !(isSpeedSkill() && other instanceof MajoBroomEnchantment broom && broom.isSpeedSkill());
    }

    private boolean isSpeedSkill() {
        return skill == Skill.LAND_SKILLS || skill == Skill.AIR_SKILLS;
    }

    @Override
    public String[] getTooltipDetails(ItemStack stack) {
        return new String[] { StatCollector.translateToLocal(getName() + ".desc") };
    }
}
