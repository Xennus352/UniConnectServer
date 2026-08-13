package com.unicconnect.repository;

import com.unicconnect.entity.ClassSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClassSessionRepository extends JpaRepository<ClassSession, UUID> {
    List<ClassSession> findBySchedule_ScheduleId(UUID scheduleId);
    boolean existsBySchedule_ScheduleIdAndSessionDate(UUID scheduleId, java.time.LocalDate sessionDate);
}