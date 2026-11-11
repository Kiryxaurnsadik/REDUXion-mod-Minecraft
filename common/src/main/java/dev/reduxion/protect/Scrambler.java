package dev.reduxion.protect;

import dev.reduxion.model.*;
import java.security.SecureRandom;
import java.util.*;

/**
 * Rebuilds the textures of a model into a single atlas made of the face islands in random order.
 * The original texture layout cannot be read back from the result.
 */
final class Scrambler {
    private Scrambler() {}

    private static final class Island {
        int tex, x0, y0, x1, y1, ax, ay;
        int w() { return x1 - x0; }
        int h() { return y1 - y0; }
    }

    static CubeModel scramble(CubeModel src) {
        CubeModel out = new CubeModel();
        out.vanillaParent = src.vanillaParent;
        out.guiLight3d = src.guiLight3d;
        out.layers.addAll(src.layers);
        src.display.forEach((k, v) -> out.display.put(k, v.clone()));
        boolean anyAnimated = false;
        for (CubeModel.Tex t : src.textures) if (t.animated()) anyAnimated = true;
        // flat items and any model with animated textures keep their textures whole (strips must stay intact)
        if (src.isFlat() || src.cubes.isEmpty() || anyAnimated) {
            for (CubeModel.Tex t : src.textures) {
                CubeModel.Tex nt = new CubeModel.Tex(Png.clean(t.png), t.width, t.height);
                nt.frames = t.frames;
                nt.mcmeta = t.mcmeta;
                out.textures.add(nt);
            }
            if (anyAnimated && !src.isFlat()) {
                // preserve geometry and UVs unchanged when we cannot scramble the atlas
                for (CubeModel.Cube c : src.cubes) {
                    CubeModel.Cube n = new CubeModel.Cube();
                    n.from = c.from.clone(); n.to = c.to.clone();
                    System.arraycopy(c.rot.m, 0, n.rot.m, 0, 9);
                    n.t = c.t.clone(); n.shade = c.shade;
                    for (int f = 0; f < 6; f++) {
                        CubeModel.Face face = c.faces[f];
                        if (face == null) continue;
                        CubeModel.Face nf = new CubeModel.Face(face.uv.clone(), face.texture, face.rotation);
                        nf.tint = face.tint;
                        n.faces[f] = nf;
                    }
                    out.cubes.add(n);
                }
            }
            return out;
        }
        List<Png.Image> images = new ArrayList<>();
        for (CubeModel.Tex t : src.textures) images.add(Png.decode(t.png));

        Map<String, Island> islands = new LinkedHashMap<>();
        List<Island[]> perCube = new ArrayList<>();
        for (CubeModel.Cube c : src.cubes) {
            Island[] arr = new Island[6];
            for (int f = 0; f < 6; f++) {
                CubeModel.Face face = c.faces[f];
                if (face == null) continue;
                Png.Image img = images.get(Math.min(face.texture, images.size() - 1));
                float ua = Math.min(face.uv[0], face.uv[2]) * img.w, ub = Math.max(face.uv[0], face.uv[2]) * img.w;
                float va = Math.min(face.uv[1], face.uv[3]) * img.h, vb = Math.max(face.uv[1], face.uv[3]) * img.h;
                Island is = new Island();
                is.tex = Math.min(face.texture, images.size() - 1);
                is.x0 = clamp((int) Math.floor(ua + 1e-4f), 0, img.w - 1);
                is.y0 = clamp((int) Math.floor(va + 1e-4f), 0, img.h - 1);
                is.x1 = clamp((int) Math.ceil(ub - 1e-4f), is.x0 + 1, img.w);
                is.y1 = clamp((int) Math.ceil(vb - 1e-4f), is.y0 + 1, img.h);
                String key = is.tex + ":" + is.x0 + ":" + is.y0 + ":" + is.x1 + ":" + is.y1;
                Island prev = islands.putIfAbsent(key, is);
                arr[f] = prev != null ? prev : is;
            }
            perCube.add(arr);
        }
        List<Island> order = new ArrayList<>(islands.values());
        Collections.shuffle(order, new SecureRandom());
        long area = 0;
        int maxW = 1;
        for (Island is : order) { area += (long) (is.w() + 2) * (is.h() + 2); maxW = Math.max(maxW, is.w() + 2); }
        int aw = Math.max(maxW, (int) Math.ceil(Math.sqrt(area * 1.3)));
        aw = (aw + 15) / 16 * 16;
        int x = 0, y = 0, shelf = 0;
        for (Island is : order) {
            if (x + is.w() + 2 > aw) { x = 0; y += shelf; shelf = 0; }
            is.ax = x + 1;
            is.ay = y + 1;
            x += is.w() + 2;
            shelf = Math.max(shelf, is.h() + 2);
        }
        int ah = Math.max(16, (y + shelf + 15) / 16 * 16);
        int[] px = new int[aw * ah];
        for (Island is : order) {
            Png.Image img = images.get(is.tex);
            for (int yy = -1; yy <= is.h(); yy++)
                for (int xx = -1; xx <= is.w(); xx++) {
                    int sx = clamp(is.x0 + xx, is.x0, is.x1 - 1), sy = clamp(is.y0 + yy, is.y0, is.y1 - 1);
                    px[(is.ay + yy) * aw + is.ax + xx] = img.get(sx, sy);
                }
        }
        out.textures.add(new CubeModel.Tex(Png.encode(new Png.Image(aw, ah, px)), aw, ah));
        for (int ci = 0; ci < src.cubes.size(); ci++) {
            CubeModel.Cube c = src.cubes.get(ci);
            CubeModel.Cube n = new CubeModel.Cube();
            n.from = c.from.clone();
            n.to = c.to.clone();
            System.arraycopy(c.rot.m, 0, n.rot.m, 0, 9);
            n.t = c.t.clone();
            n.shade = c.shade;
            for (int f = 0; f < 6; f++) {
                CubeModel.Face face = c.faces[f];
                if (face == null) continue;
                Island is = perCube.get(ci)[f];
                Png.Image img = images.get(is.tex);
                float[] uv = {
                    (is.ax + face.uv[0] * img.w - is.x0) / aw, (is.ay + face.uv[1] * img.h - is.y0) / ah,
                    (is.ax + face.uv[2] * img.w - is.x0) / aw, (is.ay + face.uv[3] * img.h - is.y0) / ah};
                CubeModel.Face nf = new CubeModel.Face(uv, 0, face.rotation);
                nf.tint = face.tint;
                n.faces[f] = nf;
            }
            out.cubes.add(n);
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
