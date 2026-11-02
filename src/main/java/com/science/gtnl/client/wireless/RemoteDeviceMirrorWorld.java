package com.science.gtnl.client.wireless;

import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.SaveHandlerMP;

import com.science.gtnl.common.wireless.WirelessChannelManager.Address;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// Holds a remote device's client copy away from the real client world: AE2 cable buses replace their own tile entity
// and update lighting while reading block data, which must never touch the player's world at those coordinates.
@SideOnly(Side.CLIENT)
public class RemoteDeviceMirrorWorld extends World {

    private final int x, y, z, meta;
    private final Block block;
    private TileEntity tile;

    public RemoteDeviceMirrorWorld(World client, Address address, Block block, int meta) {
        super(
            new SaveHandlerMP(),
            "GTNLRemoteDevice",
            client.provider,
            new WorldSettings(client.getWorldInfo()),
            client.theProfiler);
        isRemote = true;
        chunkProvider = createChunkProvider();
        x = address.x();
        y = address.y();
        z = address.z();
        this.block = block;
        this.meta = meta;
    }

    public TileEntity place(TileEntity created) {
        setTileEntity(x, y, z, created);
        return tile;
    }

    public TileEntity tile() {
        return tile;
    }

    private boolean at(int x, int y, int z) {
        return x == this.x && y == this.y && z == this.z;
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        return at(x, y, z) ? block : Blocks.air;
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        return at(x, y, z) ? meta : 0;
    }

    @Override
    public boolean blockExists(int x, int y, int z) {
        return at(x, y, z);
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        return at(x, y, z) ? tile : null;
    }

    @Override
    public void setTileEntity(int x, int y, int z, TileEntity created) {
        if (!at(x, y, z) || created == null) return;
        created.setWorldObj(this);
        created.xCoord = x;
        created.yCoord = y;
        created.zCoord = z;
        created.blockType = block;
        created.blockMetadata = meta;
        tile = created;
    }

    @Override
    public void removeTileEntity(int x, int y, int z) {
        if (at(x, y, z)) tile = null;
    }

    @Override
    public boolean func_147451_t(int x, int y, int z) {
        return false;
    }

    @Override
    protected IChunkProvider createChunkProvider() {
        return new ChunkProviderClient(this);
    }

    @Override
    protected int func_152379_p() {
        return 0;
    }

    @Override
    public Entity getEntityByID(int id) {
        return null;
    }
}
