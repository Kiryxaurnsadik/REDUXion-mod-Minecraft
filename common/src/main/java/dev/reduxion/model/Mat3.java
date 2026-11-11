package dev.reduxion.model;

/** Minimal 3x3 rotation matrix (row-major). */
public final class Mat3 {
    public final float[] m = {1,0,0, 0,1,0, 0,0,1};

    public static Mat3 identity() { return new Mat3(); }

    public static Mat3 axis(int axis, float deg) {
        Mat3 r = new Mat3();
        double a = Math.toRadians(deg);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        switch (axis) {
            case 0 -> { r.m[4] = c; r.m[5] = -s; r.m[7] = s; r.m[8] = c; }
            case 1 -> { r.m[0] = c; r.m[2] = s; r.m[6] = -s; r.m[8] = c; }
            default -> { r.m[0] = c; r.m[1] = -s; r.m[3] = s; r.m[4] = c; }
        }
        return r;
    }

    /** Blockbench/Minecraft euler order: R = Rz * Ry * Rx (X applied first). */
    public static Mat3 euler(float x, float y, float z) {
        return axis(2, z).mul(axis(1, y)).mul(axis(0, x));
    }

    public Mat3 mul(Mat3 o) {
        Mat3 r = new Mat3();
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                float s = 0;
                for (int k = 0; k < 3; k++) s += m[i * 3 + k] * o.m[k * 3 + j];
                r.m[i * 3 + j] = s;
            }
        return r;
    }

    public float[] apply(float x, float y, float z) {
        return new float[]{
            m[0] * x + m[1] * y + m[2] * z,
            m[3] * x + m[4] * y + m[5] * z,
            m[6] * x + m[7] * y + m[8] * z};
    }

    public boolean isIdentity() {
        float[] id = {1,0,0, 0,1,0, 0,0,1};
        for (int i = 0; i < 9; i++) if (Math.abs(m[i] - id[i]) > 1e-4f) return false;
        return true;
    }

    /** If this is a rotation about a single principal axis, returns {axis, degrees}; otherwise null. */
    public float[] singleAxis() {
        if (isIdentity()) return new float[]{0, 0};
        for (int axis = 0; axis < 3; axis++) {
            int a = axis;
            // the axis column/row must be unit
            if (Math.abs(m[a * 3 + a] - 1f) > 1e-4f) continue;
            int b = (a + 1) % 3, c = (a + 2) % 3;
            float cos, sin;
            if (a == 0) { cos = m[4]; sin = m[7]; }
            else if (a == 1) { cos = m[0]; sin = m[2]; }
            else { cos = m[0]; sin = m[3]; }
            float deg = (float) Math.toDegrees(Math.atan2(sin, cos));
            Mat3 test = axis(a, deg);
            boolean ok = true;
            for (int i = 0; i < 9; i++) if (Math.abs(test.m[i] - m[i]) > 1e-3f) { ok = false; break; }
            if (ok) return new float[]{a, deg};
        }
        return null;
    }
}
