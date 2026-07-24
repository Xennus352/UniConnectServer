package com.unicconnect.repository;

import com.unicconnect.model.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
    Optional<StudentProfile> findByStudentIdNumber(String studentIdNumber);
}