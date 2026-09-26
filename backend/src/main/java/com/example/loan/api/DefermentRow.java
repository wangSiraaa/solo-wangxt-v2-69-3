package com.example.loan.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * 延期模拟还款计划中的一期。
 *
 * <p>日内事件顺序（同一期内固定）：① 计提利息 → ② 区间事件（只还利息付息 /
 * 资本化 / 补缴）→ ③ 提前还款 → ④ 常规还款。{@code events} 按该顺序记录。</p>
 *
 * @param period          期次（从 1 开始）
 * @param payment         当期实际还款额（本金 + 实付利息；暂停期为 0）
 * @param principal       当期实还本金（含补缴的挂账本金）
 * @param interest        当期实付利息（含补缴的挂账利息）
 * @param accruedInterest 当期计提利息（期初剩余本金 × 月利率）
 * @param capitalized     当期资本化金额（计提利息计入本金的部分）
 * @param balance         当期所有事件后的剩余本金
 * @param events          当期发生的事件（INTEREST_ONLY / CAPITALIZE / DEFER / CATCH_UP / PREPAYMENT）
 */
public record DefermentRow(int period,
                           BigDecimal payment,
                           BigDecimal principal,
                           BigDecimal interest,
                           BigDecimal accruedInterest,
                           BigDecimal capitalized,
                           BigDecimal balance,
                           List<String> events) {
}
