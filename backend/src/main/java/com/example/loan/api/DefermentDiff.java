package com.example.loan.api;

import java.math.BigDecimal;

/**
 * 延期方案相对原方案的关键差异（均为「延期方案 − 原方案」），
 * 并单独列示延期方案的延期利息与资本化金额。
 *
 * @param addedPeriods      新增期数（延期方案期数 − 原方案期数）
 * @param interestDiff      总利息差
 * @param totalPaymentDiff  还款总额差
 * @param totalCostDiff     总成本差（含手续费）
 * @param defermentInterest 延期方案的延期利息
 * @param capitalizedAmount 延期方案的资本化金额
 */
public record DefermentDiff(int addedPeriods,
                            BigDecimal interestDiff,
                            BigDecimal totalPaymentDiff,
                            BigDecimal totalCostDiff,
                            BigDecimal defermentInterest,
                            BigDecimal capitalizedAmount) {
}
