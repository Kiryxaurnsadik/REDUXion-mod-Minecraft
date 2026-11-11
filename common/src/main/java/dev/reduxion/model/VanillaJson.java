package dev.reduxion.model;

import com.google.gson.*;
import dev.reduxion.core.J;
import java.util.List;

/** Writes a {@link CubeModel} as a vanilla JSON model. */
public final class VanillaJson {
    private VanillaJson() {}

    /**
     * @param texIds  resource ids of the model textures (same order as model.textures), e.g. "reduxion:item/t_ab12"
     * @param euler   true: write free x/y/z rotations (1.21.11); false: only single-axis rotations are representable
     * @param slots   display slots supported by the target version
     */
    public static JsonObject write(CubeModel m, List<String> texIds, boolean euler, int slots) {
        JsonObject root = new JsonObject();
        JsonObject tex = new JsonObject();
        if (m.isFlat()) {
            root.addProperty("parent", m.vanillaParent);
            for (int i = 0; i < m.layers.size(); i++) tex.addProperty("layer" + i, texIds.get(m.layers.get(i)));
            if (tex.size() > 0) root.add("textures", tex);
        } else {
            for (int i = 0; i < texIds.size(); i++) tex.addProperty(String.valueOf(i), texIds.get(i));
            if (!texIds.isEmpty()) tex.addProperty("particle", texIds.get(0));
            root.add("textures", tex);
            root.addProperty("gui_light", m.guiLight3d ? "side" : "front");
            JsonArray els = new JsonArray();
            for (CubeModel.Cube c : m.cubes) {
                JsonObject el = new JsonObject();
                // rotate about the transformed centre so any rigid transform is representable
                float[] ctr = {(c.from[0] + c.to[0]) / 2, (c.from[1] + c.to[1]) / 2, (c.from[2] + c.to[2]) / 2};
                float[] rc = c.rot.apply(ctr[0], ctr[1], ctr[2]);
                float[] nc = {rc[0] + c.t[0], rc[1] + c.t[1], rc[2] + c.t[2]};
                float[] d = {nc[0] - ctr[0], nc[1] - ctr[1], nc[2] - ctr[2]};
                float[] from = {c.from[0] + d[0], c.from[1] + d[1], c.from[2] + d[2]};
                float[] to = {c.to[0] + d[0], c.to[1] + d[1], c.to[2] + d[2]};
                for (int i = 0; i < 3; i++) { from[i] = clamp(from[i]); to[i] = clamp(to[i]); }
                el.add("from", J.arr(from));
                el.add("to", J.arr(to));
                if (!c.rot.isIdentity()) {
                    JsonObject r = new JsonObject();
                    r.add("origin", J.arr(nc));
                    float[] single = c.rot.singleAxis();
                    if (single != null && (!euler || isVanillaAngle(single[1]))) {
                        r.addProperty("axis", new String[]{"x", "y", "z"}[(int) single[0]]);
                        r.addProperty("angle", J.round(single[1]));
                    } else if (euler) {
                        float[] e = toEuler(c.rot);
                        r.addProperty("x", J.round(e[0]));
                        r.addProperty("y", J.round(e[1]));
                        r.addProperty("z", J.round(e[2]));
                    }
                    if (r.size() > 1) el.add("rotation", r);
                }
                if (!c.shade) el.addProperty("shade", false);
                JsonObject faces = new JsonObject();
                for (int f = 0; f < 6; f++) {
                    CubeModel.Face face = c.faces[f];
                    if (face == null) continue;
                    JsonObject fo = new JsonObject();
                    fo.add("uv", J.arr(face.uv[0] * 16, face.uv[1] * 16, face.uv[2] * 16, face.uv[3] * 16));
                    fo.addProperty("texture", "#" + face.texture);
                    if (face.rotation != 0) fo.addProperty("rotation", ((face.rotation % 360) + 360) % 360);
                    if (face.tint >= 0) fo.addProperty("tintindex", face.tint);
                    faces.add(CubeModel.FACE_NAMES[f], fo);
                }
                el.add("faces", faces);
                els.add(el);
            }
            root.add("elements", els);
        }
        if (!m.display.isEmpty()) {
            JsonObject disp = new JsonObject();
            int n = 0;
            for (String slot : CubeModel.DISPLAY_SLOTS) {
                if (n++ >= slots) break;
                float[] d = m.display.get(slot);
                if (d == null) continue;
                JsonObject o = new JsonObject();
                o.add("rotation", J.arr(d[0], d[1], d[2]));
                o.add("translation", J.arr(d[3], d[4], d[5]));
                o.add("scale", J.arr(d[6], d[7], d[8]));
                disp.add(slot, o);
            }
            root.add("display", disp);
        }
        return root;
    }

    private static boolean isVanillaAngle(float a) { return true; }
    private static float clamp(float v) { return Math.max(-16f, Math.min(32f, v)); }

    /** Decomposes R = Rz * Ry * Rx into degrees {x,y,z}. */
    public static float[] toEuler(Mat3 r) {
        float[] m = r.m;
        double sy = -m[6];
        double x, y, z;
        if (Math.abs(sy) < 0.9999999) {
            y = Math.asin(sy);
            x = Math.atan2(m[7], m[8]);
            z = Math.atan2(m[3], m[0]);
        } else {
            y = sy > 0 ? Math.PI / 2 : -Math.PI / 2;
            z = 0;
            x = Math.atan2(sy > 0 ? m[1] : -m[1], m[4]);
        }
        return new float[]{(float) Math.toDegrees(x), (float) Math.toDegrees(y), (float) Math.toDegrees(z)};
    }
}
