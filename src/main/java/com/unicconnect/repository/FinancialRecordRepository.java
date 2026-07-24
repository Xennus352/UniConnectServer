package com.unicconnect.repository;

import com.unicconnect.model.FinancialRecord;
import com.unicconnect.model.FinancialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinancialRecordRepository extends JpaRepository<FinancialRecord, Long> {
    List<FinancialRecord> findByUserId(Long userId);
    List<FinancialRecord> findByType(FinancialType type);
}