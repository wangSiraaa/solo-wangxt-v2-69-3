package com.example.loan.api;

/**
 * 宽限与延期模拟的完整结果：同一笔提前还款下，
 * 原方案（无宽限区间）与延期方案（应用宽限区间）的对比。
 *
 * @param original 原方案（不适用任何宽限区间）
 * @param deferred 延期方案（应用全部宽限区间）
 * @param diff     两方案关键差异（延期 − 原）
 */
public record DeferralComparison(DeferralPlan original,
                                 DeferralPlan deferred,
                                 DeferralDiff diff) {
}
