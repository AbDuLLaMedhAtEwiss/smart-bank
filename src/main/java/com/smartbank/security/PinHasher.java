package com.smartbank.security;

import com.smartbank.exception.ValidationException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** PIN hashing with PBKDF2-HMAC-SHA256 and a random per-account salt. Stored as "saltHex:hashHex". */
public final class PinHasher {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private PinHasher() {
    }

    public static void validateFormat(char[] pin) {
        if (pin == null || pin.length < 4 || pin.length > 6) {
            throw new ValidationException("PIN must be 4 to 6 digits");
        }
        for (char c : pin) {
            if (c < '0' || c > '9') {
                throw new ValidationException("PIN must contain digits only");
            }
        }
    }

    public static String hash(char[] pin) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return HEX.formatHex(salt) + ":" + HEX.formatHex(derive(pin, salt));
    }

    public static boolean matches(char[] pin, String stored) {
        try {
            int sep = stored.indexOf(':');
            if (pin == null || sep < 0) {
                return false;
            }
            byte[] salt = HEX.parseHex(stored.substring(0, sep));
            byte[] expected = HEX.parseHex(stored.substring(sep + 1));
            return MessageDigest.isEqual(expected, derive(pin, salt)); // constant-time comparison
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(char[] pin, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(pin, salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 not available", e);
        } finally {
            spec.clearPassword();
        }
    }
}
