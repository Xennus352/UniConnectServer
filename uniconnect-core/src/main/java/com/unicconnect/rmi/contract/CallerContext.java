package com.unicconnect.rmi.contract;

import java.io.Serializable;
import java.util.UUID;

/**
 * Authenticated caller identity transported over RMI. The Spring Boot API
 * builds it AFTER JWT authentication; the RMI Server verifies the HMAC,
 * freshness and nonce before trusting {@code userId}. Role/position strings
 * are intentionally NOT part of the context — the server always re-reads
 * them from the database.
 */
public record CallerContext(
        UUID userId,
        long epochMillis,
        UUID nonce,
        byte[] signature
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
