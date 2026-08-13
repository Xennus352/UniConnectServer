package com.unicconnect.repository;

import com.unicconnect.entity.ResultBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResultBatchRepository extends JpaRepository<ResultBatch, UUID> {
    List<ResultBatch> findByTerm_TermId(UUID termId);
    List<ResultBatch> findBySemester_SemesterId(UUID semesterId);
    List<ResultBatch> findByExamType_ExamTypeId(UUID examTypeId);
    java.util.Optional<ResultBatch> findByTerm_TermIdAndExamType_ExamTypeIdAndSemester_SemesterId(
            UUID termId, UUID examTypeId, UUID semesterId);
}