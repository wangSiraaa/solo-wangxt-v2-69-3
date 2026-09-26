package com.example.loan.api;

import com.example.loan.domain.RepaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 延期模拟历史记录的列表视图（不暴露实体与懒加载关联）。
 */
public record DefermentRecordView(
        Long id,
        Instant createdAt,
        String contractNo,
        RepaymentMethod method,
        BigDecimal annualRate,
        BigDecimal remainingPrincipal,
        int remainingPeriods,
        BigDecimal prepaymentAmount,
        int prepaymentPeriod,
        int intervalCount,
        int originalPeriods,
        int deferredPeriods,
        int addedPeriods,
        BigDecimal defermentInterest,
        BigDecimal capitalizedAmount,
        BigDecimal interestDiff) {
}
