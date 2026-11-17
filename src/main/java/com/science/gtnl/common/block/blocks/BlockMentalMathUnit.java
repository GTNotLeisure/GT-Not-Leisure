package com.science.gtnl.common.block.blocks;

import static com.science.gtnl.ScienceNotLeisure.RESOURCE_ROOT_ID;

import java.util.List;
import java.util.Objects;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.science.gtnl.common.block.blocks.tile.TileEntityMentalMathUnit;

import appeng.block.AEBaseTileBlock;
import appeng.client.texture.FlippableIcon;
import appeng.core.sync.GuiBridge;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockMentalMathUnit extends AEBaseTileBlock {

    private final MentalMathUnitDefinition definition;

    public BlockMentalMathUnit(MentalMathUnitDefinition definition) {
        super(Material.iron);
        this.definition = Objects.requireNonNull(definition, "definition");
        setTileEntity(TileEntityMentalMathUnit.class);
    }

    public MentalMathUnitDefinition getDefinition() {
        return definition;
    }

    @Override
    public String getUnlocalizedName() {
        return "gtnl.block.mentalmath_unit." + definition.name();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        String root = RESOURCE_ROOT_ID + ":mentalmath_unit/" + definition.name() + "/";
        FlippableIcon bottom = new FlippableIcon(register.registerIcon(root + "bottom"));
        FlippableIcon top = new FlippableIcon(register.registerIcon(root + "top"));
        FlippableIcon front = new FlippableIcon(register.registerIcon(root + "front"));
        FlippableIcon back = new FlippableIcon(register.registerIcon(root + "back"));
        FlippableIcon left = new FlippableIcon(register.registerIcon(root + "side_left"));
        FlippableIcon right = new FlippableIcon(register.registerIcon(root + "side_right"));
        blockIcon = front;
        getRendererInstance().updateIcons(bottom, top, back, front, left, right);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(StatCollector.translateToLocalFormatted("gtnl.block.mentalmath_unit.bytes", definition.bytes()));
        tooltip.add(
            StatCollector
                .translateToLocalFormatted("gtnl.block.mentalmath_unit.parallelism", definition.parallelism()));
    }

    @Override
    public boolean onActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY,
        float hitZ) {
        TileEntityMentalMathUnit tile = getTileEntity(world, x, y, z);
        if (tile == null || player.isSneaking() || !tile.isFormed() || !tile.isActive()) {
            return false;
        }
        if (!world.isRemote) {
            Platform.openGUI(player, tile, ForgeDirection.getOrientation(side), GuiBridge.GUI_CRAFTING_CPU);
        }
        return true;
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if (world.isRemote) {
            return;
        }
        TileEntityMentalMathUnit tile = getTileEntity(world, x, y, z);
        if (tile != null) {
            tile.updateMultiBlock();
        }
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int metadata) {
        TileEntityMentalMathUnit tile = getTileEntity(world, x, y, z);
        if (tile != null) {
            tile.breakCluster();
            tile.spillPreviousState();
        }
        super.breakBlock(world, x, y, z, block, metadata);
    }

    @Override
    public int getDamageValue(World world, int x, int y, int z) {
        return 0;
    }
}
