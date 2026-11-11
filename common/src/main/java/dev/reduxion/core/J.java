package dev.reduxion.core;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;

/** Small JSON helpers (no reflection, safe under obfuscation). */
public final class J {
    private J() {}
    public static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    public static JsonObject parse(byte[] data) {
        return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    public static JsonObject parse(String s) { return JsonParser.parseString(s).getAsJsonObject(); }
    public static byte[] bytes(JsonElement e) { return PRETTY.toJson(e).getBytes(StandardCharsets.UTF_8); }

    public static String str(JsonObject o, String k, String def) {
        JsonElement e = o == null ? null : o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }
    public static Integer intOrNull(JsonObject o, String k) {
        JsonElement e = o == null ? null : o.get(k);
        if (e == null || !e.isJsonPrimitive()) return null;
        try { return (int) Math.round(e.getAsDouble()); } catch (Exception ex) { return null; }
    }
    public static int i(JsonObject o, String k, int def) { Integer v = intOrNull(o, k); return v == null ? def : v; }
    public static float f(JsonObject o, String k, float def) {
        JsonElement e = o == null ? null : o.get(k);
        if (e == null || !e.isJsonPrimitive()) return def;
        try { return e.getAsFloat(); } catch (Exception ex) { return def; }
    }
    public static boolean b(JsonObject o, String k, boolean def) {
        JsonElement e = o == null ? null : o.get(k);
        if (e == null || !e.isJsonPrimitive()) return def;
        try { return e.getAsBoolean(); } catch (Exception ex) { return def; }
    }
    public static JsonObject obj(JsonObject o, String k) {
        JsonElement e = o == null ? null : o.get(k);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }
    public static JsonArray arr(JsonObject o, String k) {
        JsonElement e = o == null ? null : o.get(k);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }
    public static float[] floats(JsonElement e, int n, float def) {
        float[] r = new float[n];
        java.util.Arrays.fill(r, def);
        if (e != null && e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (int i = 0; i < n && i < a.size(); i++) {
                try { r[i] = a.get(i).getAsFloat(); } catch (Exception ignored) {}
            }
        }
        return r;
    }
    public static JsonArray arr(float... v) {
        JsonArray a = new JsonArray();
        for (float x : v) a.add(round(x));
        return a;
    }
    public static Number round(float x) {
        float r = Math.round(x * 10000f) / 10000f;
        if (r == (int) r) return (int) r;
        return r;
    }
}
