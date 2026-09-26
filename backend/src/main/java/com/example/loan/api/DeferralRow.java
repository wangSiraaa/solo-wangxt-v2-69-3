package com.example.loan.api;

import com.example.loan.domain.DeferralMode;

import java.math.BigDecimal;
import java.util.List;

/**
 * 延期模拟还款计划中的一期。当期利息在期初余额（含此前资本化）上计提；
 * events 按执行顺序列出当日发生的全部事件。
 *
 * @param period           期次（从 1 开始）
 * @param mode             当期所处的还款处理模式（NORMAL 表示不在任何宽限区间）
 * @param events           当日事件（执行顺序固定，见 {@link DeferralEventType}）
 * @param payment          当期计划还款额（按当前模式；暂停区间为 0）
 * @param principal        当期计划本金（不含补缴本金与提前还款）
 * @param interest         当期计提利息
 * @param lumpSumPaid      当期一次性补缴总额（退出期 > 0，含递延本息）
 * @param lumpSumPrincipal 一次性补缴中的本金部分
 * @param capitalized      当期资本化利息（CAPITALIZE 区间 > 0）
 * @param deferred         当期新增挂账金额（DEFER_LUMPSUM 区间 > 0，含本息）
 * @param prepayment       当期提前还款金额（> 0 表示提前还款发生在本期还款日）
 * @param balance          期末剩余本金（当日全部事件之后）
 */
public record DeferralRow(int period,
                          DeferralMode mode,
                          List<DeferralEventType> events,
                          BigDecimal payment,
                          BigDecimal principal,
                          BigDecimal interest,
                          BigDecimal lumpSumPaid,
                          BigDecimal lumpSumPrincipal,
                          BigDecimal capitalized,
                          BigDecimal deferred,
                          BigDecimal prepayment,
                          BigDecimal balance) {
}
