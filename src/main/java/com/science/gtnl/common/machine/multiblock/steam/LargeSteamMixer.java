package com.science.gtnl.common.machine.multiblock.steam;

import static com.science.gtnl.ScienceNotLeisure.RESOURCE_ROOT_ID;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.science.gtnl.common.machine.multiMachineBase.SteamMultiMachineBase;
import com.science.gtnl.common.machine.tooltip.GTNLMultiblockTooltipBuilder;
import com.science.gtnl.utils.StructureUtils;
import com.science.gtnl.utils.structure.MachineStructureElements;

import gregtech.api.casing.Casings;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.xmod.gregtech.common.blocks.textures.TexturesGtBlock;

@IMetaTileEntity.SkipGenerateDescription
@IMetaTileEntity.SkipGenerateName
public class LargeSteamMixer extends SteamMultiMachineBase<LargeSteamMixer> implements ISurvivalConstructable {

    private static boolean enableHVRecipe = false;
    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final String LSMi_STRUCTURE_FILE_PATH = RESOURCE_ROOT_ID + ":" + "multiblock/large_steam_mixer";
    private static final int HORIZONTAL_OFF_SET = 3;
    private static final int VERTICAL_OFF_SET = 3;
    private static final int DEPTH_OFF_SET = 0;
    private static final String[][] shape = StructureUtils.readStructureFromFile(LSMi_STRUCTURE_FILE_PATH);

    public LargeSteamMixer(String aName) {
        super(aName);
    }

    public LargeSteamMixer(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Override
    public String getLocalNameKey() {
        return "gtnl.machine.large_steam_mixer.name";
    }

    @Override
    public IStructureDefinition<LargeSteamMixer> getStructureDefinition() {
        return StructureDefinition.<LargeSteamMixer>builder()
            .addShape(STRUCTURE_PIECE_MAIN, StructureUtility.transpose(shape))
            .addElement(
                'A',
                MachineStructureElements.machineCasingWithHatches(
                    LargeSteamMixer.class,
                    getCasingTextureID(),
                    1,
                    SteamHatchElement.InputBus_Steam,
                    SteamHatchElement.OutputBus_Steam,
                    HatchElement.InputBus,
                    HatchElement.OutputBus,
                    HatchElement.InputHatch,
                    HatchElement.OutputHatch,
                    HatchElement.Maintenance))
            .addElement('B', MachineStructureElements.gearCasing())
            .addElement('C', MachineStructureElements.pipeCasing())
            .addElement('D', MachineStructureElements.fireboxCasing())
            .addElement('E', GTStructureUtility.chainAllGlasses())
            .addElement('F', MachineStructureElements.industrialCasing())
            .build();
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new LargeSteamMixer(mName);
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            HORIZONTAL_OFF_SET,
            VERTICAL_OFF_SET,
            DEPTH_OFF_SET,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        enableHVRecipe = false;
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) return;
        checkHatch(errors);
        if (checkMachineTier(
            errors,
            30,
            tierMachineCasing == 1 && tierGearCasing == 1
                && tierPipeCasing == 1
                && tierFireboxCasing == 1
                && tierIndustrialCasing == 1,
            tierMachineCasing == 2 && tierGearCasing == 2
                && tierPipeCasing == 2
                && tierFireboxCasing == 2
                && tierIndustrialCasing == 2)
            && tierMachine == 2) {
            enableHVRecipe = getUpgradeTier(aStack);
        }
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.mixerNonCellRecipes;
    }

    @Override
    public int getMaxParallelRecipes() {
        if (tierMachine == 1) {
            return 8;
        } else if (tierMachine == 2) {
            if (enableHVRecipe) return 32;
            return 16;
        }
        return 8;
    }

    @Override
    public double getEUtDiscount() {
        return super.getEUtDiscount() * (tierMachine + (enableHVRecipe ? 1 : 0));
    }

    @Override
    public double getDurationModifier() {
        return (1 / 0.67 / tierMachine - (enableHVRecipe ? 0.25 : 0)) / (1 << Math.min(4, recipeOcCount));
    }

    @Override
    public int getTierRecipes() {
        return tierMachine + (enableHVRecipe ? 1 : 0);
    }

    @NotNull
    @Override
    public CheckRecipeResult checkProcessing() {
        ItemStack controllerItem = getControllerSlot();
        enableHVRecipe = getUpgradeTier(controllerItem);
        return super.checkProcessing();
    }

    public boolean getUpgradeTier(ItemStack inventory) {
        if (inventory == null) return false;
        return inventory.isItemEqual(GTOreDictUnificator.get(OrePrefixes.gearGt, Materials.StainlessSteel, 1L));
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        int id = tierMachine >= 2 ? Casings.SolidSteelMachineCasing.getTextureId()
            : Casings.BronzePlatedBricks.getTextureId();
        if (side == aFacing) {
            if (aActive) return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(id), TextureFactory.builder()
                .addIcon(TexturesGtBlock.oMCDIndustrialMixerActive)
                .extFacing()
                .build() };
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(id), TextureFactory.builder()
                .addIcon(TexturesGtBlock.oMCDIndustrialMixer)
                .extFacing()
                .build() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(id) };
    }

    @Override
    public String getMachineType() {
        return StatCollector.translateToLocal("gtnl.machine.large_steam_mixer.recipe_type");
    }

    @Override
    public MultiblockTooltipBuilder createTooltip() {
        GTNLMultiblockTooltipBuilder tt = new GTNLMultiblockTooltipBuilder();
        tt.addAutoTooltip(this)
            .toolTipFinisher();
        return tt;
    }
}
