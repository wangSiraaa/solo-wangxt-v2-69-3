package com.example.loan.api;

import com.example.loan.domain.RepaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * 宽限与延期模拟请求。
 * 合同参数：指定 contractId 取自模拟合同，否则使用手工录入参数。
 * 宽限区间：policyId 与内联 intervals 二选一；使用政策时按政策当前内容冻结快照。
 * 提前还款：prepaymentAmount 为 0 表示不提前还款；否则在 prepaymentPeriod 期还款日冲减本金
 * （默认第 1 期）。年利率为小数形式（0.049 表示 4.9%）。
 */
public record DeferralSimulateRequest(
        Long contractId,

        RepaymentMethod method,

        @DecimalMin(value = "0", message = "年利率不能为负")
        @DecimalMax(value = "0.36", message = "年利率不能超过 36%")
        BigDecimal annualRate,

        @DecimalMin(value = "0.01", message = "剩余本金必须大于 0")
        BigDecimal remainingPrincipal,

        @Min(value = 1, message = "剩余期数至少为 1")
        @Max(value = 600, message = "剩余期数不能超过 600")
        Integer remainingPeriods,

        Long policyId,

        List<@Valid GraceInterval> intervals,

        @NotNull(message = "提前还款金额不能为空（0 表示不提前还款）")
        @DecimalMin(value = "0", message = "提前还款金额不能为负")
        BigDecimal prepaymentAmount,

        @Min(value = 1, message = "提前还款期次至少为 1")
        Integer prepaymentPeriod,

        @NotNull(message = "手续费不能为空（可为 0）")
        @DecimalMin(value = "0", message = "手续费不能为负")
        BigDecimal fee) {
}
