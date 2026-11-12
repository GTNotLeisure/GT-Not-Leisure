package com.science.gtnl.common.item.items;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.gtnllib.text.TextEffects;
import com.science.gtnl.common.item.GTNLCreativeTabs;
import com.science.gtnl.common.item.wand.MajoWandStage;
import com.science.gtnl.utils.enums.GTNLItemList;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.wands.WandCap;
import thaumcraft.api.wands.WandRod;
import thaumcraft.common.items.wands.ItemWandCasting;

@SuppressWarnings("unchecked")
public class ItemMajoWand extends ItemWandCasting {

    private static final Aspect[] PRIMAL_ASPECTS = Aspect.getPrimalAspects()
        .toArray(new Aspect[0]);

    private final WandRod[] rods = new WandRod[MajoWandStage.values().length];
    private final WandCap[] caps = new WandCap[MajoWandStage.values().length];

    public ItemMajoWand() {
        setUnlocalizedName("gtnl.majo_wand");
        setCreativeTab(GTNLCreativeTabs.GTNotLeisureItem);

        for (MajoWandStage stage : MajoWandStage.values()) {
            String tag = "gtnl_majo_" + stage.key;
            ItemStack component = new ItemStack(this, 1, stage.ordinal());
            WandRod rod = new WandRod(
                tag,
                stage.capacity,
                component,
                9,
                stage == MajoWandStage.JOURNEYS_END ? ItemMajoWand::refillVis : null,
                new ResourceLocation(
                    "thaumcraft",
                    "textures/models/wand_rod_" + (stage == MajoWandStage.DEPARTURE ? "greatwood" : "silverwood")
                        + ".png"));
            rod.setGlowing(stage == MajoWandStage.JOURNEYS_END);
            rods[stage.ordinal()] = rod;
            WandCap cap = new WandCap(tag, stage.costMultiplier, component, 9);
            cap.setTexture(
                new ResourceLocation(
                    "thaumcraft",
                    "textures/models/wand_cap_"
                        + (stage.ordinal() >= MajoWandStage.VOID.ordinal() ? "void" : "thaumium")
                        + ".png"));
            caps[stage.ordinal()] = cap;
        }
        GTNLItemList.MajoWand.set(createStack(MajoWandStage.DEPARTURE));
    }

    public ItemStack createStack(MajoWandStage stage) {
        ItemStack stack = new ItemStack(this, 1, stage.ordinal());
        setRod(stack, rods[stage.ordinal()]);
        setCap(stack, caps[stage.ordinal()]);
        return stack;
    }

    public ItemStack upgradeStack(ItemStack input, MajoWandStage target) {
        if (input == null || target == null
            || target == MajoWandStage.DEPARTURE
            || input.getItem() != this
            || input.getItemDamage() != target.ordinal() - 1) {
            throw new IllegalArgumentException("Majo wands must be upgraded one stage at a time");
        }
        ItemStack result = input.copy();
        result.stackSize = 1;
        result.setItemDamage(target.ordinal());
        setRod(result, rods[target.ordinal()]);
        setCap(result, caps[target.ordinal()]);
        return result;
    }

    @Override
    public WandRod getRod(ItemStack stack) {
        return rods[MajoWandStage.fromMetadata(stack.getItemDamage())
            .ordinal()];
    }

    @Override
    public WandCap getCap(ItemStack stack) {
        return caps[MajoWandStage.fromMetadata(stack.getItemDamage())
            .ordinal()];
    }

    @Override
    public boolean isSceptre(ItemStack stack) {
        return false;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return StatCollector.translateToLocal("item.gtnl.majo_wand.name");
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        if (entity instanceof EntityPlayer) super.onUpdate(stack, world, entity, slot, held);
    }

    private static void refillVis(ItemStack stack, EntityPlayer player) {
        if (player != null && (player.worldObj.isRemote || player.ticksExisted % 20 != 0)) return;
        ItemWandCasting wand = (ItemWandCasting) stack.getItem();
        int maximum = wand.getMaxVis(stack);
        for (Aspect aspect : PRIMAL_ASPECTS) {
            int current = wand.getVis(stack, aspect);
            int amount = player == null ? maximum : 100;
            int next = (int) Math.min(maximum, (long) Math.max(0, current) + amount);
            if (current != next) stack.setTagInfo(aspect.getTag(), new NBTTagInt(next));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List stacks) {
        for (MajoWandStage stage : MajoWandStage.values()) {
            ItemStack stack = createStack(stage);
            refillVis(stack, null);
            stacks.add(stack);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        MajoWandStage stage = MajoWandStage.fromMetadata(stack.getItemDamage());
        tooltip.add(
            TextEffects.apply(
                StatCollector.translateToLocalFormatted(
                    "item.gtnl.majo_wand.stage",
                    StatCollector.translateToLocal("item.gtnl.majo_wand.stage." + stage.key)),
                TextEffects.GENESIS_COMPONENT_RARITY_SHADER));
        tooltip.add(
            TextEffects.apply(
                StatCollector.translateToLocalFormatted(
                    "item.gtnl.majo_wand.discount",
                    Math.round((1.0F - Math.max(stage.costMultiplier, 0.1F)) * 100)),
                TextEffects.EVERCOLD_CYAN));
        tooltip.add(
            TextEffects
                .apply(StatCollector.translateToLocal("item.gtnl.majo_wand.tooltip"), TextEffects.EVERCOLD_CYAN));
        if (stage == MajoWandStage.JOURNEYS_END) {
            tooltip.add(
                TextEffects
                    .apply(StatCollector.translateToLocal("item.gtnl.majo_wand.refill"), TextEffects.EVERCOLD_CYAN));
        }
        super.addInformation(stack, player, tooltip, advanced);
    }
}
