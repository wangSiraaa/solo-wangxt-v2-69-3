package com.example.loan.repo;

import com.example.loan.domain.DeferralSimulationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeferralSimulationRecordRepository extends JpaRepository<DeferralSimulationRecord, Long> {

    List<DeferralSimulationRecord> findAllByOrderByCreatedAtDesc();
}
