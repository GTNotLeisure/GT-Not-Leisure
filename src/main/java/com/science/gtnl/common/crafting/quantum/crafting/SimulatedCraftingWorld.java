package com.science.gtnl.common.crafting.quantum.crafting;

import java.io.File;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.storage.IPlayerFileData;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

public class SimulatedCraftingWorld extends World {

    public SimulatedCraftingWorld(WorldServer source) {
        super(
            new SimulationSaveHandler(source.getWorldInfo()),
            "QuantumCraftingSimulation",
            new WorldSettings(source.getWorldInfo()),
            new WorldProviderSurface(),
            new Profiler());
        this.isRemote = true;
    }

    @Override
    protected IChunkProvider createChunkProvider() {
        return null;
    }

    @Override
    protected int func_152379_p() {
        return 0;
    }

    @Override
    public Entity getEntityByID(int id) {
        return null;
    }

    @Override
    public Chunk getChunkFromChunkCoords(int x, int z) {
        return new EmptyChunk(this, x, z);
    }

    @Override
    public boolean blockExists(int x, int y, int z) {
        return false;
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        return Blocks.air;
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        return null;
    }

    @Override
    public void setTileEntity(int x, int y, int z, TileEntity tile) {}

    @Override
    public boolean setBlock(int x, int y, int z, Block block, int metadata, int flags) {
        return false;
    }

    @Override
    public boolean spawnEntityInWorld(Entity entity) {
        return false;
    }

    public static class SimulationSaveHandler implements ISaveHandler {

        private final WorldInfo info;

        public SimulationSaveHandler(WorldInfo source) {
            this.info = new WorldInfo(source);
            this.info.setServerInitialized(true);
        }

        @Override
        public WorldInfo loadWorldInfo() {
            return this.info;
        }

        @Override
        public void checkSessionLock() {}

        @Override
        public IChunkLoader getChunkLoader(WorldProvider provider) {
            return null;
        }

        @Override
        public void saveWorldInfoWithPlayer(WorldInfo worldInfo, NBTTagCompound player) {}

        @Override
        public void saveWorldInfo(WorldInfo worldInfo) {}

        @Override
        public IPlayerFileData getSaveHandler() {
            return null;
        }

        @Override
        public void flush() {}

        @Override
        public File getWorldDirectory() {
            return null;
        }

        @Override
        public File getMapFileFromName(String name) {
            return null;
        }

        @Override
        public String getWorldDirectoryName() {
            return "QuantumCraftingSimulation";
        }
    }
}
