package com.science.gtnl.common.recipe.thaumcraft;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.IntFunction;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;

import com.google.common.hash.Hashing;
import com.mojang.authlib.GameProfile;
import com.science.gtnl.compat.thaumicenergistics.InfusionProviders;

import appeng.util.Platform;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.common.lib.FakeThaumcraftPlayer;
import thaumcraft.common.tiles.TileArcaneWorkbench;

public final class ArcanePatternRecipes {

    public static final String STALE = "arcaneRecipeMismatch";
    private static final String RESEARCH = "arcaneResearchChecks";
    public static final String AUTHOR_ID = "arcaneAuthorId";
    private static final ThreadLocal<ResearchCheck> CHECK = new ThreadLocal<>();

    private record ResearchCheck(String player, NBTTagCompound keys, boolean replay) {}

    public static Boolean replayResearch(String player, String key) {
        var check = CHECK.get();
        return check != null && check.replay && check.player.equals(player) ? check.keys.getBoolean(key) : null;
    }

    public static void recordResearch(String player, String key, boolean granted) {
        var check = CHECK.get();
        if (granted && check != null && !check.replay && check.player.equals(player)) check.keys.setBoolean(key, true);
    }

    public static Match match(EntityPlayer player, IntFunction<ItemStack> inputs, NBTTagCompound encoded) {
        var result = find(player, inputs, encoded, null);
        if (result != null && encoded != null) encoded.setString(
            AUTHOR_ID,
            player.getUniqueID()
                .toString());
        return result;
    }

    public static NBTTagCompound validatedCosts(ItemStack pattern, World world) {
        NBTTagCompound tag = pattern.getTagCompound();
        if (tag == null) return null;
        var slots = inputs(tag);
        if (slots.tagCount() < 9) return null;
        boolean replay = tag.hasKey(RESEARCH, NBT.TAG_COMPOUND);
        String author = tag.getString("author");
        if (author.isEmpty()) return null;
        EntityPlayer player = world.getPlayerEntityByName(author);
        if (player == null) {
            UUID id;
            try {
                id = tag.hasKey(AUTHOR_ID) ? UUID.fromString(tag.getString(AUTHOR_ID))
                    : UUID.nameUUIDFromBytes(("OfflinePlayer:" + author).getBytes(StandardCharsets.UTF_8));
            } catch (IllegalArgumentException invalidProfile) {
                return null;
            }
            player = new FakeThaumcraftPlayer(world, new GameProfile(id, author));
        }
        Match current = find(
            player,
            slot -> Platform.loadItemStackFromNBT(slots.getCompoundTagAt(slot)),
            null,
            replay ? tag.getCompoundTag(RESEARCH) : null);
        return current == null ? null
            : current.validatedCost(Platform.loadItemStackFromNBT(tag.getCompoundTag("out")), tag);
    }

    public static boolean writesEssentia(NBTTagCompound tag) {
        return !tag.hasKey("writeEssentia") || tag.getBoolean("writeEssentia");
    }

    public static NBTTagList inputs(NBTTagCompound tag) {
        return tag.getTagList(tag.hasKey("in") ? "in" : "ingredients", NBT.TAG_COMPOUND);
    }

    public static NBTTagCompound writtenCosts(NBTTagCompound tag) {
        var inputs = inputs(tag);
        if (inputs.tagCount() < 9) throw new IllegalArgumentException("Invalid arcane grid");
        if (tag.hasKey("essentia") && inputs.tagCount() != 9)
            throw new IllegalArgumentException("Ambiguous arcane cost");
        var written = tag.hasKey("essentia") ? (NBTTagCompound) tag.getCompoundTag("essentia")
            .copy() : new NBTTagCompound();
        for (int i = 9; i < inputs.tagCount(); i++) {
            var entry = inputs.getCompoundTagAt(i);
            String key = entry.getString("AspectTag");
            long count = entry.getLong("Cnt");
            if (!"essentia".equals(entry.getString("StackType")) || written.hasKey(key)
                || count <= 0
                || count > Integer.MAX_VALUE
                || entry.hasKey("Amount") && entry.getLong("Amount") != count)
                throw new IllegalArgumentException("Invalid essentia input");
            written.setInteger(key, (int) count);
        }
        if (!writesEssentia(tag) && !written.hasNoTags())
            throw new IllegalArgumentException("Unexpected essentia input");
        for (String key : written.func_150296_c()) if (Aspect.getAspect(key) == null || written.getInteger(key) <= 0)
            throw new IllegalArgumentException("Invalid aspect: " + key);
        return written;
    }

    public record Match(ItemStack output, NBTTagCompound costs) {

        public void finishPattern(NBTTagCompound pattern, NBTTagList inputs, boolean write) {
            if (write) {
                for (String key : new ObjectAVLTreeSet<>(costs.func_150296_c())) {
                    inputs.appendTag(
                        InfusionProviders.nativeIngredient(Aspect.getAspect(key), costs.getInteger(key))
                            .toNBTGeneric());
                }
            } else pattern.setByteArray("arcaneRecipeCostHash", costHash());
            pattern.setTag("in", inputs);
            pattern.setBoolean("writeEssentia", write);
        }

        NBTTagCompound validatedCost(ItemStack expected, NBTTagCompound tag) {
            if (!ItemStack.areItemStacksEqual(output, expected)) return null;
            var recorded = writtenCosts(tag);
            if (writesEssentia(tag) && !costs.equals(recorded)) return null;
            if (tag.hasKey("arcaneRecipeCostHash")
                && !Arrays.equals(tag.getByteArray("arcaneRecipeCostHash"), costHash())) return null;
            return costs;
        }

        private byte[] costHash() {
            var hash = Hashing.sha256()
                .newHasher();
            for (String key : new ObjectAVLTreeSet<>(costs.func_150296_c())) {
                byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
                hash.putInt(bytes.length)
                    .putBytes(bytes)
                    .putInt(costs.getInteger(key));
            }
            return hash.hash()
                .asBytes();
        }
    }

    private static Match find(EntityPlayer player, IntFunction<ItemStack> inputs, NBTTagCompound encoded,
        NBTTagCompound replay) {
        var grid = new TileArcaneWorkbench();
        grid.setWorldObj(player.worldObj);
        for (int i = 0; i < 9; i++) grid.setInventorySlotContentsSoftly(i, ItemStack.copyItemStack(inputs.apply(i)));
        return findIn(ThaumcraftApi.getCraftingRecipes(), grid, player, player.getCommandSenderName(), encoded, replay);
    }

    static Match findIn(Iterable<?> recipes, TileArcaneWorkbench grid, EntityPlayer player, String name,
        NBTTagCompound encoded, NBTTagCompound replay) {
        var previous = CHECK.get();
        var keys = replay == null ? new NBTTagCompound() : replay;
        try {
            if (encoded != null || replay != null) CHECK.set(new ResearchCheck(name, keys, replay != null));
            else CHECK.remove();
            for (Object entry : recipes) {
                if (!(entry instanceof IArcaneRecipe recipe)) continue;
                if (encoded != null && replay == null) keys.func_150296_c()
                    .clear();
                if (!recipe.matches(grid, grid.getWorldObj(), player)) continue;
                var aspects = recipe.getAspects();
                if (aspects == null) aspects = recipe.getAspects(grid);
                if (aspects == null) return null;
                var cost = new NBTTagCompound();
                for (Aspect aspect : aspects.getAspects()) {
                    if (aspect == null) continue;
                    int amount = aspects.getAmount(aspect);
                    if (amount < 0) return null;
                    if (amount > 0) cost.setInteger(aspect.getTag(), amount);
                }
                ItemStack result = recipe.getCraftingResult(grid);
                if (encoded != null && replay == null) encoded.setTag(RESEARCH, keys);
                return result == null ? null : new Match(result, cost);
            }
            return null;
        } finally {
            if (previous == null) CHECK.remove();
            else CHECK.set(previous);
        }
    }
}
