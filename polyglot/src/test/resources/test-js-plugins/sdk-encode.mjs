import { defineService, encode } from "restheart:sdk";

// reference plugin: the encode utility, every conversion and its inverse
export default defineService({
    name: "sdkEncode",
    description: "conformance: encode base64, base64url, hex, utf8, url",
    uri: "/sdk/encode",
    secured: false,
    contentKind: "json",

    handle(req, res) {
        const s = req.query.s ?? "";
        res.json({
            base64: encode.toBase64(s),
            base64Roundtrip: encode.fromUtf8(encode.fromBase64(encode.toBase64(s))),
            base64url: encode.toBase64Url(s),
            base64urlRoundtrip: encode.fromUtf8(encode.fromBase64Url(encode.toBase64Url(s))),
            hex: encode.toHex(s),
            hexRoundtrip: encode.fromUtf8(encode.fromHex(encode.toHex(s))),
            utf8Length: encode.toUtf8(s).length,
            urlEncoded: encode.urlEncode(s),
            urlRoundtrip: encode.urlDecode(encode.urlEncode(s))
        });
    }
});
