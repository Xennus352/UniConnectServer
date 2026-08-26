package com.unicconnect.rmi.server;

import com.unicconnect.entity.LobbyStatus;
import com.unicconnect.entity.Staff;
import com.unicconnect.exception.BusinessRuleException;
import com.unicconnect.repository.StaffPositionAssignmentRepository;
import com.unicconnect.repository.StaffRepository;
import com.unicconnect.repository.TimetableLobbyMemberRepository;
import com.unicconnect.repository.TimetableLobbyRepository;
import com.unicconnect.service.port.TimetableAccessPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * RMI-tier implementation of the shared timetable access rules. Mirrors
 * HodAccessService (active HOD+LECTURER) and the lobby draft rule against
 * the SAME database, driven by {@link RmiCurrentUserHolder} instead of the
 * HTTP SecurityContext.
 */
@Component
public class ServerTimetableAccessPort implements TimetableAccessPort {

    private final StaffRepository staffRepository;
    private final StaffPositionAssignmentRepository positionRepository;
    private final TimetableLobbyRepository lobbyRepository;
    private final TimetableLobbyMemberRepository lobbyMemberRepository;

    public ServerTimetableAccessPort(StaffRepository staffRepository,
                                     StaffPositionAssignmentRepository positionRepository,
                                     TimetableLobbyRepository lobbyRepository,
                                     TimetableLobbyMemberRepository lobbyMemberRepository) {
        this.staffRepository = staffRepository;
        this.positionRepository = positionRepository;
        this.lobbyRepository = lobbyRepository;
        this.lobbyMemberRepository = lobbyMemberRepository;
    }

    @Override public Staff requireHod() {
        return currentHod().orElseThrow(() -> new BusinessRuleException("Only HOD lecturers can perform this action"));
    }

    @Override public Optional<Staff> currentHod() {
        Staff staff = staffRepository.findByUser_UserId(RmiCurrentUserHolder.requireUserId())
                .orElseThrow(() -> new BusinessRuleException("Only staff can perform this action"));
        Set<String> active = activePositions(staff);
        return active.contains("HOD") && active.contains("LECTURER")
                ? Optional.of(staff) : Optional.empty();
    }

    @Override public void requireSharedDraftAccess(UUID generationId) {
        if (!canAccessSharedDraft(generationId)) {
            throw new BusinessRuleException("You do not have access to this shared draft timetable");
        }
    }

    @Override public boolean canAccessSharedDraft(UUID generationId) {
        if (currentHod().isPresent()) return true;
        Boolean memberOfOpenLobby = lobbyRepository.findByGeneration_GenerationId(generationId)
                .filter(l -> l.getStatus() == LobbyStatus.OPEN)
                .map(l -> lobbyMemberRepository.findByLobby_LobbyIdAndStaff_StaffId(
                        l.getLobbyId(), callerStaffId()).isPresent())
                .orElse(Boolean.FALSE);
        return memberOfOpenLobby;
    }

    @Override public void requireEditLockOwnership(UUID generationId) {
        throw new BusinessRuleException(
                "Timetable editing runs in the Spring Boot API tier only");
    }

    // -------- helpers --------

    private UUID callerStaffId() {
        return staffRepository.findByUser_UserId(RmiCurrentUserHolder.requireUserId())
                .map(Staff::getStaffId)
                .orElseThrow(() -> new BusinessRuleException("Only staff can perform this action"));
    }

    private Set<String> activePositions(Staff staff) {
        Set<String> out = new HashSet<>();
        LocalDate today = LocalDate.now();
        for (var pa : positionRepository.findByStaff_StaffId(staff.getStaffId())) {
            if (pa.getStartDate() != null && pa.getStartDate().isAfter(today)) continue;
            if (pa.getEndDate() != null && pa.getEndDate().isBefore(today)) continue;
            if (pa.getPosition() != null && pa.getPosition().getPositionName() != null) {
                out.add(pa.getPosition().getPositionName());
            }
        }
        return out;
    }
}
