package com.example.loan.api;

import java.math.BigDecimal;

/**
 * 延期方案相对原方案的关键差异（均为「延期方案 − 原方案」，两方案适用同一笔提前还款）。
 *
 * @param periodDiff    新增期数（正数表示延期方案期数更多）
 * @param interestDiff  总利息差
 * @param totalPaidDiff 总还款差（不含手续费）
 * @param totalCostDiff 总成本差（含手续费）
 */
public record DeferralDiff(int periodDiff,
                           BigDecimal interestDiff,
                           BigDecimal totalPaidDiff,
                           BigDecimal totalCostDiff) {
}
