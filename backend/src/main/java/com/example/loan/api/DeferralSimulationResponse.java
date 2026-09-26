package com.example.loan.api;

import com.example.loan.domain.RepaymentMethod;

import java.util.List;

/**
 * 延期模拟响应：recordId 为本次模拟记录的持久化 ID；
 * intervals 为本次模拟实际使用的宽限区间（政策快照内容）。
 */
public record DeferralSimulationResponse(Long recordId,
                                         RepaymentMethod method,
                                         String policyName,
                                         List<GraceInterval> intervals,
                                         DeferralComparison comparison) {
}
