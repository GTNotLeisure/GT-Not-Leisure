package com.science.gtnl.common.crafting.quantum.crafting;

import java.util.Arrays;
import java.util.UUID;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;

public class CraftingEventSimulation {

    private static final GameProfile PROFILE = new GameProfile(
        UUID.fromString("0ae2a7ce-9a52-4b7e-8b1c-6f6f51e2a0be"),
        "[AE2]");
    private static WorldServer source;
    private static SimulatedCraftingWorld world;
    private static FakePlayer player;

    public static ItemStack processCraftingResult(ItemStack output, InventoryCrafting input, World level) {
        if (CraftingCalculation.isCalculationThread()) {
            return CraftingCalculation.runOnMainThread(() -> processCraftingResult(output, input, level));
        }
        if (!(level instanceof WorldServer server)) return output;
        if (source != server) {
            source = server;
            world = new SimulatedCraftingWorld(server);
            player = new FakePlayer(server, PROFILE);
        }
        player.worldObj = world;
        player.dimension = world.provider.dimensionId;
        Arrays.fill(player.inventory.mainInventory, null);
        Arrays.fill(player.inventory.armorInventory, null);
        // Legacy AE2 invokes the crafting event when returning containers; onCreated belongs to item production.
        FMLCommonHandler.instance()
            .firePlayerCraftingEvent(player, output, input);
        return output;
    }

    public static void clear() {
        source = null;
        world = null;
        player = null;
    }
}
