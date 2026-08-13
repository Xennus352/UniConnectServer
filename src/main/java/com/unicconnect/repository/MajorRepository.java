package com.unicconnect.repository;

import com.unicconnect.entity.Major;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MajorRepository extends JpaRepository<Major, UUID> {
    List<Major> findByUnit_UnitId(UUID unitId);
}