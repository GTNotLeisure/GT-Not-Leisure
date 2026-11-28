package com.science.gtnl.common.item.items;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.science.gtnl.common.recipe.thaumcraft.ArcanePatternRecipes;
import com.science.gtnl.compat.thaumicenergistics.InfusionProviders;
import com.science.gtnl.utils.ArcaneCraftingPatternDetails;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class ArcaneCraftPattern extends DireCraftPattern {

    @Override
    public String getUnlocalizedName() {
        return "item.gtnl.arcane_craft_pattern";
    }

    @Override
    public ArcaneCraftingPatternDetails getPatternForItem(ItemStack stack, World world) {
        if (!InfusionProviders.isAvailable() || !stack.hasTagCompound()) return null;
        try {
            var tag = stack.getTagCompound();
            boolean matrix = tag.hasKey("matrixMultiply");
            int multiply = Math.max(1, tag.getInteger("matrixMultiply"));
            if (matrix) return getPatternForItem(stack, world, multiply);
            return new ArcaneCraftingPatternDetails(stack, multiply, false, ArcanePatternRecipes.writtenCosts(tag));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public ArcaneCraftingPatternDetails getPatternForItem(ItemStack stack, World world, int multiply) {
        try {
            if (!InfusionProviders.isAvailable() || !stack.hasTagCompound() || world == null) return null;
            var cost = world.isRemote ? ArcanePatternRecipes.writtenCosts(stack.getTagCompound())
                : ArcanePatternRecipes.validatedCosts(stack, world);
            return cost == null ? null : new ArcaneCraftingPatternDetails(stack, multiply, true, cost);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected void getCheckedSubItems(Item item, CreativeTabs tab, List<ItemStack> items) {
        if (InfusionProviders.isAvailable()) super.getCheckedSubItems(item, tab, items);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addCheckedInformation(ItemStack stack, EntityPlayer player, List<String> lines, boolean more) {
        if (!InfusionProviders.isAvailable()) {
            lines.add(StatCollector.translateToLocal("gtnl.dire_encoder.requires_thaumic_energistics"));
            return;
        }
        super.addCheckedInformation(stack, player, lines, more);
        if (stack.hasTagCompound() && stack.getTagCompound()
            .getBoolean(ArcanePatternRecipes.STALE)) {
            lines.add(StatCollector.translateToLocal("gtnl.dire_encoder.stale"));
        }
    }
}
