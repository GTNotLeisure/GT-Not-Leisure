package com.science.gtnl.common.machine.hatch;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.gtnewhorizon.gtnhlib.util.ItemUtil;

import appeng.api.storage.data.IAEItemStack;
import appeng.core.AELog;
import appeng.util.prioitylist.IPartitionList;

public class ItemFilteredList implements IPartitionList<IAEItemStack> {

    private static final Pattern REGEX_SPECIAL_CHARACTERS = Pattern
        .compile(".*[\\^\\$\\.\\*\\+\\?\\{\\}\\[\\]\\|\\(\\)].*");
    private static final Cache<String, Predicate<String>> NAME_MATCHERS = CacheBuilder.newBuilder()
        .maximumSize(256)
        .build();

    public final Predicate<IAEItemStack> filterPredicate;

    public ItemFilteredList(String filter) {
        this.filterPredicate = makeFilter(filter.trim());
    }

    @Override
    public boolean isListed(IAEItemStack input) {
        return filterPredicate != null && filterPredicate.test(input);
    }

    @Override
    public boolean isEmpty() {
        return this.filterPredicate == null;
    }

    @Override
    public Iterable<IAEItemStack> getItems() {
        return null;
    }

    public static Predicate<IAEItemStack> makeFilter(String filter) {
        try {
            Predicate<ItemStack> matcher = makeMatcher(filter);
            if (matcher == null) return null;
            return new ItemListMatcher(matcher);
        } catch (Exception e) {
            AELog.debug(e);
            return null;
        }
    }

    public static Predicate<ItemStack> makeMatcher(String filter) {
        if (filter.isEmpty()) return null;

        Integer meta = null;
        try {
            int metaIndex = filter.indexOf('@');
            String beforeMeta = metaIndex < 0 ? filter : filter.substring(0, metaIndex);
            if (metaIndex >= 0) {
                try {
                    meta = Integer.parseInt(filter.substring(metaIndex + 1));
                } catch (NumberFormatException ignored) {}
            }

            int separator = beforeMeta.indexOf(':');
            String modid = separator < 0 ? ""
                : beforeMeta.substring(0, separator)
                    .trim();
            String name = beforeMeta.substring(separator + 1)
                .trim();
            Predicate<String> modMatcher = modid.isEmpty() ? value -> true : makeNameMatcher(modid);
            Predicate<String> nameMatcher = name.isEmpty() ? value -> true : makeNameMatcher(name);
            final Integer fMeta = meta;

            return (stack) -> {
                if (ItemUtil.isStackInvalid(stack)) return false;
                if (fMeta != null && stack.getItemDamage() != fMeta) return false;

                String rl = Item.itemRegistry.getNameForObject(stack.getItem());
                if (rl == null || rl.isEmpty()) return false;

                String modIDPart;
                String namePart;
                int colonIndex = rl.indexOf(':');
                if (colonIndex >= 0) {
                    modIDPart = rl.substring(0, colonIndex);
                    namePart = rl.substring(colonIndex + 1);
                } else {
                    modIDPart = "minecraft";
                    namePart = rl;
                }

                return modMatcher.test(modIDPart) && nameMatcher.test(namePart);
            };

        } catch (PatternSyntaxException e) {
            AELog.debug("Failed to parse item filter: " + filter, e);
            return null;
        }
    }

    public static boolean matchesRegexOrWildcard(String value, String pattern) {
        return makeNameMatcher(pattern).test(value);
    }

    public static Predicate<String> makeNameMatcher(String pattern) {
        Predicate<String> matcher = NAME_MATCHERS.getIfPresent(pattern);
        if (matcher != null) return matcher;

        if (pattern.equals("*")) {
            matcher = value -> true;
        } else {
            boolean regex = REGEX_SPECIAL_CHARACTERS.matcher(pattern)
                .matches();
            if (regex || pattern.indexOf('*') >= 0) {
                Pattern compiled = Pattern.compile(regex ? pattern : pattern.replace("*", ".*"));
                matcher = value -> compiled.matcher(value)
                    .matches();
            } else {
                matcher = value -> value.equalsIgnoreCase(pattern);
            }
        }
        NAME_MATCHERS.put(pattern, matcher);
        return matcher;
    }

    public static class ItemListMatcher implements Predicate<IAEItemStack> {

        public final Map<ItemRef, Boolean> cache = new ConcurrentHashMap<>();
        public final Predicate<ItemStack> matcher;

        public ItemListMatcher(Predicate<ItemStack> matcher) {
            this.matcher = matcher;
        }

        @Override
        public boolean test(IAEItemStack t) {
            if (t == null) return false;
            ItemRef key = new ItemRef(t);
            Boolean result = cache.get(key);
            if (result != null) return result;
            boolean matched = matcher.test(t.getItemStack());
            Boolean previous = cache.putIfAbsent(key, matched);
            return previous == null ? matched : previous;
        }
    }

    public static class ItemRef {

        public final Item item;
        public final int meta;
        public final int hash;

        public ItemRef(IAEItemStack stack) {
            this.item = stack.getItem();
            this.meta = stack.getItemDamage();
            this.hash = item.hashCode() ^ meta;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (!(obj instanceof ItemRef other)) return false;
            return other.item == this.item && other.meta == this.meta;
        }
    }
}
