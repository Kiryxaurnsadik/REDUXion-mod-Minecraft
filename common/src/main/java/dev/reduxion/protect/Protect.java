package dev.reduxion.protect;

import com.google.gson.*;
import dev.reduxion.core.J;
import dev.reduxion.model.CubeModel;
import dev.reduxion.pack.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * Protected ".reduxp" packs: models are baked, textures re-atlased, the result is compressed, cut into
 * fixed-size chunks and each chunk is encrypted with AES-256-GCM under hashed file names.
 */
public final class Protect {
    private Protect() {}

    public static final class WrongPassword extends Exception {
        public WrongPassword() { super("wrong password"); }
    }

    public static final int MIN_PASSWORD = 8;
    private static final int CHUNK = 8192, ITERATIONS = 150_000, MAGIC = 0x52445850;
    private static final SecureRandom RNG = new SecureRandom();

    // The embedded key is split and only assembled at run time. This is obfuscation, not secrecy.
    private static final int[] KA = {0x5d3a91c7, 0x1be402aa, 0x77c0de31, 0x0a55f19e};
    private static final int[] KB = {0x93e1b6f4, 0x48ad7203, 0xe6195bcd, 0x2f80c476};
    private static final long KC = 0x6a09e667f3bcc908L;

    private static byte[] embedded() {
        ByteBuffer b = ByteBuffer.allocate(40);
        for (int i = 0; i < 4; i++) {
            b.putInt(KA[i] ^ Integer.rotateLeft(KB[3 - i], i * 7 + 3));
            b.putInt(KB[i] + KA[(i + 1) & 3] * 31);
        }
        b.putLong(KC ^ ((long) KA[2] << 32 | (KB[1] & 0xFFFFFFFFL)));
        return b.array();
    }

    private static byte[] hmac(byte[] key, byte[]... data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        for (byte[] d : data) mac.update(d);
        return mac.doFinal();
    }

    private static byte[] deriveKey(byte[] salt, char[] password) throws GeneralSecurityException {
        byte[] base = hmac(embedded(), salt);
        byte[] second;
        if (password != null) {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            second = f.generateSecret(new PBEKeySpec(password, salt, ITERATIONS, 256)).getEncoded();
        } else {
            second = "open".getBytes(StandardCharsets.UTF_8);
        }
        return hmac(base, second);
    }

    private static String chunkName(byte[] key, String label) throws GeneralSecurityException {
        byte[] h = hmac(key, ("n:" + label).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder("d/");
        for (int i = 0; i < 16; i++) sb.append(String.format("%02x", h[i]));
        return sb.toString();
    }

    private static byte[] seal(byte[] key, byte[] plain, String aad) throws GeneralSecurityException {
        byte[] nonce = new byte[12];
        RNG.nextBytes(nonce);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        byte[] ct = c.doFinal(plain);
        byte[] out = new byte[12 + ct.length];
        System.arraycopy(nonce, 0, out, 0, 12);
        System.arraycopy(ct, 0, out, 12, ct.length);
        return out;
    }

    private static byte[] open(byte[] key, byte[] data, String aad) throws GeneralSecurityException {
        if (data == null || data.length < 28) throw new AEADBadTagException();
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, data, 0, 12));
        c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        return c.doFinal(data, 12, data.length - 12);
    }

    /** Exports a compiled open pack as a protected pack. */
    public static void export(ReduxPack pack, Path file, char[] password) throws IOException {
        if (pack.isProtected()) throw new IOException("protected packs cannot be re-exported");
        if (password != null && password.length < MIN_PASSWORD) throw new IOException("password is too short");
        try {
            PackCompiler.compile(pack);
            // 1. bake + scramble
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DataOutputStream o = new DataOutputStream(new DeflaterOutputStream(raw, new Deflater(9)))) {
                o.writeInt(MAGIC);
                JsonArray rules = new JsonArray();
                for (Rule r : pack.rules) {
                    JsonObject ro = r.toJson(true);
                    ro.remove("id");
                    rules.add(ro);
                }
                byte[] rj = J.COMPACT.toJson(rules).getBytes(StandardCharsets.UTF_8);
                o.writeInt(rj.length);
                o.write(rj);
                o.writeInt(pack.models.size());
                for (Map.Entry<String, CubeModel> e : pack.models.entrySet()) {
                    o.writeUTF(e.getKey());
                    ModelCodec.write(o, Scrambler.scramble(e.getValue()));
                }
                o.writeInt(pack.images.size());
                for (Map.Entry<String, byte[]> e : pack.images.entrySet()) {
                    byte[] png = dev.reduxion.model.Png.clean(e.getValue());
                    o.writeUTF(e.getKey());
                    o.writeInt(png.length);
                    o.write(png);
                }
            }
            byte[] payload = raw.toByteArray();
            // 2. chunk + encrypt
            byte[] salt = new byte[16];
            RNG.nextBytes(salt);
            byte[] key = deriveKey(salt, password);
            int count = (payload.length + CHUNK - 1) / CHUNK;
            Map<String, byte[]> chunks = new TreeMap<>();
            for (int i = 0; i < count; i++) {
                byte[] plain = new byte[CHUNK];
                RNG.nextBytes(plain);
                System.arraycopy(payload, i * CHUNK, plain, 0, Math.min(CHUNK, payload.length - i * CHUNK));
                chunks.put(chunkName(key, "c" + i), seal(key, plain, "c" + i));
            }
            byte[] index = new byte[CHUNK];
            RNG.nextBytes(index);
            ByteBuffer.wrap(index).putInt(MAGIC).putInt(payload.length).putInt(count);
            chunks.put(chunkName(key, "index"), seal(key, index, "index"));
            int decoys = 2 + RNG.nextInt(4);
            for (int i = 0; i < decoys; i++) {
                byte[] junk = new byte[CHUNK + 28];
                RNG.nextBytes(junk);
                byte[] nm = new byte[16];
                RNG.nextBytes(nm);
                StringBuilder sb = new StringBuilder("d/");
                for (byte b : nm) sb.append(String.format("%02x", b));
                chunks.put(sb.toString(), junk);
            }
            // 3. container
            Map<String, byte[]> z = new LinkedHashMap<>();
            Manifest m = pack.manifest;
            JsonObject mo = m.toJson();
            mo.addProperty("protected", true);
            if (password != null) mo.addProperty("password", true);
            mo.addProperty("salt", Base64.getEncoder().encodeToString(salt));
            mo.addProperty("rules", pack.rules.size());
            z.put("redux.json", J.bytes(mo));
            if (pack.icon != null) z.put("icon.png", pack.icon);
            z.putAll(chunks);
            PackIO.writeZip(file, z);
        } catch (GeneralSecurityException e) {
            throw new IOException("encryption failed: " + e.getMessage(), e);
        }
    }

    /** Decrypts a protected pack into {@code pack}. */
    public static void unlock(ReduxPack pack, Map<String, byte[]> zip, JsonObject manifest, char[] password)
            throws IOException, WrongPassword {
        try {
            byte[] salt = Base64.getDecoder().decode(J.str(manifest, "salt", ""));
            byte[] key = deriveKey(salt, pack.manifest.hasPassword ? password : null);
            byte[] index;
            try {
                index = open(key, zip.get(chunkName(key, "index")), "index");
            } catch (GeneralSecurityException e) {
                if (pack.manifest.hasPassword) throw new WrongPassword();
                throw new IOException("pack is damaged or was made by an incompatible version");
            }
            ByteBuffer ib = ByteBuffer.wrap(index);
            if (ib.getInt() != MAGIC) throw new IOException("bad index");
            int length = ib.getInt(), count = ib.getInt();
            if (length < 0 || count < 0 || count > 65536 || (long) count * CHUNK < length) throw new IOException("bad index");
            byte[] payload = new byte[length];
            for (int i = 0; i < count; i++) {
                byte[] plain = open(key, zip.get(chunkName(key, "c" + i)), "c" + i);
                System.arraycopy(plain, 0, payload, i * CHUNK, Math.min(CHUNK, length - i * CHUNK));
            }
            pack.rules.clear();
            pack.models.clear();
            pack.images.clear();
            try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(payload)))) {
                if (in.readInt() != MAGIC) throw new IOException("bad payload");
                byte[] rj = new byte[in.readInt()];
                in.readFully(rj);
                int n = 0;
                for (JsonElement e : JsonParser.parseString(new String(rj, StandardCharsets.UTF_8)).getAsJsonArray()) {
                    Rule r = Rule.fromJson(e.getAsJsonObject());
                    r.id = "r" + (++n);
                    pack.rules.add(r);
                }
                int nm = in.readInt();
                for (int i = 0; i < nm; i++) {
                    String k = in.readUTF();
                    pack.models.put(k, ModelCodec.read(in));
                }
                int ni = in.readInt();
                for (int i = 0; i < ni; i++) {
                    String k = in.readUTF();
                    byte[] png = new byte[in.readInt()];
                    in.readFully(png);
                    pack.images.put(k, png);
                }
            }
            pack.locked = false;
        } catch (GeneralSecurityException e) {
            throw new IOException("pack is damaged");
        }
    }

    /** Light-weight local obfuscation for remembered passwords (not a real secret store). */
    public static String wrapLocal(String text) {
        try {
            byte[] key = hmac(embedded(), "local".getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(seal(key, text.getBytes(StandardCharsets.UTF_8), "local"));
        } catch (GeneralSecurityException e) {
            return "";
        }
    }

    public static String unwrapLocal(String data) {
        try {
            byte[] key = hmac(embedded(), "local".getBytes(StandardCharsets.UTF_8));
            return new String(open(key, Base64.getDecoder().decode(data), "local"), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
