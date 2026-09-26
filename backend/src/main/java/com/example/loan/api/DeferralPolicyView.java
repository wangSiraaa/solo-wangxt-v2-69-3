package com.example.loan.api;

import java.time.Instant;
import java.util.List;

/**
 * 延期政策视图（不暴露实体）。
 */
public record DeferralPolicyView(Long id,
                                 String name,
                                 List<GraceInterval> intervals,
                                 Instant createdAt,
                                 Instant updatedAt) {
}
