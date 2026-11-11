package dev.reduxion.model;

import java.util.*;

/** Flat list of textured quads built from a {@link CubeModel}. Positions in model units (0..16). */
public final class Mesh {
    public static final class Quad {
        /** 4 vertices: x,y,z */
        public final float[][] pos = new float[4][];
        /** 4 vertices: u,v normalized to the texture */
        public final float[][] uv = new float[4][];
        public float[] normal;
        public int texture;
        public boolean shade = true;
        public int tint = -1;
        /** 0 north,1 east,2 south,3 west,4 up,5 down (nearest direction of the normal) */
        public int dir;
    }

    public final List<Quad> quads = new ArrayList<>();

    // corner tables per face: indices into {x0,x1},{y0,y1},{z0,z1}; order: top-left, bottom-left, bottom-right, top-right as seen from outside
    private static final int[][][] CORNERS = {
        /* north (-z) */ {{1,1,0},{1,0,0},{0,0,0},{0,1,0}},
        /* east  (+x) */ {{1,1,1},{1,0,1},{1,0,0},{1,1,0}},
        /* south (+z) */ {{0,1,1},{0,0,1},{1,0,1},{1,1,1}},
        /* west  (-x) */ {{0,1,0},{0,0,0},{0,0,1},{0,1,1}},
        /* up    (+y) */ {{0,1,0},{0,1,1},{1,1,1},{1,1,0}},
        /* down  (-y) */ {{0,0,1},{0,0,0},{1,0,0},{1,0,1}},
    };
    private static final float[][] NORMALS = {{0,0,-1},{1,0,0},{0,0,1},{-1,0,0},{0,1,0},{0,-1,0}};

    public static Mesh build(CubeModel model) {
        Mesh mesh = new Mesh();
        if (model.isFlat()) {
            int n = Math.max(1, model.layers.size());
            for (int l = 0; l < n; l++) {
                int tex = model.layers.isEmpty() ? 0 : model.layers.get(l);
                float z0 = 7.5f - l * 0.01f, z1 = 8.5f + l * 0.01f;
                // front (south) and back (north)
                mesh.quads.add(flat(new float[][]{{0,16,z1},{0,0,z1},{16,0,z1},{16,16,z1}}, new float[][]{{0,0},{0,1},{1,1},{1,0}}, 2, tex, l));
                mesh.quads.add(flat(new float[][]{{16,16,z0},{16,0,z0},{0,0,z0},{0,16,z0}}, new float[][]{{1,0},{1,1},{0,1},{0,0}}, 0, tex, l));
            }
            return mesh;
        }
        for (CubeModel.Cube c : model.cubes) {
            float[][] b = {{c.from[0], c.to[0]}, {c.from[1], c.to[1]}, {c.from[2], c.to[2]}};
            for (int f = 0; f < 6; f++) {
                CubeModel.Face face = c.faces[f];
                if (face == null) continue;
                Quad q = new Quad();
                float u1 = face.uv[0], v1 = face.uv[1], u2 = face.uv[2], v2 = face.uv[3];
                float[][] uvs = {{u1, v1}, {u1, v2}, {u2, v2}, {u2, v1}};
                int steps = ((face.rotation / 90) % 4 + 4) % 4;
                for (int i = 0; i < 4; i++) {
                    int[] k = CORNERS[f][i];
                    float[] p = c.rot.apply(b[0][k[0]], b[1][k[1]], b[2][k[2]]);
                    q.pos[i] = new float[]{p[0] + c.t[0], p[1] + c.t[1], p[2] + c.t[2]};
                    // texture rotation: rotating the image clockwise shifts which uv corner each vertex gets
                    q.uv[i] = uvs[((i - steps) % 4 + 4) % 4];
                }
                q.normal = c.rot.apply(NORMALS[f][0], NORMALS[f][1], NORMALS[f][2]);
                q.dir = nearestDir(q.normal);
                q.texture = face.texture;
                q.shade = c.shade;
                q.tint = face.tint;
                mesh.quads.add(q);
            }
        }
        return mesh;
    }

    private static Quad flat(float[][] pos, float[][] uv, int dir, int tex, int tint) {
        Quad q = new Quad();
        for (int i = 0; i < 4; i++) { q.pos[i] = pos[i]; q.uv[i] = uv[i]; }
        q.normal = NORMALS[dir];
        q.dir = dir;
        q.texture = tex;
        q.shade = false;
        q.tint = tint;
        return q;
    }

    public static int nearestDir(float[] n) {
        int best = 0;
        float bd = -2;
        for (int i = 0; i < 6; i++) {
            float d = n[0] * NORMALS[i][0] + n[1] * NORMALS[i][1] + n[2] * NORMALS[i][2];
            if (d > bd) { bd = d; best = i; }
        }
        return best;
    }

    /** {minX,minY,minZ,maxX,maxY,maxZ} */
    public float[] bounds() {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (Quad q : quads) for (float[] p : q.pos) for (int i = 0; i < 3; i++) {
            b[i] = Math.min(b[i], p[i]);
            b[i + 3] = Math.max(b[i + 3], p[i]);
        }
        if (quads.isEmpty()) return new float[]{0, 0, 0, 16, 16, 16};
        return b;
    }
}
