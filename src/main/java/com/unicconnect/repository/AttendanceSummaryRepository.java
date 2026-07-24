package com.unicconnect.repository;

import com.unicconnect.model.AttendanceSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendanceSummaryRepository extends JpaRepository<AttendanceSummary, Long> {
    List<AttendanceSummary> findByStudentId(Long studentId);
    Optional<AttendanceSummary> findByStudentIdAndSubjectCode(Long studentId, String subjectCode);
    List<AttendanceSummary> findByIsBelow75True();
}