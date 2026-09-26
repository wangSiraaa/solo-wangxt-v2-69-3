package com.example.loan.api;

import java.util.List;

/**
 * 延期模拟中的一个方案（原方案或延期方案）：汇总指标 + 逐期计划。
 */
public record DefermentPlanResult(String code,
                                  String label,
                                  DefermentSummary summary,
                                  List<DefermentRow> schedule) {
}
