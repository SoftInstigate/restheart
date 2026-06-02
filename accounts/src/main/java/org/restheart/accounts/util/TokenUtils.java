package org.restheart.accounts.util;

import org.bson.BsonDateTime;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Stateless utility methods for token generation, password hashing and
 * expiry checks. Has no dependency on RESTHeart APIs.
 */
public final class TokenUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final int BCRYPT_LOG_ROUNDS = 12;

    private TokenUtils() {
        // utility class — no instances
    }

    /**
     * Generates a cryptographically secure random token.
     *
     * @return 64-character lowercase hex string (32 bytes from {@link SecureRandom})
     */
    public static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    /**
     * Hashes a plain-text password with BCrypt (log rounds = 12).
     *
     * @param plainPassword the password to hash
     * @return the BCrypt hash string
     */
    public static String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_LOG_ROUNDS));
    }

    /**
     * Verifies a plain-text password against a stored BCrypt hash.
     *
     * @param plain  the candidate password
     * @param hashed the stored BCrypt hash
     * @return {@code true} if the password matches the hash
     */
    public static boolean checkPassword(String plain, String hashed) {
        return BCrypt.checkpw(plain, hashed);
    }

    /**
     * Checks whether a token has expired based on its creation time and a TTL.
     *
     * @param createdAt the {@link BsonDateTime} stored alongside the token
     * @param ttlHours  the maximum validity period in hours
     * @return {@code true} if the current time is past {@code createdAt + ttlHours}
     */
    public static boolean isExpired(BsonDateTime createdAt, int ttlHours) {
        Instant expiresAt = Instant.ofEpochMilli(createdAt.getValue())
                                   .plusSeconds((long) ttlHours * 3600);
        return Instant.now().isAfter(expiresAt);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
