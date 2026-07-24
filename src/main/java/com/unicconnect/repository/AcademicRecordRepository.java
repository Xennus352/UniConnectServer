package com.unicconnect.repository;

import com.unicconnect.model.AcademicRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AcademicRecordRepository extends JpaRepository<AcademicRecord, Long> {
    List<AcademicRecord> findByStudentId(Long studentId);
    List<AcademicRecord> findByStudentIdAndAcademicYear(Long studentId, String academicYear);
}