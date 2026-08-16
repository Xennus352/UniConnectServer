package com.unicconnect.repository;

import com.unicconnect.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {
    List<Attendance> findBySession_SessionId(UUID sessionId);
    List<Attendance> findByStudent_StudentId(UUID studentId);
    boolean existsBySession_SessionIdAndStudent_StudentId(UUID sessionId, UUID studentId);

    @Query("SELECT COUNT(DISTINCT a.student.studentId) FROM Attendance a WHERE a.session.sessionId = :sessionId")
    long countStudentsBySession(@Param("sessionId") UUID sessionId);

    long countBySession_Schedule_Generation_GenerationId(UUID generationId);
}