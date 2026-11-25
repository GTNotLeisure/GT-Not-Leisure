package com.science.gtnl.utils.machine.bee;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.util.StatCollector;

import forestry.api.apiculture.EnumBeeType;
import forestry.api.apiculture.IAlleleBeeSpecies;
import forestry.core.utils.StringUtil;

public class BeeSpeciesSearch {

    private static volatile Index index;

    private BeeSpeciesSearch() {}

    private record Entry(String displayName, String text, String compactText) {}

    private record Index(List<IAlleleBeeSpecies> species, long translationRevision, long revision,
        Map<String, Entry> entries, Map<String, IAlleleBeeSpecies> aliases, Set<String> ambiguous) {}

    public static String normalize(String value) {
        if (value == null || value.isEmpty()) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        StringBuilder result = new StringBuilder(normalized.length());
        boolean space = false;
        for (int offset = 0; offset < normalized.length();) {
            int point = normalized.codePointAt(offset);
            offset += Character.charCount(point);
            if (point == 0xA7) {
                if (offset < normalized.length()) offset += Character.charCount(normalized.codePointAt(offset));
                continue;
            }
            if (Character.getType(point) == Character.FORMAT) continue;
            if (!Character.isLetterOrDigit(point)) {
                space = !result.isEmpty();
                continue;
            }
            if (space) result.append(' ');
            result.appendCodePoint(point);
            space = false;
        }
        return result.toString()
            .toLowerCase(Locale.ROOT);
    }

    public static long revision() {
        return current().revision();
    }

    public static String displayName(String uid) {
        if (uid == null || uid.isEmpty()) return "";
        Entry entry = current().entries()
            .get(uid);
        return entry == null ? readable(uid) : entry.displayName();
    }

    public static IAlleleBeeSpecies resolve(String name) {
        Index current = current();
        String key = normalize(name);
        return current.ambiguous()
            .contains(key) ? null
                : current.aliases()
                    .get(key);
    }

    public static List<String> sorted(Collection<String> species) {
        Index current = current();
        List<String> result = new ArrayList<>(species);
        result.sort(Comparator.comparing((String uid) -> {
            Entry entry = current.entries()
                .get(uid);
            return entry == null ? readable(uid) : entry.displayName();
        })
            .thenComparing(uid -> uid));
        return result;
    }

    public static List<String> filter(List<String> sortedSpecies, String query) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) return sortedSpecies;
        String[] terms = normalized.split(" ");
        Index current = current();
        List<String> result = new ArrayList<>();
        for (String uid : sortedSpecies) {
            Entry entry = current.entries()
                .get(uid);
            String text = entry == null ? normalize(uid) : entry.text();
            String compact = entry == null ? text.replace(" ", "") : entry.compactText();
            boolean matches = true;
            for (String term : terms) {
                if (!text.contains(term) && !compact.contains(term)) {
                    matches = false;
                    break;
                }
            }
            if (matches) result.add(uid);
        }
        return result;
    }

    public static String readable(String value) {
        if (value == null || value.isEmpty()) return "";
        String name = value.substring(value.lastIndexOf('.') + 1);
        if (name.regionMatches(true, 0, "SpeciesTE", 0, 9)) name = name.substring(9);
        else if (name.regionMatches(true, 0, "species", 0, 7)) name = name.substring(7);
        return name.isEmpty() ? value : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static Index current() {
        List<IAlleleBeeSpecies> species = BeeBreedingHelper.getRegisteredSpecies();
        long translationRevision = StatCollector.getLastTranslationUpdateTimeInMilliseconds();
        Index previous = index;
        if (previous != null && previous.species() == species && previous.translationRevision() == translationRevision)
            return previous;
        Map<String, Entry> entries = new HashMap<>();
        Map<String, IAlleleBeeSpecies> aliases = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (IAlleleBeeSpecies bee : species) {
            String uid = bee.getUID();
            String translationKey = bee.getUnlocalizedName();
            String name = bee.getName();
            if (name == null || name.isEmpty() || "name".equalsIgnoreCase(name) || name.equals(translationKey))
                name = readable(uid);
            Set<String> names = new HashSet<>();
            names.add(uid);
            names.add(readable(uid));
            names.add(name);
            if (translationKey != null && !translationKey.isEmpty()) {
                names.add(translationKey);
                names.add(readable(translationKey));
                names.add(StatCollector.translateToLocal(translationKey));
                names.add(StatCollector.translateToFallback(translationKey));
                for (EnumBeeType type : List.of(EnumBeeType.DRONE, EnumBeeType.PRINCESS, EnumBeeType.QUEEN)) {
                    String customKey = "bees.custom." + type.getName()
                        + "."
                        + translationKey.replace("bees.species.", "");
                    if (StringUtil.canTranslate(customKey)) names.add(StringUtil.localize(customKey));
                    String grammar = StringUtil.localize("bees.grammar." + type.getName());
                    String typeName = StringUtil.localize("bees.grammar." + type.getName() + ".type");
                    names.add(
                        grammar.replace("%SPECIES", name)
                            .replace("%TYPE", typeName));
                }
            }
            StringBuilder text = new StringBuilder();
            for (String alias : names) {
                String key = normalize(alias);
                if (key.isEmpty()) continue;
                text.append(key)
                    .append(' ');
                IAlleleBeeSpecies old = aliases.putIfAbsent(key, bee);
                if (old != null && !old.getUID()
                    .equals(uid)) ambiguous.add(key);
            }
            String searchable = text.toString();
            entries.put(uid, new Entry(name, searchable, searchable.replace(" ", "")));
        }
        Index next = new Index(
            species,
            translationRevision,
            previous == null ? 1 : previous.revision() + 1,
            Map.copyOf(entries),
            Map.copyOf(aliases),
            Set.copyOf(ambiguous));
        index = next;
        return next;
    }
}
