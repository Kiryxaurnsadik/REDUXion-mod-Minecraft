package dev.reduxion.model;

import com.google.gson.*;
import dev.reduxion.core.J;
import java.nio.charset.StandardCharsets;

/** Builds and validates Minecraft texture ".mcmeta" animation sections. */
public final class Anim {
    private Anim() {}

    /** @param frameOrder space/comma separated frame indices, or "" for the default order */
    public static byte[] mcmeta(int frameTime, boolean interpolate, String frameOrder) {
        JsonObject a = new JsonObject();
        if (frameTime > 1) a.addProperty("frametime", frameTime);
        if (interpolate) a.addProperty("interpolate", true);
        if (frameOrder != null && !frameOrder.isBlank()) {
            JsonArray frames = new JsonArray();
            for (String tok : frameOrder.split("[,\\s]+")) {
                if (tok.isBlank()) continue;
                try { frames.add(Integer.parseInt(tok.trim())); } catch (NumberFormatException ignored) {}
            }
            if (frames.size() > 0) a.add("frames", frames);
        }
        JsonObject root = new JsonObject();
        root.add("animation", a);
        return J.COMPACT.toJson(root).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Reads an existing ".mcmeta" supplied with a texture. Returns the normalized bytes and the frame count
     * implied by the image, or null when there is no valid animation section.
     * @param pngW,pngH the texture image size
     */
    public static Parsed fromMcmeta(byte[] mcmeta, int pngW, int pngH) {
        if (mcmeta == null || pngW <= 0 || pngH <= pngW || pngH % pngW != 0) return null;
        try {
            JsonObject root = J.parse(mcmeta);
            JsonObject anim = J.obj(root, "animation");
            if (anim == null) return null;
            int frameH = J.i(anim, "height", pngW);
            if (frameH <= 0 || pngH % frameH != 0) frameH = pngW;
            int frames = pngH / frameH;
            if (frames < 2) return null;
            // re-serialize to drop anything odd and keep the useful keys
            byte[] clean = mcmeta(J.i(anim, "frametime", 1), J.b(anim, "interpolate", false),
                framesToString(J.arr(anim, "frames")));
            return new Parsed(clean, frames);
        } catch (Exception e) {
            return null;
        }
    }

    private static String framesToString(JsonArray a) {
        if (a == null) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonElement e : a) {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) sb.append(e.getAsInt()).append(' ');
        }
        return sb.toString().trim();
    }

    public static final class Parsed {
        public final byte[] mcmeta;
        public final int frames;
        Parsed(byte[] mcmeta, int frames) { this.mcmeta = mcmeta; this.frames = frames; }
    }
}
