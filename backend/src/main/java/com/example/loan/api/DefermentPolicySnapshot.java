package com.example.loan.api;

import com.example.loan.domain.RepaymentMethod;

import java.math.BigDecimal;
import java.util.List;

/**
 * 延期政策快照：一次宽限与延期模拟的全部输入。随计算记录持久化，
 * 之后即使政策（区间、提前还款安排）被修改，历史结果仍可按该快照复现。
 *
 * @param policyVersion      政策/计算口径版本
 * @param prepaymentPeriod   提前还款所在期次（0 = 计划开始前立即扣减）
 * @param intervals          归一化（按起始期排序）后的宽限区间
 */
public record DefermentPolicySnapshot(
        String policyVersion,
        RepaymentMethod method,
        BigDecimal annualRate,
        BigDecimal remainingPrincipal,
        int remainingPeriods,
        BigDecimal prepaymentAmount,
        int prepaymentPeriod,
        BigDecimal fee,
        List<DefermentIntervalRequest> intervals) {

    public static final String CURRENT_VERSION = "v1";
}
