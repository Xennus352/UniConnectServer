package com.unicconnect.repository;

import com.unicconnect.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {
    Optional<Student> findByRollNo(String rollNo);
    boolean existsByRollNo(String rollNo);
    Optional<Student> findByUser_UserId(UUID userId);
    List<Student> findByMajor_MajorId(UUID majorId);
    List<Student> findBySemester_SemesterId(UUID semesterId);
    List<Student> findBySection_SectionId(UUID sectionId);
    List<Student> findByTerm_TermId(UUID termId);
    boolean existsByUser_UserId(UUID userId);
}