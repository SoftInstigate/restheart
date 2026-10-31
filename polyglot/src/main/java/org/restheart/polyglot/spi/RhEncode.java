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

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import org.graalvm.polyglot.Value;

/**
 * The {@code encode} utility of the SDK: base64, base64url, hex, UTF-8 and URL encoding.
 *
 * <p>GraalJS has no {@code Buffer}, no {@code TextEncoder} and no {@code atob}: these are the
 * conversions a plugin needs to sign a payload, read a header or build a query string. Every
 * {@code to*} method accepts a string (encoded as UTF-8) or bytes (a {@code byte[]}, a
 * {@code Uint8Array} or an {@code ArrayBuffer}); every {@code from*} method returns bytes.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RhEncode {
    private static final HexFormat HEX = HexFormat.of();

    public String toBase64(Value data) {
        return Base64.getEncoder().encodeToString(GuestValues.toBytes(data));
    }

    public byte[] fromBase64(String s) {
        return Base64.getDecoder().decode(s);
    }

    public String toBase64Url(Value data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(GuestValues.toBytes(data));
    }

    public byte[] fromBase64Url(String s) {
        return Base64.getUrlDecoder().decode(s);
    }

    public String toHex(Value data) {
        return HEX.formatHex(GuestValues.toBytes(data));
    }

    public byte[] fromHex(String s) {
        return HEX.parseHex(s);
    }

    /**
     * @param s a string
     * @return its UTF-8 bytes
     */
    public byte[] toUtf8(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param data bytes
     * @return the string they encode in UTF-8
     */
    public String fromUtf8(Value data) {
        return new String(GuestValues.toBytes(data), StandardCharsets.UTF_8);
    }

    /**
     * @param s a string
     * @return the string percent-encoded for a query string value
     */
    public String urlEncode(String s) {
        return s == null ? "" : URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /**
     * @param s a percent-encoded string
     * @return the string decoded
     */
    public String urlDecode(String s) {
        return s == null ? "" : URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
