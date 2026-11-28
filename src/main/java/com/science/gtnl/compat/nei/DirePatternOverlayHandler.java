package com.science.gtnl.compat.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.nbt.NBTTagCompound;

import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.client.gui.GuiDirePatternEncoder;
import com.science.gtnl.common.gui.container.ContainerDirePatternEncoder;
import com.science.gtnl.common.packet.DirePatternHandler;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;

public class DirePatternOverlayHandler implements IOverlayHandler {

    private final boolean arcane;

    public DirePatternOverlayHandler(boolean arcane) {
        this.arcane = arcane;
    }

    @Override
    public void overlayRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, boolean maxTransfer) {
        if (firstGui instanceof GuiDirePatternEncoder) {
            var p = getInputInv(recipe.getIngredientStacks(recipeIndex));
            ScienceNotLeisure.network.sendToServer(new DirePatternHandler((byte) (arcane ? 4 : 2), p));
        }
    }

    private NBTTagCompound getInputInv(List<PositionedStack> ingredientStacks) {
        var recipe = new NBTTagCompound();
        for (var stack : ingredientStacks) {
            if (stack == null || stack.item == null) continue;
            int slot = slotIndex(stack);
            if (slot < 0) continue;
            recipe.setTag(Integer.toString(slot), stack.item.writeToNBT(new NBTTagCompound()));
        }
        return recipe;
    }

    public ArrayList<PositionedStack> positionStacks(ArrayList<PositionedStack> stacks) {
        stacks.removeIf(stack -> slotIndex(stack) < 0);
        for (var stack : stacks) {
            int slot = slotIndex(stack);
            stack.relx = 12 + slot % 9 * 18;
            stack.rely = 8 + slot / 9 * 18;
        }
        return stacks;
    }

    private int slotIndex(PositionedStack stack) {
        int x = stack.relx - (arcane ? 47 : 3);
        int y = stack.rely - (arcane ? 38 : 3);
        int col = x / (arcane ? 28 : 18);
        int row = y / (arcane ? 27 : 18);
        int width = arcane ? 3 : 9;
        if (x < 0 || y < 0 || col >= width || row >= width) return -1;
        return arcane ? ContainerDirePatternEncoder.arcaneSlot(row * 3 + col) : row * 9 + col;
    }

}
