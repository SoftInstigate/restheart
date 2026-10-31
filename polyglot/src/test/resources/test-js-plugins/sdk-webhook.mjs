import { defineService, crypto, encode, UnauthorizedError } from "restheart:sdk";

// reference plugin: an HMAC-signed webhook receiver, the thing no pure JavaScript package can do
const SECRET = "it-webhook-secret";

export default defineService({
    name: "sdkWebhook",
    description: "conformance: crypto.hmacSha256 and crypto.timingSafeEqual on a signed payload",
    uri: "/sdk/webhook",
    secured: false,
    contentKind: "string",

    handle(req, res) {
        const signature = req.headers["x-signature"] ?? "";
        const expected = "sha256=" + crypto.hmacSha256(SECRET, req.content ?? "");

        if (!crypto.timingSafeEqual(signature, expected)) {
            throw new UnauthorizedError("bad signature");
        }

        res.json({
            verified: true,
            sha256: crypto.sha256(req.content),
            uuidLength: crypto.uuid().length,
            randomHex: crypto.randomHex(8).length,
            randomBase64: encode.toBase64(crypto.randomBytes(3)).length
        });
    }
});
