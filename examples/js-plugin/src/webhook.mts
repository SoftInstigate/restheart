import { defineService, crypto, logger, config, UnauthorizedError } from "restheart:sdk";

// an HMAC-signed webhook receiver, the way Stripe, GitHub and Twilio sign their payloads.
// This needs crypto: no pure JavaScript npm package computes an HMAC without Node's crypto.
//
// configuration, in restheart.yml:
//
//   webhookService:
//     secret: the-signing-secret-of-the-provider
export default defineService({
    name: "webhookService",
    description: "verifies the HMAC signature of a webhook payload",
    uri: "/webhook",
    secured: false,
    matchPolicy: "EXACT",
    contentKind: "string", // the raw body is what the signature covers

    handle(req, res) {
        if (!req.isPost()) {
            res.statusCode = 405;
            return;
        }

        const secret = config.secret as string | undefined;

        if (!secret) {
            throw new Error("webhookService: secret is not configured");
        }

        const signature = req.headers["x-signature"] ?? "";
        const expected = "sha256=" + crypto.hmacSha256(secret, req.content ?? "");

        // constant time: the time taken must not say how many bytes matched
        if (!crypto.timingSafeEqual(signature, expected)) {
            throw new UnauthorizedError("bad signature");
        }

        const event = JSON.parse(req.content ?? "{}");
        logger.info("webhook event {}", event.type);

        res.json({ received: true });
    },
});
