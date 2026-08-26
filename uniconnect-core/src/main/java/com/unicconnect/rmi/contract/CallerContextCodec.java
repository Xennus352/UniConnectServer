package com.unicconnect.rmi.contract;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * HMAC-SHA256 signing/verification for {@link CallerContext}. Both JVMs share
 * the secret through configuration (env var RMI_SHARED_SECRET); it never
 * reaches the browser.
 */
public final class CallerContextCodec {

    public static final long MAX_AGE_MILLIS = 120_000L;

    private CallerContextCodec() {}

    public static byte[] sign(byte[] secret, UUID userId, long epochMillis, UUID nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(canonical(userId, epochMillis, nonce));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign caller context", e);
        }
    }

    /** @throws CallerContextRejectedException on any verification failure. */
    public static void verify(byte[] secret, CallerContext ctx) {
        if (ctx == null || ctx.userId() == null || ctx.nonce() == null || ctx.signature() == null) {
            throw new CallerContextRejectedException("Malformed caller context");
        }
        long now = System.currentTimeMillis();
        if (Math.abs(now - ctx.epochMillis()) > MAX_AGE_MILLIS) {
            throw new CallerContextRejectedException("Expired caller context");
        }
        byte[] expected = sign(secret, ctx.userId(), ctx.epochMillis(), ctx.nonce());
        if (!MessageDigest.isEqual(expected, ctx.signature())) {
            throw new CallerContextRejectedException("Invalid caller signature");
        }
    }

    private static byte[] canonical(UUID userId, long epochMillis, UUID nonce) {
        return (userId + "|" + epochMillis + "|" + nonce).getBytes(StandardCharsets.UTF_8);
    }
}
