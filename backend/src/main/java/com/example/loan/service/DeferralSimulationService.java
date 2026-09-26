package com.example.loan.service;

import com.example.loan.api.DeferralComparison;
import com.example.loan.api.DeferralDiff;
import com.example.loan.api.DeferralEventType;
import com.example.loan.api.DeferralPlan;
import com.example.loan.api.DeferralPlanSummary;
import com.example.loan.api.DeferralRow;
import com.example.loan.api.GraceInterval;
import com.example.loan.domain.DeferralMode;
import com.example.loan.domain.RepaymentMethod;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 宽限与延期模拟引擎。全部金额使用 BigDecimal（2 位小数，HALF_UP），
 * 月利率 = 年利率 / 12（12 位小数，由 {@link AmortizationService} 提供）。
 *
 * <p>模型约定：</p>
 * <ul>
 *   <li>还款水平按原合同（剩余本金、剩余期数）确定后全程保持不变：
 *       等额本息保持月供金额，等额本金保持月还本金；</li>
 *   <li>提前还款在指定期次的还款日冲减本金（当期利息仍在期初余额上计提）；</li>
 *   <li>宽限区间为剩余计划内的期次闭区间，区间内按所选模式处理，
 *       期限自动顺延，尾期精确结清（末期后余额恰好为 0，不出现负余额）；</li>
 *   <li>「暂停后一次性补缴」的挂账本金按影子余额计算（假设当期正常还款后的余额），
 *       保证补缴本金不会超过区间开始时的实际余额。</li>
 * </ul>
 *
 * <p>同一期次还款日内事件顺序（固定不变，即日内顺序）：</p>
 * <ol>
 *   <li>区间退出一次性补缴（EXIT_LUMPSUM）</li>
 *   <li>区间内延期行为：利息资本化（CAPITALIZE）/ 挂账（DEFER_ACCRUAL）</li>
 *   <li>提前还款（PREPAYMENT）</li>
 *   <li>当期还款（PERIOD_PAYMENT）</li>
 * </ol>
 * 当期利息在期初余额（含此前资本化）上计提，不受当日事件影响。
 */
@Service
public class DeferralSimulationService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUND = RoundingMode.HALF_UP;
    /** 模拟计算的安全上限，防止异常参数导致死循环。 */
    private static final int MAX_PERIODS = 1200;

    private final AmortizationService amortizationService;

    public DeferralSimulationService(AmortizationService amortizationService) {
        this.amortizationService = amortizationService;
    }

    /**
     * 对同一笔提前还款，分别生成「原方案」（无宽限区间）与「延期方案」（应用全部区间）并对比。
     *
     * @param method             还款方式（等额本息 / 等额本金）
     * @param annualRate         年利率，小数形式（0.049 表示 4.9%）
     * @param remainingPrincipal 当前剩余本金
     * @param remainingPeriods   当前剩余期数（月）
     * @param prepaymentAmount   提前还款金额（0 表示不提前还款）
     * @param prepaymentPeriod   提前还款发生的期次（null 默认为 1，在该期还款日冲减本金）
     * @param fee                手续费（一次性成本，不冲减本金）
     * @param intervals          宽限区间列表（至少一个；允许相邻，不允许重叠）
     */
    public DeferralComparison simulate(RepaymentMethod method,
                                       BigDecimal annualRate,
                                       BigDecimal remainingPrincipal,
                                       int remainingPeriods,
                                       BigDecimal prepaymentAmount,
                                       Integer prepaymentPeriod,
                                       BigDecimal fee,
                                       List<GraceInterval> intervals) {
        int prepayAt = validate(method, annualRate, remainingPrincipal, remainingPeriods,
                prepaymentAmount, prepaymentPeriod, fee);
        List<GraceInterval> sorted = validateIntervals(intervals, remainingPeriods);
        BigDecimal monthlyRate = amortizationService.monthlyRate(annualRate);
        // 金额统一规整到分，保证结果精度与入参精度无关（历史记录可按快照精确复现）
        BigDecimal principal0 = money(remainingPrincipal);
        BigDecimal prepayAmount = money(prepaymentAmount);
        BigDecimal feeAmount = money(fee);

        // 原方案：同一笔提前还款，不适用任何宽限区间
        DeferralPlan original = buildPlan(method, principal0, monthlyRate, remainingPeriods,
                prepayAmount, prepayAt, feeAmount, List.of());
        // 延期方案：同一笔提前还款 + 全部宽限区间
        DeferralPlan deferred = buildPlan(method, principal0, monthlyRate, remainingPeriods,
                prepayAmount, prepayAt, feeAmount, sorted);

        DeferralDiff diff = new DeferralDiff(
                deferred.summary().periods() - original.summary().periods(),
                deferred.summary().totalInterest().subtract(original.summary().totalInterest()),
                deferred.summary().totalPaid().subtract(original.summary().totalPaid()),
                deferred.summary().totalCost().subtract(original.summary().totalCost()));
        return new DeferralComparison(original, deferred, diff);
    }

    /** 校验并排序宽限区间（政策保存时使用，不校验剩余期数上限）。 */
    public List<GraceInterval> validateIntervals(List<GraceInterval> intervals) {
        return validateIntervals(intervals, Integer.MAX_VALUE);
    }

    /**
     * 校验并排序宽限区间：起始期 ≥ 1、结束期 ≥ 起始期、结束期 ≤ 剩余期数、
     * 模式合法；相邻区间允许（前一区间结束期的下一期即为后一区间起始期），重叠区间拒绝。
     */
    public List<GraceInterval> validateIntervals(List<GraceInterval> intervals, int remainingPeriods) {
        if (intervals == null || intervals.isEmpty()) {
            throw new IllegalArgumentException("至少配置一个宽限区间");
        }
        List<GraceInterval> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparingInt(GraceInterval::startPeriod));
        GraceInterval prev = null;
        for (GraceInterval iv : sorted) {
            if (iv.mode() == null || iv.mode() == DeferralMode.NORMAL) {
                throw new IllegalArgumentException("宽限区间的处理方式不能为空");
            }
            if (iv.startPeriod() < 1 || iv.endPeriod() < iv.startPeriod()) {
                throw new IllegalArgumentException(
                        "宽限区间期次不合法：[" + iv.startPeriod() + ", " + iv.endPeriod() + "]");
            }
            if (iv.endPeriod() > remainingPeriods) {
                throw new IllegalArgumentException("宽限区间 [" + iv.startPeriod() + ", " + iv.endPeriod()
                        + "] 超出剩余期数 " + remainingPeriods);
            }
            if (prev != null && iv.startPeriod() <= prev.endPeriod()) {
                throw new IllegalArgumentException("宽限区间重叠：[" + prev.startPeriod() + ", " + prev.endPeriod()
                        + "] 与 [" + iv.startPeriod() + ", " + iv.endPeriod() + "]");
            }
            prev = iv;
        }
        return sorted;
    }

    /** 生成一个完整还款计划（原方案或延期方案），逐期模拟直至结清。 */
    private DeferralPlan buildPlan(RepaymentMethod method, BigDecimal principal0, BigDecimal rate, int periods,
                                   BigDecimal prepayAmount, int prepayAt, BigDecimal fee,
                                   List<GraceInterval> intervals) {
        BigDecimal monthlyPayment = method == RepaymentMethod.EQUAL_INSTALLMENT
                ? amortizationService.installmentPayment(principal0, rate, periods) : null;
        BigDecimal fixedPrincipal = method == RepaymentMethod.EQUAL_PRINCIPAL
                ? amortizationService.monthlyPrincipal(principal0, periods) : null;

        // 期次 → 区间（区间互不重叠，每期至多属于一个区间）
        Map<Integer, GraceInterval> byPeriod = new HashMap<>();
        for (GraceInterval iv : intervals) {
            for (int p = iv.startPeriod(); p <= iv.endPeriod(); p++) {
                byPeriod.put(p, iv);
            }
        }
        // 「暂停后一次性补缴」区间的递延台账：[挂账本息合计, 挂账本金合计, 影子余额]
        Map<GraceInterval, BigDecimal[]> accrual = new HashMap<>();

        List<DeferralRow> rows = new ArrayList<>();
        BigDecimal balance = principal0;
        BigDecimal normalInterest = BigDecimal.ZERO;
        BigDecimal deferredInterest = BigDecimal.ZERO;
        BigDecimal capitalizedTotal = BigDecimal.ZERO;
        BigDecimal lumpSumTotal = BigDecimal.ZERO;
        BigDecimal scheduledPayments = BigDecimal.ZERO;
        BigDecimal scheduledPrincipal = BigDecimal.ZERO; // 计划内本金（含补缴本金，不含提前还款）
        BigDecimal prepayTotal = BigDecimal.ZERO;

        int period = 1;
        while (balance.signum() > 0) {
            if (period > MAX_PERIODS) {
                throw new IllegalArgumentException("按期数上限仍无法结清，参数不合理");
            }
            GraceInterval iv = byPeriod.get(period);
            DeferralMode mode = iv == null ? DeferralMode.NORMAL : iv.mode();
            // 当期利息在期初余额（含此前资本化）上计提，不受当日事件影响
            BigDecimal interest = money(balance.multiply(rate));
            if (mode == DeferralMode.NORMAL) {
                normalInterest = normalInterest.add(interest);
            } else {
                deferredInterest = deferredInterest.add(interest);
            }

            List<DeferralEventType> events = new ArrayList<>(4);
            BigDecimal payment = BigDecimal.ZERO;
            BigDecimal principal = BigDecimal.ZERO;
            BigDecimal lumpSum = BigDecimal.ZERO;
            BigDecimal lumpSumPrincipal = BigDecimal.ZERO;
            BigDecimal capitalized = BigDecimal.ZERO;
            BigDecimal deferred = BigDecimal.ZERO;
            BigDecimal prepay = BigDecimal.ZERO;

            // 事件 1：区间退出一次性补缴（上一期属于补缴区间且本期已离开该区间）
            GraceInterval prev = byPeriod.get(period - 1);
            if (prev != null && prev.mode() == DeferralMode.DEFER_LUMPSUM && prev != iv) {
                BigDecimal[] acc = accrual.get(prev);
                // 提前还款可能已冲减部分本金，补缴本金不超过当前余额
                lumpSumPrincipal = acc[1].min(balance);
                lumpSum = acc[0].subtract(acc[1]).add(lumpSumPrincipal);
                balance = balance.subtract(lumpSumPrincipal);
                events.add(DeferralEventType.EXIT_LUMPSUM);
            }

            // 事件 2：区间内的当期延期行为
            if (mode == DeferralMode.CAPITALIZE) {
                capitalized = interest;
                balance = balance.add(interest);
                capitalizedTotal = capitalizedTotal.add(interest);
                events.add(DeferralEventType.CAPITALIZE);
            } else if (mode == DeferralMode.DEFER_LUMPSUM) {
                BigDecimal shadow0 = balance; // 进入区间时的实际余额作为影子余额起点
                BigDecimal[] acc = accrual.computeIfAbsent(iv,
                        k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, shadow0});
                // 挂账本金按影子余额兜底，保证补缴本金总额不超过区间开始时的实际余额
                BigDecimal deferredPrincipal = scheduledPrincipal(method, monthlyPayment, fixedPrincipal, interest)
                        .min(acc[2]);
                deferred = interest.add(deferredPrincipal);
                acc[0] = acc[0].add(deferred);
                acc[1] = acc[1].add(deferredPrincipal);
                acc[2] = acc[2].subtract(deferredPrincipal);
                events.add(DeferralEventType.DEFER_ACCRUAL);
            }

            // 事件 3：提前还款（在本期还款日冲减本金）
            if (period == prepayAt && prepayAmount.signum() > 0) {
                if (prepayAmount.compareTo(balance) >= 0) {
                    throw new IllegalArgumentException("提前还款金额必须小于第 " + period
                            + " 期还款日当时的剩余本金（大于等于即为全额结清）");
                }
                balance = balance.subtract(prepayAmount);
                prepay = prepayAmount;
                prepayTotal = prepayTotal.add(prepayAmount);
                events.add(DeferralEventType.PREPAYMENT);
            }

            // 事件 4：当期还款
            if (mode == DeferralMode.INTEREST_ONLY) {
                payment = interest; // 只还利息，本金不变
                events.add(DeferralEventType.PERIOD_PAYMENT);
            } else if (mode == DeferralMode.NORMAL) {
                principal = scheduledPrincipal(method, monthlyPayment, fixedPrincipal, interest).min(balance);
                payment = principal.add(interest);
                balance = balance.subtract(principal);
                events.add(DeferralEventType.PERIOD_PAYMENT);
            }

            scheduledPayments = scheduledPayments.add(payment);
            scheduledPrincipal = scheduledPrincipal.add(principal).add(lumpSumPrincipal);
            lumpSumTotal = lumpSumTotal.add(lumpSum);
            rows.add(new DeferralRow(period, mode, List.copyOf(events),
                    payment, principal, interest, lumpSum, lumpSumPrincipal,
                    capitalized, deferred, prepay, balance));
            period++;
        }

        BigDecimal totalInterest = normalInterest.add(deferredInterest);
        BigDecimal totalPaid = scheduledPayments.add(lumpSumTotal).add(prepayTotal);
        DeferralPlanSummary summary = new DeferralPlanSummary(
                rows.size(), monthlyPayment,
                rowCash(rows.get(0)), rowCash(rows.get(rows.size() - 1)),
                scheduledPayments, lumpSumTotal, prepayTotal, totalPaid,
                scheduledPrincipal, normalInterest, deferredInterest, totalInterest,
                capitalizedTotal, fee, totalPaid.add(fee));
        return new DeferralPlan(summary, rows);
    }

    /** 当期计划本金：等额本息 = 月供 − 当期利息；等额本金 = 月还本金。 */
    private BigDecimal scheduledPrincipal(RepaymentMethod method, BigDecimal monthlyPayment,
                                          BigDecimal fixedPrincipal, BigDecimal interest) {
        if (method == RepaymentMethod.EQUAL_PRINCIPAL) {
            return fixedPrincipal;
        }
        BigDecimal principalPart = monthlyPayment.subtract(interest);
        if (principalPart.signum() <= 0) {
            throw new IllegalArgumentException("月供不足以覆盖当期利息，参数不合理");
        }
        return principalPart;
    }

    /** 一期的现金流出合计（计划还款 + 一次性补缴 + 提前还款）。 */
    private static BigDecimal rowCash(DeferralRow row) {
        return row.payment().add(row.lumpSumPaid()).add(row.prepayment());
    }

    private int validate(RepaymentMethod method, BigDecimal annualRate, BigDecimal remainingPrincipal,
                         int remainingPeriods, BigDecimal prepaymentAmount, Integer prepaymentPeriod,
                         BigDecimal fee) {
        if (method == null) {
            throw new IllegalArgumentException("还款方式不能为空");
        }
        if (annualRate == null || annualRate.signum() < 0 || annualRate.compareTo(new BigDecimal("0.36")) > 0) {
            throw new IllegalArgumentException("年利率需在 0 ~ 36% 之间");
        }
        if (remainingPrincipal == null || remainingPrincipal.signum() <= 0) {
            throw new IllegalArgumentException("剩余本金必须大于 0");
        }
        if (remainingPeriods < 1 || remainingPeriods > 600) {
            throw new IllegalArgumentException("剩余期数需在 1 ~ 600 之间");
        }
        if (prepaymentAmount == null || prepaymentAmount.signum() < 0) {
            throw new IllegalArgumentException("提前还款金额不能为负（0 表示不提前还款）");
        }
        if (fee == null || fee.signum() < 0) {
            throw new IllegalArgumentException("手续费不能为负");
        }
        int prepayAt = prepaymentPeriod == null ? 1 : prepaymentPeriod;
        if (prepaymentAmount.signum() > 0 && (prepayAt < 1 || prepayAt > remainingPeriods)) {
            throw new IllegalArgumentException("提前还款期次需在 1 ~ " + remainingPeriods + " 之间");
        }
        return prepayAt;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, ROUND);
    }
}
