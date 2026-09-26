package com.example.loan.repo;

import com.example.loan.domain.DefermentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DefermentRecordRepository extends JpaRepository<DefermentRecord, Long> {

    List<DefermentRecord> findAllByOrderByCreatedAtDesc();
}
