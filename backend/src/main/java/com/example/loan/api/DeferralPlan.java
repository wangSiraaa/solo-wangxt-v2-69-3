package com.example.loan.api;

import java.util.List;

/**
 * 延期模拟中的一个还款方案：汇总指标 + 逐期计划。
 */
public record DeferralPlan(DeferralPlanSummary summary, List<DeferralRow> schedule) {
}
