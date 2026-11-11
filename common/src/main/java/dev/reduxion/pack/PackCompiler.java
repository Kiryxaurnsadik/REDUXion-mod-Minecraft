package dev.reduxion.pack;

import dev.reduxion.model.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Turns the source files of an open pack into models/images keyed by content hash. */
public final class PackCompiler {
    private PackCompiler() {}

    public static void compile(ReduxPack pack) {
        pack.models.clear();
        pack.images.clear();
        pack.warnings.clear();
        for (Rule r : pack.rules) {
            r.modelKey = r.layer1Key = r.layer2Key = null;
            try {
                if (Rule.ITEM.equals(r.target)) compileItem(pack, r);
                else {
                    r.layer1Key = image(pack, r.layer1);
                    r.layer2Key = image(pack, r.layer2);
                    if (r.layer1Key == null && r.layer2Key == null) pack.warnings.add(r.id + ": no texture");
                }
            } catch (Exception e) {
                pack.warnings.add(r.id + ": " + e);
            }
        }
    }

    private static String image(ReduxPack pack, String path) {
        if (path == null || path.isEmpty()) return null;
        byte[] png = pack.files.get(path);
        if (png == null || Png.size(png) == null) { pack.warnings.add("missing texture " + path); return null; }
        String key = "a_" + hash(png);
        pack.images.put(key, png);
        return key;
    }

    private static void compileItem(ReduxPack pack, Rule r) {
        String m = r.model == null ? "" : r.model.trim();
        if (m.startsWith("item:")) return;
        CubeModel model;
        byte[] seed;
        if (m.startsWith("vanilla:")) {
            String ref = m.substring(8);
            model = new CubeModel();
            model.vanillaParent = ref.contains(":") ? ref : "minecraft:" + ref;
            seed = m.getBytes(StandardCharsets.UTF_8);
        } else {
            byte[] data = pack.files.get(m);
            if (data == null) { pack.warnings.add(r.id + ": missing file " + m); return; }
            seed = data;
            String lower = m.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".bbmodel")) {
                BBModelReader.Result res = BBModelReader.read(data);
                model = res.model;
                model.skippedMeshes = res.skippedMeshes;
                if (res.skippedMeshes > 0) pack.warnings.add(r.id + ": " + res.skippedMeshes + " mesh element(s) skipped (only cubes are supported)");
            } else if (lower.endsWith(".png")) {
                model = JsonModelReader.flat(data, mcmetaFor(pack, m), r.handheld);
            } else {
                model = JsonModelReader.read(data, new JsonModelReader.TexSource() {
                    @Override public byte[] png(String ref) { return findTexture(pack, ref); }
                    @Override public byte[] mcmeta(String ref) { return findMcmeta(pack, ref); }
                });
            }
        }
        for (Map.Entry<String, float[]> e : r.display.entrySet()) model.display.put(e.getKey(), e.getValue().clone());
        StringBuilder extra = new StringBuilder(pack.manifest.id).append(r.handheld);
        r.display.forEach((k, v) -> extra.append(k).append(Arrays.toString(v)));
        // textures (and their animation) referenced by json models are part of the identity too
        for (CubeModel.Tex t : model.textures) {
            extra.append(hash(t.png));
            if (t.mcmeta != null) extra.append(new String(t.mcmeta, StandardCharsets.UTF_8));
        }
        r.modelKey = "m_" + hash(seed, extra.toString().getBytes(StandardCharsets.UTF_8));
        pack.models.put(r.modelKey, model);
    }

    private static String[] texTries(String ref) {
        String p = ref;
        int c = p.indexOf(':');
        if (c >= 0) p = p.substring(c + 1);
        if (p.endsWith(".png")) p = p.substring(0, p.length() - 4);
        return new String[]{"textures/" + p + ".png", p + ".png", "textures/" + p.substring(p.lastIndexOf('/') + 1) + ".png"};
    }

    static byte[] findTexture(ReduxPack pack, String ref) {
        for (String t : texTries(ref)) {
            byte[] b = pack.files.get(t);
            if (b != null) return b;
        }
        return null;
    }

    /** Finds the ".mcmeta" that sits next to a texture referenced from a json model. */
    static byte[] findMcmeta(ReduxPack pack, String ref) {
        for (String t : texTries(ref)) {
            byte[] b = mcmetaFor(pack, t);
            if (b != null) return b;
        }
        return null;
    }

    /** Looks up "<pngPath>.mcmeta" for a known texture path. */
    static byte[] mcmetaFor(ReduxPack pack, String pngPath) {
        if (pngPath == null) return null;
        byte[] b = pack.files.get(pngPath + ".mcmeta");
        if (b == null && pngPath.endsWith(".png")) b = pack.files.get(pngPath.substring(0, pngPath.length() - 4) + ".mcmeta");
        return b;
    }

    public static String hash(byte[]... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (byte[] p : parts) md.update(p);
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
