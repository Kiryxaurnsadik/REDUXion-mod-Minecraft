package dev.reduxion.view;

import dev.reduxion.model.*;
import java.util.*;

/**
 * Tiny CPU rasterizer for the studio preview. Draws from the pack's own textures (never from a game atlas)
 * and burns the "Preview REDUX" watermark into the produced pixels.
 */
public final class SoftRenderer {
    public static final String[] MODES = {"edit", "thirdperson_righthand", "thirdperson_lefthand",
        "firstperson_righthand", "firstperson_lefthand", "head", "ground", "fixed", "gui"};

    private static final class Tri {
        float[][] p = new float[3][];
        float[][] uv = new float[3][];
        Png.Image tex;
        int color;
        float light;
    }

    public float yaw = 35, pitch = 25, zoom = 1f;
    public String mode = "edit";
    public boolean watermark = true;

    private final List<Tri> tris = new ArrayList<>();

    public int[] render(Mesh mesh, List<Png.Image> textures, Map<String, float[]> display, int w, int h) {
        tris.clear();
        float[] center;
        float span;
        boolean orbit = true;
        Xf item;
        switch (mode) {
            case "gui" -> {
                item = Xf.display(display.get("gui"), false);
                center = new float[]{0, 0, 0}; span = 1.25f; orbit = false;
            }
            case "ground" -> {
                item = Xf.tr(0, 0.25f, 0).mul(Xf.display(display.get("ground"), false));
                addBox(-1.5f, -0.05f, -1.5f, 1.5f, 0f, 1.5f, 0xFF5E8A3A, Xf.id());
                center = new float[]{0, 0.3f, 0}; span = 2.2f;
            }
            case "fixed" -> {
                item = Xf.tr(0, 0, 0.03f).mul(Xf.sc(0.5f, 0.5f, 0.5f)).mul(Xf.display(display.get("fixed"), false));
                addBox(-0.375f, -0.375f, -0.0625f, 0.375f, 0.375f, 0f, 0xFF8A6B45, Xf.id());
                addBox(-0.3125f, -0.3125f, 0f, 0.3125f, 0.3125f, 0.004f, 0xFFB79A6B, Xf.id());
                addBox(-1.2f, -1.2f, -0.1f, 1.2f, 1.2f, -0.0625f, 0xFF7A7A7A, Xf.id());
                center = new float[]{0, 0, 0}; span = 1.6f;
            }
            case "head" -> {
                Xf model = Xf.tr(0, 1.501f, 0).mul(Xf.sc(-1, -1, 1));
                mannequin(model);
                item = model.mul(Xf.tr(0, -0.25f, 0)).mul(Xf.rot(1, 180)).mul(Xf.sc(0.625f, -0.625f, -0.625f))
                    .mul(Xf.display(display.get("head"), false));
                center = new float[]{0, 1.55f, 0}; span = 2.0f;
            }
            case "thirdperson_righthand", "thirdperson_lefthand" -> {
                boolean left = mode.endsWith("lefthand");
                Xf model = Xf.tr(0, 1.501f, 0).mul(Xf.sc(-1, -1, 1));
                mannequin(model);
                float side = left ? 1 : -1;
                item = model.mul(Xf.tr(side * 5 / 16f, 2 / 16f, 0)).mul(Xf.rot(0, -90)).mul(Xf.rot(1, 180))
                    .mul(Xf.tr((left ? -1 : 1) / 16f, 0.125f, -0.625f)).mul(Xf.display(display.get(mode), left));
                center = new float[]{0, 0.95f, 0}; span = 2.6f;
            }
            case "firstperson_righthand", "firstperson_lefthand" -> {
                boolean left = mode.endsWith("lefthand");
                item = Xf.tr(left ? -0.56f : 0.56f, -0.52f, -0.72f).mul(Xf.display(display.get(mode), left));
                center = new float[]{0, -0.25f, 0}; span = 2.0f; orbit = false;
            }
            default -> {
                item = Xf.display(null, false);
                grid();
                center = new float[]{0, 0, 0}; span = 2.0f;
            }
        }
        for (Mesh.Quad q : mesh.quads) {
            float[][] p = new float[4][];
            for (int i = 0; i < 4; i++) p[i] = item.apply(q.pos[i]);
            float[] n = item.dir(q.normal);
            Png.Image tex = q.texture >= 0 && q.texture < textures.size() ? textures.get(q.texture) : null;
            addQuad(p, q.uv, tex, 0xFFFFFFFF, q.shade ? n : null);
        }
        return raster(w, h, center, span, orbit);
    }

    private void grid() {
        // floor plate under the 16x16x16 edit volume plus axis lines
        addBox(-0.5f, -0.505f, -0.5f, 0.5f, -0.5f, 0.5f, 0x80303030, Xf.id());
        float t = 0.004f;
        for (int i = 0; i <= 16; i += 4) {
            float c = -0.5f + i / 16f;
            addBox(c - t, -0.5f, -0.5f, c + t, -0.498f, 0.5f, 0xFF707070, Xf.id());
            addBox(-0.5f, -0.5f, c - t, 0.5f, -0.498f, c + t, 0xFF707070, Xf.id());
        }
        addBox(-0.5f, -0.5f, -0.5f, 0.5f, -0.492f, -0.492f, 0xFFE04040, Xf.id());
        addBox(-0.5f, -0.5f, -0.5f, -0.492f, 0.5f, -0.492f, 0xFF40C040, Xf.id());
        addBox(-0.5f, -0.5f, -0.5f, -0.492f, -0.492f, 0.5f, 0xFF4070E0, Xf.id());
    }

    private void mannequin(Xf model) {
        float px = 1 / 16f;
        int skin = 0xFFB9B9B9, body = 0xFF8D8D8D, legs = 0xFF6C6C6C;
        addBox(-4 * px, -8 * px, -4 * px, 4 * px, 0, 4 * px, skin, model);
        addBox(-4 * px, 0, -2 * px, 4 * px, 12 * px, 2 * px, body, model);
        addBox(-8 * px, 0, -2 * px, -4 * px, 12 * px, 2 * px, skin, model);
        addBox(4 * px, 0, -2 * px, 8 * px, 12 * px, 2 * px, skin, model);
        addBox(-4 * px, 12 * px, -2 * px, 0, 24 * px, 2 * px, legs, model);
        addBox(0, 12 * px, -2 * px, 4 * px, 24 * px, 2 * px, legs, model);
        // face marker so the front is recognisable (model front is -z before the flip)
        addBox(-3 * px, -5 * px, -4.1f * px, -1 * px, -4 * px, -4 * px, 0xFF303030, model);
        addBox(1 * px, -5 * px, -4.1f * px, 3 * px, -4 * px, -4 * px, 0xFF303030, model);
        addBox(-1.5f, -0.02f, -1.5f, 1.5f, 0f, 1.5f, 0x60202020, Xf.id());
    }

    private void addBox(float x0, float y0, float z0, float x1, float y1, float z1, int color, Xf xf) {
        float[][] c = new float[8][];
        for (int i = 0; i < 8; i++)
            c[i] = xf.apply(new float[]{(i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1});
        int[][] faces = {{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
        float[][] normals = {{0,0,-1},{0,0,1},{0,-1,0},{0,1,0},{-1,0,0},{1,0,0}};
        for (int f = 0; f < 6; f++) {
            float[][] p = {c[faces[f][0]], c[faces[f][1]], c[faces[f][2]], c[faces[f][3]]};
            addQuad(p, null, null, color, xf.dir(normals[f]));
        }
    }

    private void addQuad(float[][] p, float[][] uv, Png.Image tex, int color, float[] normal) {
        float light = 1f;
        if (normal != null) {
            float ax = Math.abs(normal[0]), az = Math.abs(normal[2]);
            float up = Math.max(0, normal[1]), down = Math.max(0, -normal[1]);
            light = ax * ax * 0.6f + az * az * 0.8f + up * up * 1.0f + down * down * 0.5f;
        }
        int[][] idx = {{0, 1, 2}, {0, 2, 3}};
        for (int[] id : idx) {
            Tri t = new Tri();
            for (int k = 0; k < 3; k++) {
                t.p[k] = p[id[k]];
                t.uv[k] = uv == null ? new float[]{0, 0} : uv[id[k]];
            }
            t.tex = tex;
            t.color = color;
            t.light = light;
            tris.add(t);
        }
    }

    private int[] raster(int w, int h, float[] center, float span, boolean orbit) {
        int[] out = new int[w * h];
        float[] depth = new float[w * h];
        Arrays.fill(depth, -Float.MAX_VALUE);
        // background: subtle vertical gradient
        for (int y = 0; y < h; y++) {
            int g = 0x1A + (int) (0x12 * (1f - y / (float) h));
            int c = 0xD0000000 | (g << 16) | (g << 8) | (g + 6);
            for (int x = 0; x < w; x++) out[y * w + x] = c;
        }
        Xf view = orbit ? Xf.rot(0, pitch).mul(Xf.rot(1, -yaw)) : Xf.id();
        float scale = Math.min(w, h) / span * zoom;
        for (Tri t : tris) {
            float[] sx = new float[3], sy = new float[3], sz = new float[3];
            for (int k = 0; k < 3; k++) {
                float[] p = view.apply(new float[]{t.p[k][0] - center[0], t.p[k][1] - center[1], t.p[k][2] - center[2]});
                sx[k] = w / 2f + p[0] * scale;
                sy[k] = h / 2f - p[1] * scale;
                sz[k] = p[2];
            }
            float area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0]);
            if (Math.abs(area) < 1e-6f) continue;
            int minX = Math.max(0, (int) Math.floor(Math.min(sx[0], Math.min(sx[1], sx[2]))));
            int maxX = Math.min(w - 1, (int) Math.ceil(Math.max(sx[0], Math.max(sx[1], sx[2]))));
            int minY = Math.max(0, (int) Math.floor(Math.min(sy[0], Math.min(sy[1], sy[2]))));
            int maxY = Math.min(h - 1, (int) Math.ceil(Math.max(sy[0], Math.max(sy[1], sy[2]))));
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    float px = x + 0.5f, py = y + 0.5f;
                    float w0 = ((sx[1] - px) * (sy[2] - py) - (sx[2] - px) * (sy[1] - py)) / area;
                    float w1 = ((sx[2] - px) * (sy[0] - py) - (sx[0] - px) * (sy[2] - py)) / area;
                    float w2 = 1 - w0 - w1;
                    if (w0 < -1e-4f || w1 < -1e-4f || w2 < -1e-4f) continue;
                    float z = w0 * sz[0] + w1 * sz[1] + w2 * sz[2];
                    int i = y * w + x;
                    if (z <= depth[i]) continue;
                    int c = t.color;
                    if (t.tex != null) {
                        float u = w0 * t.uv[0][0] + w1 * t.uv[1][0] + w2 * t.uv[2][0];
                        float v = w0 * t.uv[0][1] + w1 * t.uv[1][1] + w2 * t.uv[2][1];
                        int tx = Math.max(0, Math.min(t.tex.w - 1, (int) (u * t.tex.w)));
                        int ty = Math.max(0, Math.min(t.tex.h - 1, (int) (v * t.tex.h)));
                        c = t.tex.get(tx, ty);
                    }
                    int a = c >>> 24;
                    if (a < 26) continue;
                    int r = (int) (((c >> 16) & 0xFF) * t.light), g = (int) (((c >> 8) & 0xFF) * t.light), b = (int) ((c & 0xFF) * t.light);
                    int src = (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
                    if (a >= 250) {
                        out[i] = 0xFF000000 | src;
                        depth[i] = z;
                    } else {
                        out[i] = blend(out[i], src, a);
                    }
                }
            }
        }
        if (watermark) Watermark.stamp(out, w, h);
        return out;
    }

    static int blend(int dst, int srcRgb, int a) {
        int dr = (dst >> 16) & 0xFF, dg = (dst >> 8) & 0xFF, db = dst & 0xFF, da = dst >>> 24;
        int r = (((srcRgb >> 16) & 0xFF) * a + dr * (255 - a)) / 255;
        int g = (((srcRgb >> 8) & 0xFF) * a + dg * (255 - a)) / 255;
        int b = ((srcRgb & 0xFF) * a + db * (255 - a)) / 255;
        int oa = Math.max(da, a);
        return (oa << 24) | (r << 16) | (g << 8) | b;
    }
}
