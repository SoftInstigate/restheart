/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.polyglot.spi;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.graalvm.polyglot.Value;

/**
 * The {@code crypto} utility of the SDK: digests, HMAC, random bytes, UUID and a constant-time
 * compare.
 *
 * <p>This is what a webhook receiver needs: Stripe, GitHub and Twilio sign their payloads with
 * HMAC, and no pure JavaScript npm package computes one without Node's {@code crypto}.
 * Digests and HMACs are returned as lowercase hex; {@code encode} converts to base64 when a
 * provider wants that.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RhCrypto {
    private static final HexFormat HEX = HexFormat.of();
    private static final SecureRandom RANDOM = new SecureRandom();

    public String sha256(Value data) {
        return digest("SHA-256", data);
    }

    public String sha512(Value data) {
        return digest("SHA-512", data);
    }

    public String sha1(Value data) {
        return digest("SHA-1", data);
    }

    public String md5(Value data) {
        return digest("MD5", data);
    }

    private static String digest(String algorithm, Value data) {
        try {
            return HEX.formatHex(MessageDigest.getInstance(algorithm).digest(GuestValues.toBytes(data)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String hmacSha256(Value key, Value data) {
        return hmac("HmacSHA256", key, data);
    }

    public String hmacSha512(Value key, Value data) {
        return hmac("HmacSHA512", key, data);
    }

    public String hmacSha1(Value key, Value data) {
        return hmac("HmacSHA1", key, data);
    }

    private static String hmac(String algorithm, Value key, Value data) {
        try {
            var mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(GuestValues.toBytes(key), algorithm));
            return HEX.formatHex(mac.doFinal(GuestValues.toBytes(data)));
        } catch (Exception e) {
            throw new IllegalArgumentException("cannot compute " + algorithm + ": " + e.getMessage(), e);
        }
    }

    /**
     * @param n how many bytes
     * @return that many random bytes from a {@link SecureRandom}
     */
    public byte[] randomBytes(int n) {
        var bytes = new byte[Math.max(0, n)];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * @param n how many bytes
     * @return that many random bytes, as lowercase hex (twice as many characters)
     */
    public String randomHex(int n) {
        return HEX.formatHex(randomBytes(n));
    }

    /**
     * @return a random (version 4) UUID
     */
    public String uuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * Compares two values in constant time, the way a signature must be compared so that the
     * time taken says nothing about how many leading bytes matched.
     *
     * @param a a string or bytes
     * @param b a string or bytes
     * @return whether they are equal
     */
    public boolean timingSafeEqual(Value a, Value b) {
        return MessageDigest.isEqual(GuestValues.toBytes(a), GuestValues.toBytes(b));
    }
}
