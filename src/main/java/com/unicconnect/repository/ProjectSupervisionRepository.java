package com.unicconnect.repository;

import com.unicconnect.model.ProjectSupervision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectSupervisionRepository extends JpaRepository<ProjectSupervision, Long> {
    List<ProjectSupervision> findByTeacherId(Long teacherId);
    List<ProjectSupervision> findByDepartmentId(Long departmentId);
}