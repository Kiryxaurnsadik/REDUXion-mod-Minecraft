package dev.reduxion.view;

import dev.reduxion.model.Mat3;

/** Affine transform: p' = m * p + t. */
public final class Xf {
    public final float[] m = {1,0,0, 0,1,0, 0,0,1};
    public final float[] t = {0, 0, 0};

    public static Xf id() { return new Xf(); }
    public static Xf tr(float x, float y, float z) { Xf r = new Xf(); r.t[0] = x; r.t[1] = y; r.t[2] = z; return r; }
    public static Xf sc(float x, float y, float z) { Xf r = new Xf(); r.m[0] = x; r.m[4] = y; r.m[8] = z; return r; }
    public static Xf rot(int axis, float deg) { Xf r = new Xf(); System.arraycopy(Mat3.axis(axis, deg).m, 0, r.m, 0, 9); return r; }

    /** this ∘ o (o applied first). */
    public Xf mul(Xf o) {
        Xf r = new Xf();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                float s = 0;
                for (int k = 0; k < 3; k++) s += m[i * 3 + k] * o.m[k * 3 + j];
                r.m[i * 3 + j] = s;
            }
            r.t[i] = m[i * 3] * o.t[0] + m[i * 3 + 1] * o.t[1] + m[i * 3 + 2] * o.t[2] + t[i];
        }
        return r;
    }

    public float[] apply(float[] p) {
        return new float[]{
            m[0] * p[0] + m[1] * p[1] + m[2] * p[2] + t[0],
            m[3] * p[0] + m[4] * p[1] + m[5] * p[2] + t[1],
            m[6] * p[0] + m[7] * p[1] + m[8] * p[2] + t[2]};
    }

    public float[] dir(float[] p) {
        float[] r = {
            m[0] * p[0] + m[1] * p[1] + m[2] * p[2],
            m[3] * p[0] + m[4] * p[1] + m[5] * p[2],
            m[6] * p[0] + m[7] * p[1] + m[8] * p[2]};
        float l = (float) Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]);
        if (l > 1e-6f) { r[0] /= l; r[1] /= l; r[2] /= l; }
        return r;
    }

    /** Vanilla ItemTransform: translate * rotXYZ * scale applied to a model centred at the origin (block units). */
    public static Xf display(float[] d, boolean leftHand) {
        if (d == null) return tr(-0.5f, -0.5f, -0.5f).pre(sc(1 / 16f, 1 / 16f, 1 / 16f));
        float rx = d[0], ry = d[1], rz = d[2], tx = d[3] / 16f;
        if (leftHand) { ry = -ry; rz = -rz; tx = -tx; }
        Xf x = tr(clampT(tx), clampT(d[4] / 16f), clampT(d[5] / 16f))
            .mul(rot(0, rx)).mul(rot(1, ry)).mul(rot(2, rz))
            .mul(sc(clampS(d[6]), clampS(d[7]), clampS(d[8])))
            .mul(tr(-0.5f, -0.5f, -0.5f)).mul(sc(1 / 16f, 1 / 16f, 1 / 16f));
        return x;
    }

    private Xf pre(Xf o) { return this.mul(o); }
    private static float clampT(float v) { return Math.max(-5f, Math.min(5f, v)); }
    private static float clampS(float v) { return Math.max(-4f, Math.min(4f, v)); }
}
