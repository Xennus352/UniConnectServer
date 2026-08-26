package com.unicconnect.service.port;

import com.unicconnect.entity.Staff;
import java.util.Optional;
import java.util.UUID;

/**
 * API-tier access rules injected into the shared timetable services so the
 * SAME classes run in both JVMs.
 *
 * Spring Boot API : delegates to HodAccessService / TimetableLobbyAccessService
 *                   (SecurityContext-backed).
 * RMI Server      : validates the signed CallerContext and re-derives HOD /
 *                   lecturer positions from the database.
 */
public interface TimetableAccessPort {

    /** Active HOD+LECTURER staff member or BusinessRuleException. */
    Staff requireHod();

    /** Active HOD+LECTURER staff member when applicable. */
    Optional<Staff> currentHod();

    /** Lobby rule: caller may act on this shared draft generation. */
    void requireSharedDraftAccess(UUID generationId);

    /** Non-throwing variant of {@link #requireSharedDraftAccess(UUID)}. */
    boolean canAccessSharedDraft(UUID generationId);

    /** Editing lock ownership check for schedule create/update/swap/delete. */
    void requireEditLockOwnership(UUID generationId);
}
