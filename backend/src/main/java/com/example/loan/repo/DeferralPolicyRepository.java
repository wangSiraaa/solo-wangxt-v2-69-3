package com.example.loan.repo;

import com.example.loan.domain.DeferralPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeferralPolicyRepository extends JpaRepository<DeferralPolicy, Long> {

    List<DeferralPolicy> findAllByOrderByUpdatedAtDesc();
}
