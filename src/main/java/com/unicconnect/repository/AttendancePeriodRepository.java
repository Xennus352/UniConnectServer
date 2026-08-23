package com.unicconnect.repository;

import com.unicconnect.entity.AttendancePeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AttendancePeriodRepository extends JpaRepository<AttendancePeriod, UUID> {

    List<AttendancePeriod> findByAttendance_AttendanceId(UUID attendanceId);

    @Query("SELECT ap FROM AttendancePeriod ap WHERE ap.attendance.session.sessionId = :sessionId")
    List<AttendancePeriod> findBySessionId(@Param("sessionId") UUID sessionId);

    long deleteByAttendance_AttendanceId(UUID attendanceId);
}
