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
 * 若指定 contractId，则合同参数（还款方式、利率、剩余本金、剩余期数）取自模拟合同，
 * 否则使用请求中手工录入的参数。年利率为小数形式（0.049 表示 4.9%）。
 *
 * @param prepaymentAmount 提前还款金额（直接冲减本金；为 0 表示不提前还款）
 * @param prepaymentPeriod 提前还款所在期次：0 表示计划开始前立即扣减；
 *                         N ≥ 1 表示在第 N 期的日内顺序中于区间事件之后、常规还款之前扣减
 * @param intervals        宽限区间列表（可空，互不重叠；相邻允许）
 */
public record DefermentRequest(
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

        @NotNull(message = "提前还款金额不能为空（可为 0，表示不提前还款）")
        @DecimalMin(value = "0", message = "提前还款金额不能为负")
        BigDecimal prepaymentAmount,

        @Min(value = 0, message = "提前还款期次不能为负")
        Integer prepaymentPeriod,

        @NotNull(message = "手续费不能为空（可为 0）")
        @DecimalMin(value = "0", message = "手续费不能为负")
        BigDecimal fee,

        @Valid
        List<DefermentIntervalRequest> intervals) {
}
