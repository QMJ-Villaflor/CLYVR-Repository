package clyvr.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class FirebaseScrypt {

    public static final String PREFIX = "fbscrypt$";
    private static final int ROUNDS = 8;
    private static final int MEMORY_COST = 14;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] signerKey;
    private final byte[] saltSeparator;

    public FirebaseScrypt(String signerKey, String saltSeparator) {
        this.signerKey = decodeRequired(signerKey, "CLYVR_HASH_SIGNER_KEY");
        this.saltSeparator = decodeRequired(saltSeparator, "CLYVR_HASH_SALT_SEPARATOR");
    }

    public boolean supports(String encoded) {
        return encoded != null && encoded.startsWith(PREFIX);
    }

    public String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String encodedSalt = Base64.getEncoder().encodeToString(salt);
        return PREFIX + encodedSalt + "$" + hashRaw(password, encodedSalt);
    }

    public boolean verify(String password, String encoded) {
        if (password == null || !supports(encoded)) {
            return false;
        }
        String[] parts = encoded.split("\\$", -1);
        if (parts.length != 3 || parts[1].isBlank() || parts[2].isBlank()) {
            return false;
        }
        try {
            byte[] expected = Base64.getDecoder().decode(hashRaw(password, parts[1]));
            byte[] actual = Base64.getDecoder().decode(parts[2]);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String hashRaw(String password, String encodedSalt) {
        try {
            byte[] userSalt = Base64.getDecoder().decode(encodedSalt);
            byte[] salt = new byte[userSalt.length + saltSeparator.length];
            System.arraycopy(userSalt, 0, salt, 0, userSalt.length);
            System.arraycopy(saltSeparator, 0, salt, userSalt.length, saltSeparator.length);
            byte[] derived = scrypt(
                    password.getBytes(StandardCharsets.UTF_8), salt, 1 << MEMORY_COST, ROUNDS, 1, 32);
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(derived, "AES"),
                    new IvParameterSpec(new byte[16]));
            return Base64.getEncoder().encodeToString(cipher.doFinal(signerKey));
        } catch (Exception exception) {
            throw new IllegalStateException("Password hashing failed.", exception);
        }
    }

    private static byte[] scrypt(byte[] password, byte[] salt, int n, int r, int p, int length)
            throws Exception {
        byte[] block = pbkdf2(password, salt, p * 128 * r);
        int[] scratch = new int[64 * r];
        int[] memory = new int[32 * r * n];
        for (int i = 0; i < p; i++) {
            smix(block, i * 128 * r, r, n, memory, scratch);
        }
        return pbkdf2(password, block, length);
    }

    private static byte[] pbkdf2(byte[] password, byte[] salt, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(password.length == 0 ? new byte[1] : password, "HmacSHA256"));
        if (password.length == 0) {
            mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(new byte[64], "HmacSHA256"));
        }
        byte[] result = new byte[length];
        int blocks = (length + 31) / 32;
        for (int i = 1; i <= blocks; i++) {
            mac.update(salt);
            mac.update(new byte[] {
                (byte) (i >>> 24), (byte) (i >>> 16), (byte) (i >>> 8), (byte) i
            });
            byte[] value = mac.doFinal();
            System.arraycopy(value, 0, result, (i - 1) * 32,
                    Math.min(32, length - (i - 1) * 32));
        }
        return result;
    }

    private static void smix(byte[] block, int offset, int r, int n, int[] memory, int[] scratch) {
        int words = 32 * r;
        int[] x = new int[words];
        for (int i = 0; i < words; i++) {
            int position = offset + i * 4;
            x[i] = (block[position] & 0xff)
                    | (block[position + 1] & 0xff) << 8
                    | (block[position + 2] & 0xff) << 16
                    | (block[position + 3] & 0xff) << 24;
        }
        int[] temporary = new int[words];
        for (int i = 0; i < n; i++) {
            System.arraycopy(x, 0, memory, i * words, words);
            blockMix(x, temporary, r);
        }
        for (int i = 0; i < n; i++) {
            int index = x[(2 * r - 1) * 16] & (n - 1);
            for (int k = 0; k < words; k++) {
                x[k] ^= memory[index * words + k];
            }
            blockMix(x, temporary, r);
        }
        for (int i = 0; i < words; i++) {
            int position = offset + i * 4;
            block[position] = (byte) x[i];
            block[position + 1] = (byte) (x[i] >>> 8);
            block[position + 2] = (byte) (x[i] >>> 16);
            block[position + 3] = (byte) (x[i] >>> 24);
        }
    }

    private static void blockMix(int[] block, int[] output, int r) {
        int[] x = new int[16];
        System.arraycopy(block, (2 * r - 1) * 16, x, 0, 16);
        for (int i = 0; i < 2 * r; i++) {
            for (int k = 0; k < 16; k++) {
                x[k] ^= block[i * 16 + k];
            }
            salsa20Eight(x);
            System.arraycopy(x, 0, output, i * 16, 16);
        }
        for (int i = 0; i < r; i++) {
            System.arraycopy(output, i * 32, block, i * 16, 16);
            System.arraycopy(output, i * 32 + 16, block, (i + r) * 16, 16);
        }
    }

    private static void salsa20Eight(int[] block) {
        int[] x = block.clone();
        for (int i = 0; i < 8; i += 2) {
            x[4] ^= Integer.rotateLeft(x[0] + x[12], 7);
            x[8] ^= Integer.rotateLeft(x[4] + x[0], 9);
            x[12] ^= Integer.rotateLeft(x[8] + x[4], 13);
            x[0] ^= Integer.rotateLeft(x[12] + x[8], 18);
            x[9] ^= Integer.rotateLeft(x[5] + x[1], 7);
            x[13] ^= Integer.rotateLeft(x[9] + x[5], 9);
            x[1] ^= Integer.rotateLeft(x[13] + x[9], 13);
            x[5] ^= Integer.rotateLeft(x[1] + x[13], 18);
            x[14] ^= Integer.rotateLeft(x[10] + x[6], 7);
            x[2] ^= Integer.rotateLeft(x[14] + x[10], 9);
            x[6] ^= Integer.rotateLeft(x[2] + x[14], 13);
            x[10] ^= Integer.rotateLeft(x[6] + x[2], 18);
            x[3] ^= Integer.rotateLeft(x[15] + x[11], 7);
            x[7] ^= Integer.rotateLeft(x[3] + x[15], 9);
            x[11] ^= Integer.rotateLeft(x[7] + x[3], 13);
            x[15] ^= Integer.rotateLeft(x[11] + x[7], 18);
            x[1] ^= Integer.rotateLeft(x[0] + x[3], 7);
            x[2] ^= Integer.rotateLeft(x[1] + x[0], 9);
            x[3] ^= Integer.rotateLeft(x[2] + x[1], 13);
            x[0] ^= Integer.rotateLeft(x[3] + x[2], 18);
            x[6] ^= Integer.rotateLeft(x[5] + x[4], 7);
            x[7] ^= Integer.rotateLeft(x[6] + x[5], 9);
            x[4] ^= Integer.rotateLeft(x[7] + x[6], 13);
            x[5] ^= Integer.rotateLeft(x[4] + x[7], 18);
            x[11] ^= Integer.rotateLeft(x[10] + x[9], 7);
            x[8] ^= Integer.rotateLeft(x[11] + x[10], 9);
            x[9] ^= Integer.rotateLeft(x[8] + x[11], 13);
            x[10] ^= Integer.rotateLeft(x[9] + x[8], 18);
            x[12] ^= Integer.rotateLeft(x[15] + x[14], 7);
            x[13] ^= Integer.rotateLeft(x[12] + x[15], 9);
            x[14] ^= Integer.rotateLeft(x[13] + x[12], 13);
            x[15] ^= Integer.rotateLeft(x[14] + x[13], 18);
        }
        for (int i = 0; i < 16; i++) {
            block[i] += x[i];
        }
    }

    private static byte[] decodeRequired(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not configured.");
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(name + " is invalid.", exception);
        }
    }
}
