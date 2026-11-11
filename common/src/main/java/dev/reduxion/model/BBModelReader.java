package dev.reduxion.model;

import com.google.gson.*;
import dev.reduxion.core.J;
import java.util.*;

/** Reads Blockbench .bbmodel files (Java block/item and generic formats, cubes only). */
public final class BBModelReader {
    private BBModelReader() {}

    public static final class Result {
        public final CubeModel model;
        public int skippedMeshes;
        Result(CubeModel m) { model = m; }
    }

    public static Result read(byte[] data) {
        JsonObject root = J.parse(data);
        CubeModel model = new CubeModel();
        Result res = new Result(model);

        JsonObject resolution = J.obj(root, "resolution");
        float resW = J.f(resolution, "width", 16), resH = J.f(resolution, "height", 16);

        // textures
        Map<String, Integer> texByUuid = new HashMap<>();
        List<float[]> uvSize = new ArrayList<>();
        JsonArray texArr = J.arr(root, "textures");
        if (texArr != null) {
            for (JsonElement te : texArr) {
                JsonObject t = te.getAsJsonObject();
                String src = J.str(t, "source", "");
                byte[] png = null;
                int comma = src.indexOf(',');
                if (src.startsWith("data:") && comma > 0) {
                    try { png = Base64.getDecoder().decode(src.substring(comma + 1)); } catch (Exception ignored) {}
                }
                int[] wh = png == null ? null : Png.size(png);
                if (png == null || wh == null) { png = Png.missing(); wh = new int[]{16, 16}; }
                texByUuid.put(J.str(t, "uuid", "#" + model.textures.size()), model.textures.size());
                CubeModel.Tex tex = new CubeModel.Tex(png, wh[0], wh[1]);
                // animated texture: Blockbench marks it with animation fields and stacks frames vertically
                boolean animated = (J.b(t, "animated", false) || t.has("frame_time")) && wh[1] > wh[0] && wh[1] % wh[0] == 0;
                float frameH = J.f(t, "uv_height", wh[0]);
                if (animated) {
                    tex.frames = Math.max(2, Math.round(wh[1] / Math.max(1f, Math.min(frameH, wh[0]))));
                    if (wh[1] % wh[0] == 0) tex.frames = wh[1] / wh[0];
                    tex.mcmeta = Anim.mcmeta(J.i(t, "frame_time", 1), J.b(t, "frame_interpolate", false), J.str(t, "frame_order", ""));
                    frameH = wh[1] / (float) tex.frames;
                }
                model.textures.add(tex);
                uvSize.add(new float[]{J.f(t, "uv_width", resW), animated ? frameH : J.f(t, "uv_height", resH)});
            }
        }
        if (model.textures.isEmpty()) {
            model.textures.add(new CubeModel.Tex(Png.missing(), 16, 16));
            uvSize.add(new float[]{resW, resH});
        }

        // elements by uuid
        Map<String, JsonObject> elements = new LinkedHashMap<>();
        JsonArray elArr = J.arr(root, "elements");
        if (elArr != null) for (JsonElement e : elArr) {
            JsonObject o = e.getAsJsonObject();
            elements.put(J.str(o, "uuid", UUID.randomUUID().toString()), o);
        }
        // groups (bbmodel 5+ stores them separately)
        Map<String, JsonObject> groups = new HashMap<>();
        JsonArray grArr = J.arr(root, "groups");
        if (grArr != null) for (JsonElement g : grArr) {
            JsonObject o = g.getAsJsonObject();
            groups.put(J.str(o, "uuid", ""), o);
        }

        Set<String> placed = new HashSet<>();
        JsonArray outliner = J.arr(root, "outliner");
        if (outliner != null) {
            walk(outliner, Mat3.identity(), new float[3], true, elements, groups, placed, model, texByUuid, uvSize, res);
        }
        for (Map.Entry<String, JsonObject> e : elements.entrySet()) {
            if (!placed.contains(e.getKey())) addElement(e.getValue(), Mat3.identity(), new float[3], model, texByUuid, uvSize, res);
        }

        JsonObject display = J.obj(root, "display");
        if (display != null) readDisplay(display, model);
        return res;
    }

    static void readDisplay(JsonObject display, CubeModel model) {
        for (String slot : CubeModel.DISPLAY_SLOTS) {
            JsonObject d = J.obj(display, slot);
            if (d == null) continue;
            float[] r = J.floats(d.get("rotation"), 3, 0);
            float[] t = J.floats(d.get("translation"), 3, 0);
            float[] s = J.floats(d.get("scale"), 3, 1);
            model.display.put(slot, new float[]{r[0], r[1], r[2], t[0], t[1], t[2], s[0], s[1], s[2]});
        }
    }

    private static void walk(JsonArray children, Mat3 rot, float[] t, boolean visible, Map<String, JsonObject> elements,
                             Map<String, JsonObject> groups, Set<String> placed, CubeModel model,
                             Map<String, Integer> texByUuid, List<float[]> uvSize, Result res) {
        for (JsonElement c : children) {
            if (c.isJsonPrimitive()) {
                String id = c.getAsString();
                JsonObject el = elements.get(id);
                placed.add(id);
                if (el != null && visible) addElement(el, rot, t, model, texByUuid, uvSize, res);
            } else if (c.isJsonObject()) {
                JsonObject g = c.getAsJsonObject();
                JsonObject def = groups.getOrDefault(J.str(g, "uuid", ""), g);
                boolean vis = visible && J.b(def, "visibility", true) && J.b(def, "export", true);
                float[] origin = J.floats(def.get("origin"), 3, 0);
                float[] r = J.floats(def.get("rotation"), 3, 0);
                Mat3 gr = Mat3.euler(r[0], r[1], r[2]);
                // p' = parent(gr*(p - o) + o)
                float[] go = gr.apply(origin[0], origin[1], origin[2]);
                float[] localT = {origin[0] - go[0], origin[1] - go[1], origin[2] - go[2]};
                Mat3 nr = rot.mul(gr);
                float[] pt = rot.apply(localT[0], localT[1], localT[2]);
                float[] nt = {pt[0] + t[0], pt[1] + t[1], pt[2] + t[2]};
                JsonArray ch = J.arr(g, "children");
                if (ch != null) walk(ch, nr, nt, vis, elements, groups, placed, model, texByUuid, uvSize, res);
            }
        }
    }

    private static void addElement(JsonObject el, Mat3 rot, float[] t, CubeModel model,
                                   Map<String, Integer> texByUuid, List<float[]> uvSize, Result res) {
        String type = J.str(el, "type", "cube");
        if (!J.b(el, "export", true) || !J.b(el, "visibility", true)) return;
        if (!"cube".equals(type)) {
            if ("mesh".equals(type)) res.skippedMeshes++;
            return;
        }
        float[] from = J.floats(el.get("from"), 3, 0);
        float[] to = J.floats(el.get("to"), 3, 0);
        float inflate = J.f(el, "inflate", 0);
        for (int i = 0; i < 3; i++) {
            float a = Math.min(from[i], to[i]) - inflate, b = Math.max(from[i], to[i]) + inflate;
            from[i] = a; to[i] = b;
        }
        float[] origin = J.floats(el.get("origin"), 3, 0);
        float[] r = J.floats(el.get("rotation"), 3, 0);
        Mat3 cr = Mat3.euler(r[0], r[1], r[2]);
        float[] co = cr.apply(origin[0], origin[1], origin[2]);
        float[] localT = {origin[0] - co[0], origin[1] - co[1], origin[2] - co[2]};
        CubeModel.Cube cube = new CubeModel.Cube();
        cube.from = from;
        cube.to = to;
        cube.rot = rot.mul(cr);
        float[] pt = rot.apply(localT[0], localT[1], localT[2]);
        cube.t = new float[]{pt[0] + t[0], pt[1] + t[1], pt[2] + t[2]};
        cube.shade = J.b(el, "shade", true);
        JsonObject faces = J.obj(el, "faces");
        boolean any = false;
        for (int f = 0; f < 6; f++) {
            JsonObject fo = J.obj(faces, CubeModel.FACE_NAMES[f]);
            if (fo == null) continue;
            JsonElement te = fo.get("texture");
            if (te == null || te.isJsonNull()) continue;
            int tex;
            if (te.isJsonPrimitive() && te.getAsJsonPrimitive().isNumber()) tex = te.getAsInt();
            else tex = texByUuid.getOrDefault(te.getAsString(), 0);
            if (tex < 0 || tex >= model.textures.size()) tex = 0;
            float[] uv = J.floats(fo.get("uv"), 4, 0);
            float[] sz = uvSize.get(tex);
            float[] n = {uv[0] / sz[0], uv[1] / sz[1], uv[2] / sz[0], uv[3] / sz[1]};
            CubeModel.Face face = new CubeModel.Face(n, tex, J.i(fo, "rotation", 0));
            face.tint = J.i(fo, "tint", -1);
            cube.faces[f] = face;
            any = true;
        }
        if (any) model.cubes.add(cube);
    }
}
