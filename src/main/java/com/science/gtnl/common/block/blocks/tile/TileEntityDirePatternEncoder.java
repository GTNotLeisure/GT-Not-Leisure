package com.science.gtnl.common.block.blocks.tile;

import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.science.gtnl.common.gui.container.ContainerDirePatternEncoder;
import com.science.gtnl.common.item.items.ArcaneCraftPattern;
import com.science.gtnl.common.recipe.thaumcraft.ArcanePatternRecipes;
import com.science.gtnl.compat.thaumicenergistics.InfusionProviders;

import appeng.tile.AEBaseTile;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.Platform;
import lombok.Getter;

public class TileEntityDirePatternEncoder extends AEBaseTile implements IAEAppEngInventory {

    // 0 = input,1 = output
    @Getter
    private final AppEngInternalInventory pattern = new AppEngInternalInventory(this, 2, 64);
    // 0 - 80 = input,81 = output
    @Getter
    private final AppEngInternalInventory recipe = new AppEngInternalInventory(this, 82, 1);
    @Getter
    private int recipeRevision;
    @Getter
    private boolean arcaneMode;
    @Getter
    private final boolean arcaneSupported;
    private boolean writeEssentia = true;
    private boolean storingOutput;

    public TileEntityDirePatternEncoder() {
        this(InfusionProviders.isAvailable());
    }

    TileEntityDirePatternEncoder(boolean arcaneSupported) {
        this.arcaneSupported = arcaneSupported;
    }

    public void storeEncodedPattern(ItemStack stack) {
        storingOutput = true;
        try {
            pattern.setInventorySlotContents(1, stack);
        } finally {
            storingOutput = false;
        }
    }

    public boolean writesEssentia() {
        return arcaneMode && writeEssentia;
    }

    public void setWriteEssentia(boolean write) {
        if (!arcaneMode || writeEssentia == write) return;
        writeEssentia = write;
        recipeRevision++;
        markDirty();
    }

    public void setArcaneMode(boolean arcaneMode) {
        if (this.arcaneMode == arcaneMode || arcaneMode && !arcaneSupported) return;
        this.arcaneMode = arcaneMode;
        recipeRevision++;
        markDirty();
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readCustomFromNBT(NBTTagCompound aNBT) {
        pattern.readFromNBT(aNBT, "pattern");
        recipe.readFromNBT(aNBT, "recipe");
        arcaneMode = arcaneSupported && aNBT.getBoolean("arcaneMode");
        writeEssentia = !aNBT.hasKey("writeEssentia") || aNBT.getBoolean("writeEssentia");
        if (arcaneMode && !aNBT.getBoolean("centeredArcane")) {
            for (int i = 0; i < 9; i++) {
                int old = i / 3 * 9 + i % 3;
                int center = ContainerDirePatternEncoder.arcaneSlot(i);
                ItemStack oldStack = recipe.getStackInSlot(old);
                recipe.setInventorySlotContents(old, recipe.getStackInSlot(center));
                recipe.setInventorySlotContents(center, oldStack);
            }
        }
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeCustomToNBT(NBTTagCompound aNBT) {
        pattern.writeToNBT(aNBT, "pattern");
        recipe.writeToNBT(aNBT, "recipe");
        aNBT.setBoolean("arcaneMode", arcaneMode);
        aNBT.setBoolean("writeEssentia", writeEssentia);
        aNBT.setBoolean("centeredArcane", true);
    }

    @Override
    public boolean dropItems() {
        return true;
    }

    @Override
    public void getDrops(final World w, final int x, final int y, final int z, final List<ItemStack> drops) {
        for (int l = 0; l < pattern.getSizeInventory(); l++) {
            final ItemStack is = pattern.getStackInSlot(l);
            if (is != null) {
                drops.add(is);
            }
        }
    }

    @Override
    public void onChangeInventory(IInventory inv, int slot, InvOperation mc, ItemStack removedStack,
        ItemStack newStack) {
        if (inv == recipe) {
            recipeRevision++;
        } else if (arcaneSupported && !storingOutput
            && inv == pattern
            && slot == 1
            && newStack != null
            && newStack.getItem() instanceof ArcaneCraftPattern
            && newStack.hasTagCompound()) {
                var tag = newStack.getTagCompound();
                var inputs = ArcanePatternRecipes.inputs(tag);
                if (inputs.tagCount() >= 9) {
                    setArcaneMode(true);
                    for (int i = 0; i < 9; i++) recipe.setInventorySlotContents(
                        ContainerDirePatternEncoder.arcaneSlot(i),
                        Platform.loadItemStackFromNBT(inputs.getCompoundTagAt(i)));
                }
            }
        markDirty();
    }
}
