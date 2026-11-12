package com.science.gtnl.api;

import com.gtnewhorizon.gtnhlib.util.data.ItemId;

import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;

public interface ICrusherGui {

    CheckRecipeResult INVALID = SimpleCheckRecipeResult.ofFailure("gtnl.crusher.invalid_selection");

    ItemId getItemEntry();

    void setItemEntry(ItemId entry);
}
