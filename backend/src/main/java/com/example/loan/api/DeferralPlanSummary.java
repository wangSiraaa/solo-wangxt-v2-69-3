package com.example.loan.api;

import java.math.BigDecimal;

/**
 * 延期模拟一个还款方案的汇总指标。利息按「正常利息 / 延期利息」分列，
 * 资本化金额、一次性补缴、提前还款单独列示。
 *
 * @param periods           总期数
 * @param monthlyPayment    月供水平（仅等额本息；等额本金为 null）
 * @param firstPayment      首期现金合计（计划还款 + 补缴 + 提前还款）
 * @param lastPayment       末期现金合计
 * @param scheduledPayments 计划内还款合计（不含补缴与提前还款）
 * @param lumpSumPaid       一次性补缴合计
 * @param prepayment        提前还款金额
 * @param totalPaid         现金流出合计（计划还款 + 补缴 + 提前还款，不含手续费）
 * @param totalPrincipal    计划内本金合计（含补缴本金，不含提前还款）
 * @param normalInterest    正常利息（非区间期次计提的利息）
 * @param deferredInterest  延期利息（区间期次计提的利息，含已资本化与已补缴部分）
 * @param totalInterest     总利息（正常利息 + 延期利息）
 * @param capitalizedAmount 资本化金额合计
 * @param fee               手续费（一次性）
 * @param totalCost         总成本（现金流出合计 + 手续费）
 */
public record DeferralPlanSummary(int periods,
                                  BigDecimal monthlyPayment,
                                  BigDecimal firstPayment,
                                  BigDecimal lastPayment,
                                  BigDecimal scheduledPayments,
                                  BigDecimal lumpSumPaid,
                                  BigDecimal prepayment,
                                  BigDecimal totalPaid,
                                  BigDecimal totalPrincipal,
                                  BigDecimal normalInterest,
                                  BigDecimal deferredInterest,
                                  BigDecimal totalInterest,
                                  BigDecimal capitalizedAmount,
                                  BigDecimal fee,
                                  BigDecimal totalCost) {
}
