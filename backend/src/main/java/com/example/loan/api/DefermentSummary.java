package com.example.loan.api;

import java.math.BigDecimal;

/**
 * 延期模拟一个还款方案的汇总指标。
 *
 * @param periods           总期数
 * @param firstPayment      首期还款额
 * @param lastPayment       末期还款额
 * @param monthlyPayment    常规月供水平（仅等额本息；等额本金为 null）
 * @param totalPayment      还款总额（实还本金 + 实付利息）
 * @param totalPrincipal    实还本金合计（含资本化后转入本金部分；不含提前还款）
 * @param totalInterest     计提利息合计 = 正常利息 + 延期利息
 * @param normalInterest    正常利息（非宽限区间期次计提的利息）
 * @param defermentInterest 延期利息（宽限区间期次计提的利息）
 * @param capitalizedAmount 资本化金额合计（计入本金的利息）
 * @param fee               提前还款手续费（一次性）
 * @param totalCost         总成本（还款总额 + 手续费）
 */
public record DefermentSummary(int periods,
                               BigDecimal firstPayment,
                               BigDecimal lastPayment,
                               BigDecimal monthlyPayment,
                               BigDecimal totalPayment,
                               BigDecimal totalPrincipal,
                               BigDecimal totalInterest,
                               BigDecimal normalInterest,
                               BigDecimal defermentInterest,
                               BigDecimal capitalizedAmount,
                               BigDecimal fee,
                               BigDecimal totalCost) {
}
