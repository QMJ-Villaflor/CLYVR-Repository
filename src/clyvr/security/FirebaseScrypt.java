package clyvr.security;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class FirebaseScrypt {
    public static final String PREFIX = "fbscrypt$";
    public static final int ROUNDS = 8;    
    public static final int MEM_COST = 14;  

    private static final String DEFAULT_SIGNER_KEY =
        "jxspr8Ki0RYycVU8zykbdLGjFQ3McFUH0uiiTvC8pVMXAn210wjLNmdZJzxUECKbm0QsEmYUSDzZvpjeJ9WmXA==";
    private static final String DEFAULT_SALT_SEPARATOR = "Bw==";

    private static final SecureRandom RANDOM = new SecureRandom();

    private FirebaseScrypt() {}

    private static byte[] signerKey() {
        String v = System.getenv("CLYVR_HASH_SIGNER_KEY");
        return Base64.getDecoder().decode(v == null || v.isBlank() ? DEFAULT_SIGNER_KEY : v);
    }

    private static byte[] saltSeparator() {
        String v = System.getenv("CLYVR_HASH_SALT_SEPARATOR");
        return Base64.getDecoder().decode(v == null || v.isBlank() ? DEFAULT_SALT_SEPARATOR : v);
    }

    public static boolean isHashed(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    public static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String saltB64 = Base64.getEncoder().encodeToString(salt);
        return PREFIX + saltB64 + "$" + hashRaw(password, saltB64, signerKey(), saltSeparator(), ROUNDS, MEM_COST);
    }

    public static boolean verify(String password, String stored) {
        if (!isHashed(stored)) return false;
        String[] parts = stored.split("\\$");
        if (parts.length != 3) return false;
        String expected = hashRaw(password, parts[1], signerKey(), saltSeparator(), ROUNDS, MEM_COST);
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8));
    }

    public static String hashRaw(String password, String saltB64, byte[] signerKey,
                                 byte[] saltSeparator, int rounds, int memCost) {
        try {
            byte[] userSalt = Base64.getDecoder().decode(saltB64);
            byte[] salt = new byte[userSalt.length + saltSeparator.length];
            System.arraycopy(userSalt, 0, salt, 0, userSalt.length);
            System.arraycopy(saltSeparator, 0, salt, userSalt.length, saltSeparator.length);

            byte[] derived = scrypt(password.getBytes(StandardCharsets.UTF_8), salt, 1 << memCost, rounds, 1, 32);

            Cipher aes = Cipher.getInstance("AES/CTR/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(derived, "AES"), new IvParameterSpec(new byte[16]));
            return Base64.getEncoder().encodeToString(aes.doFinal(signerKey));
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing failed", e);
        }
    }

    static byte[] scrypt(byte[] pass, byte[] salt, int n, int r, int p, int dkLen) throws Exception {
        byte[] b = pbkdf2(pass, salt, 1, p * 128 * r);
        int[] xy = new int[64 * r];
        int[] v = new int[32 * r * n];
        for (int i = 0; i < p; i++) smix(b, i * 128 * r, r, n, v, xy);
        return pbkdf2(pass, b, 1, dkLen);
    }

    private static byte[] pbkdf2(byte[] pass, byte[] salt, int iterations, int dkLen) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(pass.length == 0 ? new byte[1] : pass, "HmacSHA256"));
        if (pass.length == 0) mac = emptyKeyMac();
        byte[] out = new byte[dkLen];
        int blocks = (dkLen + 31) / 32;
        for (int i = 1; i <= blocks; i++) {
            mac.update(salt);
            mac.update(new byte[] {(byte) (i >>> 24), (byte) (i >>> 16), (byte) (i >>> 8), (byte) i});
            byte[] u = mac.doFinal();
            byte[] t = u.clone();
            for (int j = 1; j < iterations; j++) {
                u = mac.doFinal(u);
                for (int k = 0; k < t.length; k++) t[k] ^= u[k];
            }
            System.arraycopy(t, 0, out, (i - 1) * 32, Math.min(32, dkLen - (i - 1) * 32));
        }
        return out;
    }

    private static Mac emptyKeyMac() throws Exception {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(new byte[64], "HmacSHA256"));
        return m;
    }

    private static void smix(byte[] b, int bi, int r, int n, int[] v, int[] xy) {
        int words = 32 * r;
        int[] x = new int[words];
        for (int i = 0; i < words; i++) {
            int o = bi + i * 4;
            x[i] = (b[o] & 0xff) | (b[o + 1] & 0xff) << 8 | (b[o + 2] & 0xff) << 16 | (b[o + 3] & 0xff) << 24;
        }
        int[] tmp = new int[words];
        for (int i = 0; i < n; i++) {
            System.arraycopy(x, 0, v, i * words, words);
            blockMix(x, tmp, r);
        }
        for (int i = 0; i < n; i++) {
            int j = x[(2 * r - 1) * 16] & (n - 1);
            for (int k = 0; k < words; k++) x[k] ^= v[j * words + k];
            blockMix(x, tmp, r);
        }
        for (int i = 0; i < words; i++) {
            int o = bi + i * 4;
            b[o] = (byte) x[i]; b[o + 1] = (byte) (x[i] >>> 8); b[o + 2] = (byte) (x[i] >>> 16); b[o + 3] = (byte) (x[i] >>> 24);
        }
    }

    private static void blockMix(int[] b, int[] y, int r) {
        int[] x = new int[16];
        System.arraycopy(b, (2 * r - 1) * 16, x, 0, 16);
        for (int i = 0; i < 2 * r; i++) {
            for (int k = 0; k < 16; k++) x[k] ^= b[i * 16 + k];
            salsa20_8(x);
            System.arraycopy(x, 0, y, i * 16, 16);
        }
        for (int i = 0; i < r; i++) System.arraycopy(y, (2 * i) * 16, b, i * 16, 16);
        for (int i = 0; i < r; i++) System.arraycopy(y, (2 * i + 1) * 16, b, (i + r) * 16, 16);
    }

    private static void salsa20_8(int[] b) {
        int[] x = b.clone();
        for (int i = 0; i < 8; i += 2) {
            x[4] ^= Integer.rotateLeft(x[0] + x[12], 7);  x[8] ^= Integer.rotateLeft(x[4] + x[0], 9);
            x[12] ^= Integer.rotateLeft(x[8] + x[4], 13); x[0] ^= Integer.rotateLeft(x[12] + x[8], 18);
            x[9] ^= Integer.rotateLeft(x[5] + x[1], 7);   x[13] ^= Integer.rotateLeft(x[9] + x[5], 9);
            x[1] ^= Integer.rotateLeft(x[13] + x[9], 13); x[5] ^= Integer.rotateLeft(x[1] + x[13], 18);
            x[14] ^= Integer.rotateLeft(x[10] + x[6], 7); x[2] ^= Integer.rotateLeft(x[14] + x[10], 9);
            x[6] ^= Integer.rotateLeft(x[2] + x[14], 13); x[10] ^= Integer.rotateLeft(x[6] + x[2], 18);
            x[3] ^= Integer.rotateLeft(x[15] + x[11], 7); x[7] ^= Integer.rotateLeft(x[3] + x[15], 9);
            x[11] ^= Integer.rotateLeft(x[7] + x[3], 13); x[15] ^= Integer.rotateLeft(x[11] + x[7], 18);
            x[1] ^= Integer.rotateLeft(x[0] + x[3], 7);   x[2] ^= Integer.rotateLeft(x[1] + x[0], 9);
            x[3] ^= Integer.rotateLeft(x[2] + x[1], 13);  x[0] ^= Integer.rotateLeft(x[3] + x[2], 18);
            x[6] ^= Integer.rotateLeft(x[5] + x[4], 7);   x[7] ^= Integer.rotateLeft(x[6] + x[5], 9);
            x[4] ^= Integer.rotateLeft(x[7] + x[6], 13);  x[5] ^= Integer.rotateLeft(x[4] + x[7], 18);
            x[11] ^= Integer.rotateLeft(x[10] + x[9], 7); x[8] ^= Integer.rotateLeft(x[11] + x[10], 9);
            x[9] ^= Integer.rotateLeft(x[8] + x[11], 13); x[10] ^= Integer.rotateLeft(x[9] + x[8], 18);
            x[12] ^= Integer.rotateLeft(x[15] + x[14], 7); x[13] ^= Integer.rotateLeft(x[12] + x[15], 9);
            x[14] ^= Integer.rotateLeft(x[13] + x[12], 13); x[15] ^= Integer.rotateLeft(x[14] + x[13], 18);
        }
        for (int i = 0; i < 16; i++) b[i] += x[i];
    }
}
