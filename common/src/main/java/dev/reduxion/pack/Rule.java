package dev.reduxion.pack;

import com.google.gson.*;
import dev.reduxion.core.J;
import java.util.*;

/** One replacement rule, shared by both game versions. */
public final class Rule {
    public static final String ITEM = "item", ARMOR = "armor", TRIM = "trim";

    public String id = "";
    public String target = ITEM;
    // match
    public String item = "";
    public Integer cmd;
    public final List<String> cmdStrings = new ArrayList<>();
    public final List<Boolean> cmdFlags = new ArrayList<>();
    public String itemModel;
    public String assetId;
    public String trimPattern;
    public String trimMaterial;
    /** When true, custom_model_data and item_model are matched as alternatives (adapts to either server style). */
    public boolean adapt;
    /** Optional group name for organising replacements in the studio. */
    public String group = "";
    // result
    /** "models/x.bbmodel", "models/x.json", "textures/x.png", "item:minecraft:diamond_block" or "vanilla:block/diamond_block" */
    public String model = "";
    public boolean handheld;
    public String layer1, layer2;
    public final Map<String, float[]> display = new LinkedHashMap<>();
    public final List<String> versions = new ArrayList<>();
    // filled by the compiler (and stored in protected packs instead of file paths)
    public String modelKey, layer1Key, layer2Key;

    public JsonObject toJson(boolean compiled) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("target", target);
        JsonObject m = new JsonObject();
        if (TRIM.equals(target)) {
            if (trimPattern != null && !trimPattern.isEmpty()) m.addProperty("pattern", trimPattern);
            if (trimMaterial != null && !trimMaterial.isEmpty()) m.addProperty("material", trimMaterial);
        } else {
            m.addProperty("item", item);
        }
        if (cmd != null) m.addProperty("cmd", cmd);
        if (!cmdStrings.isEmpty()) { JsonArray a = new JsonArray(); cmdStrings.forEach(a::add); m.add("cmd_strings", a); }
        if (!cmdFlags.isEmpty()) { JsonArray a = new JsonArray(); cmdFlags.forEach(a::add); m.add("cmd_flags", a); }
        if (itemModel != null && !itemModel.isEmpty()) m.addProperty("item_model", itemModel);
        if (assetId != null && !assetId.isEmpty()) m.addProperty("asset_id", assetId);
        if (adapt) m.addProperty("adapt", true);
        o.add("match", m);
        if (group != null && !group.isEmpty()) o.addProperty("group", group);
        if (ITEM.equals(target)) {
            if (compiled) {
                if (model.startsWith("item:")) o.addProperty("model", model);
                if (modelKey != null) o.addProperty("k", modelKey);
            } else {
                o.addProperty("model", model);
                if (handheld) o.addProperty("handheld", true);
                if (!display.isEmpty()) {
                    JsonObject d = new JsonObject();
                    display.forEach((k, v) -> d.add(k, J.arr(v)));
                    o.add("display", d);
                }
            }
        } else if (compiled) {
            if (layer1Key != null) o.addProperty("k1", layer1Key);
            if (layer2Key != null) o.addProperty("k2", layer2Key);
        } else {
            if (layer1 != null && !layer1.isEmpty()) o.addProperty(TRIM.equals(target) ? "texture" : "layer1", layer1);
            if (layer2 != null && !layer2.isEmpty()) o.addProperty(TRIM.equals(target) ? "texture_leggings" : "layer2", layer2);
        }
        if (!versions.isEmpty()) { JsonArray a = new JsonArray(); versions.forEach(a::add); o.add("versions", a); }
        return o;
    }

    public static Rule fromJson(JsonObject o) {
        Rule r = new Rule();
        r.id = J.str(o, "id", "");
        r.target = J.str(o, "target", ITEM);
        JsonObject m = J.obj(o, "match");
        r.item = normId(J.str(m, "item", ""));
        r.cmd = J.intOrNull(m, "cmd");
        JsonArray cs = J.arr(m, "cmd_strings");
        if (cs != null) for (JsonElement e : cs) r.cmdStrings.add(e.getAsString());
        JsonArray cf = J.arr(m, "cmd_flags");
        if (cf != null) for (JsonElement e : cf) r.cmdFlags.add(e.getAsBoolean());
        r.itemModel = normIdOrNull(J.str(m, "item_model", null));
        r.assetId = normIdOrNull(J.str(m, "asset_id", null));
        r.adapt = J.b(m, "adapt", false);
        r.group = J.str(o, "group", "");
        r.trimPattern = normIdOrNull(J.str(m, "pattern", null));
        r.trimMaterial = normIdOrNull(J.str(m, "material", null));
        r.model = J.str(o, "model", "");
        r.handheld = J.b(o, "handheld", false);
        boolean trim = TRIM.equals(r.target);
        r.layer1 = J.str(o, trim ? "texture" : "layer1", null);
        r.layer2 = J.str(o, trim ? "texture_leggings" : "layer2", null);
        r.modelKey = J.str(o, "k", null);
        r.layer1Key = J.str(o, "k1", null);
        r.layer2Key = J.str(o, "k2", null);
        JsonObject d = J.obj(o, "display");
        if (d != null) for (Map.Entry<String, JsonElement> e : d.entrySet()) {
            float[] v = J.floats(e.getValue(), 9, 0);
            if (!e.getValue().isJsonArray() || e.getValue().getAsJsonArray().size() < 9) { v[6] = v[7] = v[8] = 1; }
            r.display.put(e.getKey(), v);
        }
        JsonArray vs = J.arr(o, "versions");
        if (vs != null) for (JsonElement e : vs) r.versions.add(e.getAsString());
        return r;
    }

    public static String normId(String s) {
        if (s == null) return "";
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return s;
        return s.contains(":") ? s : "minecraft:" + s;
    }
    static String normIdOrNull(String s) { return s == null || s.trim().isEmpty() ? null : normId(s); }

    /** More specific rules win. */
    public int specificity() {
        int s = 0;
        if (itemModel != null) s += 8;
        if (assetId != null) s += 4;
        if (cmd != null) s += 4;
        s += cmdStrings.size() + cmdFlags.size();
        if (trimMaterial != null) s += 2;
        return s;
    }

    public boolean appliesTo(String gameVersion) {
        return versions.isEmpty() || versions.contains(gameVersion);
    }

    /** Short human-readable description of how the rule matches. */
    public String how() {
        if (ARMOR.equals(target)) return "armor";
        if (TRIM.equals(target)) return "trim";
        if (itemModel != null) return "item_model";
        if (cmd != null) return "CMD " + cmd;
        if (!cmdStrings.isEmpty()) return "CMD \"" + cmdStrings.get(0) + "\"";
        return "*";
    }

    public Rule copy() { return fromJson(toJson(false)); }
}
