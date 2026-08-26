package com.unicconnect.rmi.server;

import java.util.UUID;

/**
 * Request-scoped authenticated identity for the current RMI call. Facades set
 * it after CallerContext verification; the shared-core access port reads it
 * to resolve HOD/lecturer rights exactly like SecurityContextHolder does in
 * the API tier.
 */
public final class RmiCurrentUserHolder {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private RmiCurrentUserHolder() {}

    public static void set(UUID userId) { CURRENT.set(userId); }

    public static UUID requireUserId() {
        UUID id = CURRENT.get();
        if (id == null) {
            throw new com.unicconnect.exception.ValidationException("Not authenticated");
        }
        return id;
    }

    public static void clear() { CURRENT.remove(); }
}
