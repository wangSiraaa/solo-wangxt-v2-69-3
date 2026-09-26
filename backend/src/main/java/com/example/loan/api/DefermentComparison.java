package com.example.loan.api;

/**
 * 宽限与延期模拟的完整结果：原方案（无宽限）与延期方案对比 + 政策快照。
 *
 * @param original 原方案：同样的提前还款安排，但不应用任何宽限区间
 * @param deferred 延期方案：提前还款 + 宽限区间共同作用后的计划
 * @param diff     两方案关键差异（含新增期数、延期利息、资本化金额）
 * @param policy   本次模拟的政策快照（与持久化内容一致，可用于复现）
 */
public record DefermentComparison(DefermentPlanResult original,
                                  DefermentPlanResult deferred,
                                  DefermentDiff diff,
                                  DefermentPolicySnapshot policy) {
}
