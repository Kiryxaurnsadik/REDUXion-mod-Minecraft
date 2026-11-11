package dev.reduxion.rules;

import dev.reduxion.pack.Rule;
import java.util.*;
import java.util.function.Supplier;

/** Immutable lookup table built from the enabled packs. Replaced as a whole when anything changes. */
public final class RuleTable {
    /** Lazily reads what the server sent for a stack. */
    public interface Probe {
        Integer cmd();
        List<String> cmdStrings();
        List<Boolean> cmdFlags();
        String itemModel();
        String assetId();
    }

    public static final class Entry {
        public final Rule rule;
        public final int packOrder;
        /** Vanilla item id to borrow the look from, or null. */
        public final String borrowItem;
        public Entry(Rule rule, int packOrder) {
            this.rule = rule;
            this.packOrder = packOrder;
            this.borrowItem = rule.model != null && rule.model.startsWith("item:") ? Rule.normId(rule.model.substring(5)) : null;
        }
    }

    private static final Entry[] NONE = new Entry[0];
    public static final RuleTable EMPTY = new RuleTable(List.of());

    private final Map<String, Entry[]> items = new HashMap<>(), armor = new HashMap<>();
    private final Entry[] armorAny;
    private final Entry[] trims;
    private final Map<Object, Entry[]> itemCache = new IdentityHashMap<>(), armorCache = new IdentityHashMap<>();
    public final int size;

    public RuleTable(List<Entry> entries) {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.<Entry>comparingInt(e -> -e.rule.specificity()).thenComparingInt(e -> e.packOrder));
        Map<String, List<Entry>> it = new HashMap<>(), ar = new HashMap<>();
        List<Entry> any = new ArrayList<>(), tr = new ArrayList<>();
        for (Entry e : sorted) {
            switch (e.rule.target) {
                case Rule.ARMOR -> {
                    if (e.rule.item.isEmpty()) any.add(e);
                    else ar.computeIfAbsent(e.rule.item, k -> new ArrayList<>()).add(e);
                }
                case Rule.TRIM -> tr.add(e);
                default -> { if (!e.rule.item.isEmpty()) it.computeIfAbsent(e.rule.item, k -> new ArrayList<>()).add(e); }
            }
        }
        it.forEach((k, v) -> items.put(k, v.toArray(NONE)));
        // wildcard armor rules are checked together with the item-specific ones
        for (Map.Entry<String, List<Entry>> e : ar.entrySet()) {
            List<Entry> l = new ArrayList<>(e.getValue());
            l.addAll(any);
            l.sort(Comparator.<Entry>comparingInt(x -> -x.rule.specificity()).thenComparingInt(x -> x.packOrder));
            armor.put(e.getKey(), l.toArray(NONE));
        }
        armorAny = any.toArray(NONE);
        trims = tr.toArray(NONE);
        size = entries.size();
    }

    public boolean hasItems() { return !items.isEmpty(); }
    public boolean hasArmor() { return !armor.isEmpty() || armorAny.length > 0; }
    public boolean hasTrims() { return trims.length > 0; }

    /** @param itemKey any object that is identical for equal items (the Item instance) */
    public Entry matchItem(Object itemKey, Supplier<String> id, Probe probe) {
        Entry[] list;
        synchronized (itemCache) {
            list = itemCache.get(itemKey);
            if (list == null) {
                list = items.getOrDefault(id.get(), NONE);
                itemCache.put(itemKey, list);
            }
        }
        return first(list, probe);
    }

    public Entry matchArmor(Object itemKey, Supplier<String> id, Probe probe) {
        Entry[] list;
        synchronized (armorCache) {
            list = armorCache.get(itemKey);
            if (list == null) {
                list = armor.getOrDefault(id.get(), armorAny);
                armorCache.put(itemKey, list);
            }
        }
        return first(list, probe);
    }

    public Entry matchTrim(String pattern, String material) {
        for (Entry e : trims) {
            Rule r = e.rule;
            if (r.trimPattern != null && !r.trimPattern.equals(pattern)) continue;
            if (r.trimMaterial != null && !r.trimMaterial.equals(material)) continue;
            return e;
        }
        return null;
    }

    private static Entry first(Entry[] list, Probe probe) {
        for (Entry e : list) if (matches(e.rule, probe)) return e;
        return null;
    }

    public static boolean matches(Rule r, Probe p) {
        if (r.adapt && (r.cmd != null || r.itemModel != null)) {
            // adaptation: the item is the right one if EITHER identifier matches, so a single rule
            // works whether the server tags it with custom_model_data or with item_model
            Integer c = p.cmd();
            boolean cmdOk = r.cmd != null && c != null && c.intValue() == r.cmd;
            boolean imOk = r.itemModel != null && r.itemModel.equals(p.itemModel());
            if (!cmdOk && !imOk) return false;
        } else {
            if (r.cmd != null) {
                Integer c = p.cmd();
                if (c == null || c.intValue() != r.cmd) return false;
            }
            if (r.itemModel != null && !r.itemModel.equals(p.itemModel())) return false;
        }
        if (r.assetId != null && !r.assetId.equals(p.assetId())) return false;
        if (!r.cmdStrings.isEmpty()) {
            List<String> s = p.cmdStrings();
            if (s.size() < r.cmdStrings.size()) return false;
            for (int i = 0; i < r.cmdStrings.size(); i++) if (!r.cmdStrings.get(i).equals(s.get(i))) return false;
        }
        if (!r.cmdFlags.isEmpty()) {
            List<Boolean> f = p.cmdFlags();
            if (f.size() < r.cmdFlags.size()) return false;
            for (int i = 0; i < r.cmdFlags.size(); i++) if (!r.cmdFlags.get(i).equals(f.get(i))) return false;
        }
        return true;
    }
}
