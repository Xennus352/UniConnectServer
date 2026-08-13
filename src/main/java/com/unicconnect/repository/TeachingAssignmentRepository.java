package com.unicconnect.repository;

import com.unicconnect.entity.TeachingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeachingAssignmentRepository extends JpaRepository<TeachingAssignment, UUID> {
    List<TeachingAssignment> findByTerm_TermId(UUID termId);
    List<TeachingAssignment> findByStaff_StaffId(UUID staffId);
    List<TeachingAssignment> findByCourse_CourseId(UUID courseId);
    List<TeachingAssignment> findBySection_SectionId(UUID sectionId);
    Optional<TeachingAssignment> findByTerm_TermIdAndCourse_CourseIdAndSection_SectionId(UUID termId, UUID courseId, UUID sectionId);
    boolean existsByTerm_TermIdAndCourse_CourseIdAndSection_SectionId(UUID termId, UUID courseId, UUID sectionId);
}