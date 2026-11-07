package com.science.gtnl.utils.structure;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureElement;

public class TileEntityStructureElement<T, E extends TileEntity> implements IStructureElement<T> {

    private final IStructureElement<T> delegate;
    private final Class<E> tileType;
    private final BiPredicate<T, E> tileValidator;

    public TileEntityStructureElement(IStructureElement<T> delegate, Class<E> tileType,
        BiPredicate<T, E> tileValidator) {
        this.delegate = Objects.requireNonNull(delegate);
        this.tileType = Objects.requireNonNull(tileType);
        this.tileValidator = Objects.requireNonNull(tileValidator);
    }

    @Override
    public boolean check(T controller, World world, int x, int y, int z) {
        if (!delegate.check(controller, world, x, y, z)) return false;
        TileEntity tile = world.getTileEntity(x, y, z);
        return tileType.isInstance(tile) && tileValidator.test(controller, tileType.cast(tile));
    }

    @Override
    public boolean couldBeValid(T controller, World world, int x, int y, int z, ItemStack trigger) {
        return delegate.couldBeValid(controller, world, x, y, z, trigger)
            && tileType.isInstance(world.getTileEntity(x, y, z));
    }

    @Override
    public Collection<String> getChannels() {
        return delegate.getChannels();
    }

    @Override
    public List<String> getDescription(T controller) {
        return delegate.getDescription(controller);
    }

    @Override
    public boolean spawnHint(T controller, World world, int x, int y, int z, ItemStack trigger) {
        return delegate.spawnHint(controller, world, x, y, z, trigger);
    }

    @Override
    public boolean placeBlock(T controller, World world, int x, int y, int z, ItemStack trigger) {
        return delegate.placeBlock(controller, world, x, y, z, trigger);
    }

    @Override
    public BlocksToPlace getBlocksToPlace(T controller, World world, int x, int y, int z, ItemStack trigger,
        AutoPlaceEnvironment environment) {
        return delegate.getBlocksToPlace(controller, world, x, y, z, trigger, environment);
    }

    @Override
    public PlaceResult survivalPlaceBlock(T controller, World world, int x, int y, int z, ItemStack trigger,
        AutoPlaceEnvironment environment) {
        PlaceResult result = delegate.survivalPlaceBlock(controller, world, x, y, z, trigger, environment);
        return result == PlaceResult.SKIP && !tileType.isInstance(world.getTileEntity(x, y, z)) ? PlaceResult.REJECT
            : result;
    }

    @Deprecated
    @Override
    public PlaceResult survivalPlaceBlock(T controller, World world, int x, int y, int z, ItemStack trigger,
        IItemSource source, EntityPlayerMP actor, Consumer<IChatComponent> chatter) {
        return survivalPlaceBlock(
            controller,
            world,
            x,
            y,
            z,
            trigger,
            AutoPlaceEnvironment.fromLegacy(source, actor, chatter));
    }

    @Override
    public int getStepA() {
        return delegate.getStepA();
    }

    @Override
    public int getStepB() {
        return delegate.getStepB();
    }

    @Override
    public int getStepC() {
        return delegate.getStepC();
    }

    @Override
    public boolean resetA() {
        return delegate.resetA();
    }

    @Override
    public boolean resetB() {
        return delegate.resetB();
    }

    @Override
    public boolean resetC() {
        return delegate.resetC();
    }

    @Override
    public boolean isNavigating() {
        return delegate.isNavigating();
    }
}
