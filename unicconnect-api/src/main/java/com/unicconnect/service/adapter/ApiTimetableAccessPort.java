package com.unicconnect.service.adapter;

import com.unicconnect.entity.Staff;
import com.unicconnect.exception.BusinessRuleException;
import com.unicconnect.service.TimetableEditLockService;
import com.unicconnect.service.TimetableLobbyAccessService;
import com.unicconnect.service.HodAccessService;
import com.unicconnect.service.port.TimetableAccessPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** API-side implementation of the shared timetable access rules. */
@Component
public class ApiTimetableAccessPort implements TimetableAccessPort {

    private final HodAccessService hodAccessService;
    private final TimetableLobbyAccessService lobbyAccessService;
    private final TimetableEditLockService editLockService;

    public ApiTimetableAccessPort(HodAccessService hodAccessService,
                                  TimetableLobbyAccessService lobbyAccessService,
                                  TimetableEditLockService editLockService) {
        this.hodAccessService = hodAccessService;
        this.lobbyAccessService = lobbyAccessService;
        this.editLockService = editLockService;
    }

    @Override public Staff requireHod() { return hodAccessService.requireHod(); }

    @Override public Optional<Staff> currentHod() { return hodAccessService.currentHod(); }

    @Override public void requireSharedDraftAccess(UUID generationId) {
        lobbyAccessService.requireSharedDraftAccess(generationId);
    }

    @Override public boolean canAccessSharedDraft(UUID generationId) {
        return lobbyAccessService.canAccessSharedDraft(generationId);
    }

    @Override public void requireEditLockOwnership(UUID generationId) {
        editLockService.requireLockOwned(generationId);
    }
}
