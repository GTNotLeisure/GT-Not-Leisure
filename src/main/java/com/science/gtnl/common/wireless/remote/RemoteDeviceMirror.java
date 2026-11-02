package com.science.gtnl.common.wireless.remote;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.minecraft.block.Block;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public class RemoteDeviceMirror {

    public static final int MAX_BYTES = 262_144;

    private RemoteDeviceMirror() {}

    public static byte[] encode(NBTTagCompound data) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            CompressedStreamTools.write(data, new DataOutputStream(bytes));
        } catch (IOException unwritable) {
            throw new IllegalArgumentException("Cannot encode remote device data", unwritable);
        }
        if (bytes.size() > MAX_BYTES)
            throw new IllegalArgumentException("Remote device data exceeds " + MAX_BYTES + " bytes");
        return bytes.toByteArray();
    }

    public static NBTTagCompound decode(byte[] data) {
        if (data.length == 0 || data.length > MAX_BYTES)
            throw new IllegalArgumentException("Invalid remote device data length: " + data.length);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
            // Keep vanilla's NBT allocation limit in addition to the separate wire size limit.
            NBTTagCompound mirror = CompressedStreamTools.func_152456_a(input, new NBTSizeTracker(2_097_152L));
            if (input.available() != 0) throw new IllegalArgumentException("Trailing remote device data");
            return mirror;
        } catch (IOException unreadable) {
            throw new IllegalArgumentException("Cannot decode remote device data", unreadable);
        }
    }

    public static NBTTagCompound describe(TileEntity tile) {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("block", Block.getIdFromBlock(tile.getBlockType()));
        data.setInteger("meta", tile.getBlockMetadata());
        if (tile instanceof IGregTechTileEntity machine) {
            data.setShort("machine", (short) machine.getMetaTileID());
            return data;
        }
        Packet description = tile.getDescriptionPacket();
        if (description instanceof S35PacketUpdateTileEntity update) {
            data.setInteger("action", update.func_148853_f());
            data.setTag("description", update.func_148857_g());
        }
        return data;
    }
}
