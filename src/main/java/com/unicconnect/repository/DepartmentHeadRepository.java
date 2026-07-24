package com.unicconnect.repository;

import com.unicconnect.model.DepartmentHead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DepartmentHeadRepository extends JpaRepository<DepartmentHead, Long> {
    Optional<DepartmentHead> findByDepartmentId(Long departmentId);
    Optional<DepartmentHead> findByTeacherId(Long teacherId);
}