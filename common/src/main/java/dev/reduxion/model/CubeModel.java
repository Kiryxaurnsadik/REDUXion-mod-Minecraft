package dev.reduxion.model;

import java.util.*;

/**
 * Version-independent model representation produced by the converters.
 * Coordinates are in Minecraft model units (0..16 = one block), UVs are normalized 0..1.
 */
public final class CubeModel {
    public static final String[] DISPLAY_SLOTS = {
        "thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand",
        "head", "ground", "fixed", "gui", "on_shelf"};

    public static final class Tex {
        public byte[] png;
        public int width, height;
        /** Animation ".mcmeta" JSON bytes for the game atlas, or null for a still texture. */
        public byte[] mcmeta;
        /** Number of vertical frames (1 = still). */
        public int frames = 1;
        public Tex(byte[] png, int w, int h) { this.png = png; this.width = w; this.height = h; }
        public boolean animated() { return mcmeta != null && frames > 1; }
        /** Logical frame height used for UV normalization (one frame for an animated strip). */
        public int frameHeight() { return animated() ? height / frames : height; }
    }

    public static final class Face {
        /** u1,v1,u2,v2 normalized 0..1 */
        public float[] uv;
        public int texture;
        public int rotation;
        public int tint = -1;
        public Face(float[] uv, int texture, int rotation) { this.uv = uv; this.texture = texture; this.rotation = rotation; }
    }

    public static final class Cube {
        public float[] from, to;
        /** World transform: p' = rot * p + t */
        public Mat3 rot = Mat3.identity();
        public float[] t = {0, 0, 0};
        public boolean shade = true;
        /** north, east, south, west, up, down */
        public final Face[] faces = new Face[6];
    }

    public static final String[] FACE_NAMES = {"north", "east", "south", "west", "up", "down"};

    public final List<Tex> textures = new ArrayList<>();
    public final List<Cube> cubes = new ArrayList<>();
    /** slot -> {rx,ry,rz, tx,ty,tz, sx,sy,sz} */
    public final Map<String, float[]> display = new LinkedHashMap<>();
    /**
     * For flat items: vanilla parent such as "minecraft:item/generated" or "minecraft:item/handheld";
     * {@link #layers} then lists the texture indices of layer0, layer1...
     */
    public String vanillaParent;
    public final List<Integer> layers = new ArrayList<>();
    public boolean guiLight3d = true;
    public int skippedMeshes;

    public boolean isFlat() { return vanillaParent != null; }

    /**
     * Returns a model whose cube textures are padded to multiples of 16 px so that stitching them into the
     * game atlas never lowers the atlas mip level. UVs are rescaled accordingly.
     */
    public CubeModel padded() {
        if (isFlat()) return this;
        boolean need = false;
        // animated strips keep their exact height (whole frames); only still textures are padded
        for (Tex t : textures) if (!t.animated() && (t.width % 16 != 0 || t.height % 16 != 0)) need = true;
        if (!need) return this;
        CubeModel out = new CubeModel();
        out.guiLight3d = guiLight3d;
        display.forEach((k, v) -> out.display.put(k, v.clone()));
        float[][] scale = new float[textures.size()][];
        for (int i = 0; i < textures.size(); i++) {
            Tex t = textures.get(i);
            if (t.animated()) { out.textures.add(t); scale[i] = new float[]{1, 1}; continue; }
            int nw = (t.width + 15) / 16 * 16, nh = (t.height + 15) / 16 * 16;
            if (nw == t.width && nh == t.height) { out.textures.add(t); scale[i] = new float[]{1, 1}; continue; }
            Png.Image src = Png.decode(t.png);
            int[] px = new int[nw * nh];
            for (int y = 0; y < src.h && y < nh; y++) System.arraycopy(src.argb, y * src.w, px, y * nw, Math.min(src.w, nw));
            out.textures.add(new Tex(Png.encode(new Png.Image(nw, nh, px)), nw, nh));
            scale[i] = new float[]{src.w / (float) nw, src.h / (float) nh};
        }
        for (Cube c : cubes) {
            Cube n = new Cube();
            n.from = c.from; n.to = c.to; n.rot = c.rot; n.t = c.t; n.shade = c.shade;
            for (int f = 0; f < 6; f++) {
                Face face = c.faces[f];
                if (face == null) continue;
                float[] s = scale[Math.min(face.texture, scale.length - 1)];
                Face nf = new Face(new float[]{face.uv[0] * s[0], face.uv[1] * s[1], face.uv[2] * s[0], face.uv[3] * s[1]}, face.texture, face.rotation);
                nf.tint = face.tint;
                n.faces[f] = nf;
            }
            out.cubes.add(n);
        }
        return out;
    }

    public static float[] defaultDisplay() { return new float[]{0,0,0, 0,0,0, 1,1,1}; }

    public boolean usesFreeRotation() {
        for (Cube c : cubes) {
            float[] s = c.rot.singleAxis();
            if (s == null) return true;
            float a = s[1];
            if (Math.abs(a) > 45.001f) return true;
            float step = a / 22.5f;
            if (Math.abs(step - Math.round(step)) > 1e-3f) return true;
        }
        return false;
    }
}
