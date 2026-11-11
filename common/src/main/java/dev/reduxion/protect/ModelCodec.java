package dev.reduxion.protect;

import dev.reduxion.model.*;
import java.io.*;
import java.util.Map;

/** Compact binary form of a baked model: no names, no groups, no source structure. */
final class ModelCodec {
    private ModelCodec() {}

    static void write(DataOutputStream o, CubeModel m) throws IOException {
        o.writeBoolean(m.vanillaParent != null);
        if (m.vanillaParent != null) o.writeUTF(m.vanillaParent);
        o.writeBoolean(m.guiLight3d);
        o.writeShort(m.layers.size());
        for (int l : m.layers) o.writeShort(l);
        o.writeShort(m.textures.size());
        for (CubeModel.Tex t : m.textures) {
            o.writeInt(t.png.length); o.write(t.png);
            o.writeShort(t.frames);
            byte[] mc = t.mcmeta == null ? new byte[0] : t.mcmeta;
            o.writeShort(mc.length); o.write(mc);
        }
        o.writeInt(m.cubes.size());
        for (CubeModel.Cube c : m.cubes) {
            for (float f : c.from) o.writeFloat(f);
            for (float f : c.to) o.writeFloat(f);
            for (float f : c.rot.m) o.writeFloat(f);
            for (float f : c.t) o.writeFloat(f);
            o.writeBoolean(c.shade);
            int mask = 0;
            for (int i = 0; i < 6; i++) if (c.faces[i] != null) mask |= 1 << i;
            o.writeByte(mask);
            for (CubeModel.Face f : c.faces) {
                if (f == null) continue;
                for (float u : f.uv) o.writeFloat(u);
                o.writeShort(f.texture);
                o.writeShort(f.rotation);
                o.writeShort(f.tint);
            }
        }
        o.writeByte(m.display.size());
        for (Map.Entry<String, float[]> e : m.display.entrySet()) {
            o.writeUTF(e.getKey());
            for (float f : e.getValue()) o.writeFloat(f);
        }
    }

    static CubeModel read(DataInputStream in) throws IOException {
        CubeModel m = new CubeModel();
        if (in.readBoolean()) m.vanillaParent = in.readUTF();
        m.guiLight3d = in.readBoolean();
        int nl = in.readUnsignedShort();
        for (int i = 0; i < nl; i++) m.layers.add(in.readUnsignedShort());
        int nt = in.readUnsignedShort();
        for (int i = 0; i < nt; i++) {
            int len = in.readInt();
            if (len < 0 || len > (64 << 20)) throw new IOException("bad data");
            byte[] png = new byte[len];
            in.readFully(png);
            int frames = in.readUnsignedShort();
            int mcl = in.readUnsignedShort();
            byte[] mc = new byte[mcl];
            in.readFully(mc);
            int[] wh = Png.size(png);
            if (wh == null) { png = Png.missing(); wh = new int[]{16, 16}; }
            CubeModel.Tex tex = new CubeModel.Tex(png, wh[0], wh[1]);
            if (frames > 1 && mcl > 0) { tex.frames = frames; tex.mcmeta = mc; }
            m.textures.add(tex);
        }
        int nc = in.readInt();
        if (nc < 0 || nc > 100000) throw new IOException("bad data");
        for (int i = 0; i < nc; i++) {
            CubeModel.Cube c = new CubeModel.Cube();
            c.from = new float[]{in.readFloat(), in.readFloat(), in.readFloat()};
            c.to = new float[]{in.readFloat(), in.readFloat(), in.readFloat()};
            for (int k = 0; k < 9; k++) c.rot.m[k] = in.readFloat();
            c.t = new float[]{in.readFloat(), in.readFloat(), in.readFloat()};
            c.shade = in.readBoolean();
            int mask = in.readUnsignedByte();
            for (int f = 0; f < 6; f++) {
                if ((mask & (1 << f)) == 0) continue;
                float[] uv = {in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat()};
                CubeModel.Face face = new CubeModel.Face(uv, in.readUnsignedShort(), in.readShort());
                face.tint = in.readShort();
                if (face.texture >= Math.max(1, nt)) face.texture = 0;
                c.faces[f] = face;
            }
            m.cubes.add(c);
        }
        int nd = in.readUnsignedByte();
        for (int i = 0; i < nd; i++) {
            String k = in.readUTF();
            float[] v = new float[9];
            for (int j = 0; j < 9; j++) v[j] = in.readFloat();
            m.display.put(k, v);
        }
        if (m.textures.isEmpty()) m.textures.add(new CubeModel.Tex(Png.missing(), 16, 16));
        return m;
    }
}
