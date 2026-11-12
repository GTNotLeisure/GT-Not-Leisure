package com.science.gtnl.common.item.items;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.gtnllib.text.TextEffects;
import com.science.gtnl.common.item.GTNLCreativeTabs;
import com.science.gtnl.common.item.wand.MajoWandStage;
import com.science.gtnl.utils.enums.GTNLItemList;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemMajoWandCore extends Item {

    public ItemMajoWandCore() {
        setUnlocalizedName("gtnl.majo_wand_core");
        setHasSubtypes(true);
        setMaxDamage(0);
        setTextureName("thaumcraft:charm");
        setCreativeTab(GTNLCreativeTabs.GTNotLeisureItem);

        GTNLItemList.MajoWandCore.set(createStack(MajoWandStage.DEPARTURE));
    }

    public ItemStack createStack(MajoWandStage stage) {
        return new ItemStack(this, 1, stage.ordinal());
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "item.gtnl.majo_wand_core." + MajoWandStage.fromMetadata(stack.getItemDamage()).key;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int pass) {
        return MajoWandStage.fromMetadata(stack.getItemDamage()).color;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List<ItemStack> stacks) {
        for (MajoWandStage stage : MajoWandStage.values()) {
            stacks.add(createStack(stage));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(
            TextEffects
                .apply(StatCollector.translateToLocal("item.gtnl.majo_wand_core.tooltip"), TextEffects.EVERCOLD_CYAN));
    }
}
