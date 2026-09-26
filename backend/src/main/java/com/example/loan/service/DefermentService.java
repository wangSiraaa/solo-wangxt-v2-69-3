package com.example.loan.service;

import com.example.loan.api.DefermentComparison;
import com.example.loan.api.DefermentDiff;
import com.example.loan.api.DefermentIntervalRequest;
import com.example.loan.api.DefermentPlanResult;
import com.example.loan.api.DefermentPolicySnapshot;
import com.example.loan.api.DefermentRow;
import com.example.loan.api.DefermentSummary;
import com.example.loan.domain.DefermentType;
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
 * 宽限与延期模拟计算。全部金额使用 BigDecimal（2 位小数，HALF_UP），
 * 月利率 = 年利率 / 12（保留 12 位小数），规则与 {@link AmortizationService} 一致。
 *
 * <p>在同一还款计划上叠加两类事件：</p>
 * <ul>
 *   <li>提前还款：在指定期次直接冲减本金（期次 0 表示计划开始前立即扣减）；</li>
 *   <li>宽限区间：互不重叠的期次闭区间，类型为只还利息 / 暂停且利息资本化 / 暂停后一次性补缴。</li>
 * </ul>
 *
 * <p>每期的日内事件顺序固定（保证宽限结束期与提前还款同期时结果确定）：<br>
 * ① 计提利息（期初剩余本金 × 月利率）→ ② 区间事件（只还利息付息 / 资本化 / 补缴）
 * → ③ 提前还款 → ④ 常规还款。</p>
 *
 * <p>常规还款保持原月供水平（等额本息保持月供金额、等额本金保持月还本金），
 * 逐月模拟至结清；延期导致期限变化时，末期本金 = 当期期初剩余本金，尾期后余额恰好为 0。</p>
 */
@Service
public class DefermentService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUND = RoundingMode.HALF_UP;
    /** 模拟计算的安全上限，防止异常参数导致死循环。 */
    private static final int MAX_PERIODS = 1200;

    /** 区间事件：只还利息（当期付息）。 */
    public static final String EVENT_INTEREST_ONLY = "INTEREST_ONLY";
    /** 区间事件：利息资本化。 */
    public static final String EVENT_CAPITALIZE = "CAPITALIZE";
    /** 区间事件：暂停挂账（补缴型区间的非结束期）。 */
    public static final String EVENT_DEFER = "DEFER";
    /** 区间事件：一次性补缴（补缴型区间的结束期）。 */
    public static final String EVENT_CATCH_UP = "CATCH_UP";
    /** 提前还款。 */
    public static final String EVENT_PREPAYMENT = "PREPAYMENT";

    private final AmortizationService amortizationService;

    public DefermentService(AmortizationService amortizationService) {
        this.amortizationService = amortizationService;
    }

    /**
     * 按政策快照生成原方案（无宽限）与延期方案并对比。
     * 同一快照重复计算结果完全一致（可复现）。
     */
    public DefermentComparison simulate(DefermentPolicySnapshot policy) {
        List<DefermentIntervalRequest> intervals = validateAndNormalize(policy);
        DefermentPolicySnapshot normalized = new DefermentPolicySnapshot(
                DefermentPolicySnapshot.CURRENT_VERSION,
                policy.method(), policy.annualRate(), policy.remainingPrincipal(),
                policy.remainingPeriods(), policy.prepaymentAmount(),
                policy.prepaymentPeriod(), policy.fee(), intervals);

        BigDecimal monthlyRate = amortizationService.monthlyRate(policy.annualRate());
        // 常规还款水平：等额本息保持月供金额、等额本金保持月还本金（均按提前还款前的合同参数计算）
        BigDecimal paymentLevel = policy.method() == RepaymentMethod.EQUAL_INSTALLMENT
                ? amortizationService.installmentPayment(policy.remainingPrincipal(), monthlyRate, policy.remainingPeriods())
                : null;
        BigDecimal monthlyPrincipal = policy.method() == RepaymentMethod.EQUAL_PRINCIPAL
                ? amortizationService.monthlyPrincipal(policy.remainingPrincipal(), policy.remainingPeriods())
                : null;

        Map<Integer, DefermentIntervalRequest> byPeriod = new HashMap<>();
        for (DefermentIntervalRequest iv : intervals) {
            for (int p = iv.startPeriod(); p <= iv.endPeriod(); p++) {
                byPeriod.put(p, iv);
            }
        }

        // 原方案：同样的提前还款安排，不应用任何宽限区间
        List<DefermentRow> originalRows = run(normalized, monthlyRate, paymentLevel, monthlyPrincipal, Map.of());
        DefermentSummary originalSummary = summarize(normalized, originalRows, paymentLevel, Map.of());

        // 延期方案：提前还款 + 宽限区间
        List<DefermentRow> deferredRows = run(normalized, monthlyRate, paymentLevel, monthlyPrincipal, byPeriod);
        DefermentSummary deferredSummary = summarize(normalized, deferredRows, paymentLevel, byPeriod);

        DefermentDiff diff = new DefermentDiff(
                deferredSummary.periods() - originalSummary.periods(),
                deferredSummary.totalInterest().subtract(originalSummary.totalInterest()),
                deferredSummary.totalPayment().subtract(originalSummary.totalPayment()),
                deferredSummary.totalCost().subtract(originalSummary.totalCost()),
                deferredSummary.defermentInterest(),
                deferredSummary.capitalizedAmount());

        return new DefermentComparison(
                new DefermentPlanResult("ORIGINAL", "原方案（无宽限）", originalSummary, originalRows),
                new DefermentPlanResult("DEFERRED", "延期方案", deferredSummary, deferredRows),
                diff, normalized);
    }

    /**
     * 逐月模拟一份还款计划。宽限区间外的期次按常规还款（保持原月供水平），
     * 末期自动结清：末期本金 = 当期期初剩余本金，尾期后余额恰好为 0。
     */
    private List<DefermentRow> run(DefermentPolicySnapshot policy, BigDecimal monthlyRate,
                                   BigDecimal paymentLevel, BigDecimal monthlyPrincipal,
                                   Map<Integer, DefermentIntervalRequest> byPeriod) {
        List<DefermentRow> rows = new ArrayList<>();
        BigDecimal balance = policy.remainingPrincipal();
        BigDecimal prepayment = policy.prepaymentAmount();

        // 提前还款期次为 0：计划开始前立即扣减
        if (policy.prepaymentPeriod() == 0 && prepayment.signum() > 0) {
            balance = money(balance.subtract(prepayment));
        }

        BigDecimal deferredInterest = BigDecimal.ZERO;  // 补缴型区间挂账利息
        BigDecimal deferredPrincipal = BigDecimal.ZERO; // 补缴型区间挂账本金
        int p = 0;
        while (balance.signum() > 0) {
            p++;
            if (p > MAX_PERIODS) {
                throw new IllegalArgumentException("按期数上限仍无法结清，参数不合理");
            }
            // ① 计提利息（期初剩余本金 × 月利率）
            BigDecimal accrued = money(balance.multiply(monthlyRate));
            List<String> events = new ArrayList<>(2);
            BigDecimal capitalized = BigDecimal.ZERO;
            BigDecimal cashInterest = BigDecimal.ZERO;
            BigDecimal cashPrincipal = BigDecimal.ZERO;

            // ② 区间事件（区间内不进行常规还款；余额变动立即生效）
            DefermentIntervalRequest iv = byPeriod.get(p);
            if (iv != null) {
                if (iv.type() == DefermentType.INTEREST_ONLY) {
                    cashInterest = accrued;
                    events.add(EVENT_INTEREST_ONLY);
                } else if (iv.type() == DefermentType.CAPITALIZE) {
                    capitalized = accrued;
                    balance = balance.add(accrued);
                    events.add(EVENT_CAPITALIZE);
                } else if (p == iv.endPeriod()) {
                    // 补缴型区间结束期：一次性补缴全部挂账本息 + 当期应还
                    BigDecimal virtualPrincipal = virtualPrincipalPart(policy.method(), paymentLevel, monthlyPrincipal, accrued);
                    cashInterest = accrued.add(deferredInterest);
                    cashPrincipal = virtualPrincipal.add(deferredPrincipal);
                    deferredInterest = BigDecimal.ZERO;
                    deferredPrincipal = BigDecimal.ZERO;
                    balance = balance.subtract(cashPrincipal);
                    events.add(EVENT_CATCH_UP);
                } else {
                    // 补缴型区间非结束期：暂停还款，本息挂账（不计复利）
                    BigDecimal virtualPrincipal = virtualPrincipalPart(policy.method(), paymentLevel, monthlyPrincipal, accrued);
                    deferredInterest = deferredInterest.add(accrued);
                    deferredPrincipal = deferredPrincipal.add(virtualPrincipal);
                    events.add(EVENT_DEFER);
                }
            }

            // ③ 提前还款：区间事件之后、常规还款之前扣减本金（同日事件顺序固定）
            if (p == policy.prepaymentPeriod() && prepayment.signum() > 0) {
                if (prepayment.compareTo(balance) >= 0) {
                    throw new IllegalArgumentException(
                            "提前还款金额必须小于第 " + p + " 期事件后的剩余本金（" + money(balance) + "）");
                }
                balance = balance.subtract(prepayment);
                events.add(EVENT_PREPAYMENT);
            }

            // ④ 常规还款（仅当本期无区间事件；末期按剩余本金结清）
            if (iv == null) {
                cashInterest = accrued;
                cashPrincipal = switch (policy.method()) {
                    case EQUAL_INSTALLMENT -> {
                        BigDecimal part = paymentLevel.subtract(accrued);
                        if (part.signum() <= 0) {
                            throw new IllegalArgumentException("月供不足以覆盖当期利息（资本化会推高计息基数），请调整方案");
                        }
                        yield part.min(balance); // 末期结清
                    }
                    case EQUAL_PRINCIPAL -> monthlyPrincipal.min(balance); // 末期结清
                };
                balance = balance.subtract(cashPrincipal);
            }

            rows.add(new DefermentRow(p,
                    money(cashPrincipal.add(cashInterest)),
                    money(cashPrincipal),
                    money(cashInterest),
                    accrued,
                    money(capitalized),
                    money(balance),
                    List.copyOf(events)));
        }
        return rows;
    }

    /** 补缴型区间内「若正常还款」当期应还的本金部分（用于挂账）。 */
    private BigDecimal virtualPrincipalPart(RepaymentMethod method, BigDecimal paymentLevel,
                                            BigDecimal monthlyPrincipal, BigDecimal accrued) {
        return switch (method) {
            case EQUAL_INSTALLMENT -> {
                BigDecimal part = paymentLevel.subtract(accrued);
                if (part.signum() <= 0) {
                    throw new IllegalArgumentException("暂停期间计提利息超过月供，无法挂账，请调整方案");
                }
                yield part;
            }
            case EQUAL_PRINCIPAL -> monthlyPrincipal;
        };
    }

    /** 汇总一份延期模拟计划：正常利息 / 延期利息 / 资本化金额分别列示。 */
    private DefermentSummary summarize(DefermentPolicySnapshot policy, List<DefermentRow> rows,
                                       BigDecimal paymentLevel, Map<Integer, DefermentIntervalRequest> byPeriod) {
        BigDecimal totalPayment = BigDecimal.ZERO;
        BigDecimal totalPrincipal = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal normalInterest = BigDecimal.ZERO;
        BigDecimal defermentInterest = BigDecimal.ZERO;
        BigDecimal capitalizedAmount = BigDecimal.ZERO;
        for (DefermentRow row : rows) {
            totalPayment = totalPayment.add(row.payment());
            totalPrincipal = totalPrincipal.add(row.principal());
            totalInterest = totalInterest.add(row.accruedInterest());
            capitalizedAmount = capitalizedAmount.add(row.capitalized());
            if (byPeriod.containsKey(row.period())) {
                defermentInterest = defermentInterest.add(row.accruedInterest());
            } else {
                normalInterest = normalInterest.add(row.accruedInterest());
            }
        }
        BigDecimal first = rows.get(0).payment();
        BigDecimal last = rows.get(rows.size() - 1).payment();
        BigDecimal monthly = policy.method() == RepaymentMethod.EQUAL_INSTALLMENT ? paymentLevel : null;
        return new DefermentSummary(rows.size(), money(first), money(last), monthly == null ? null : money(monthly),
                money(totalPayment), money(totalPrincipal), money(totalInterest),
                money(normalInterest), money(defermentInterest), money(capitalizedAmount),
                money(policy.fee()), money(totalPayment.add(policy.fee())));
    }

    /** 校验政策参数，并把区间归一化（按起始期排序）；重叠区间直接拒绝，相邻区间允许。 */
    private List<DefermentIntervalRequest> validateAndNormalize(DefermentPolicySnapshot policy) {
        if (policy.method() == null) {
            throw new IllegalArgumentException("还款方式不能为空");
        }
        if (policy.annualRate() == null || policy.annualRate().signum() < 0
                || policy.annualRate().compareTo(new BigDecimal("0.36")) > 0) {
            throw new IllegalArgumentException("年利率需在 0 ~ 36% 之间");
        }
        if (policy.remainingPrincipal() == null || policy.remainingPrincipal().signum() <= 0) {
            throw new IllegalArgumentException("剩余本金必须大于 0");
        }
        if (policy.remainingPeriods() < 1 || policy.remainingPeriods() > 600) {
            throw new IllegalArgumentException("剩余期数需在 1 ~ 600 之间");
        }
        if (policy.prepaymentAmount() == null || policy.prepaymentAmount().signum() < 0) {
            throw new IllegalArgumentException("提前还款金额不能为负");
        }
        if (policy.prepaymentAmount().compareTo(policy.remainingPrincipal()) >= 0) {
            throw new IllegalArgumentException("提前还款金额必须小于剩余本金（大于等于剩余本金即为全额结清）");
        }
        if (policy.prepaymentPeriod() < 0 || policy.prepaymentPeriod() > policy.remainingPeriods()) {
            throw new IllegalArgumentException("提前还款期次需在 0（计划开始前）~ 剩余期数之间");
        }
        if (policy.fee() == null || policy.fee().signum() < 0) {
            throw new IllegalArgumentException("手续费不能为负");
        }

        List<DefermentIntervalRequest> intervals = policy.intervals() == null
                ? List.of()
                : new ArrayList<>(policy.intervals());
        for (DefermentIntervalRequest iv : intervals) {
            if (iv == null || iv.type() == null) {
                throw new IllegalArgumentException("宽限区间的类型不能为空");
            }
            if (iv.startPeriod() < 1) {
                throw new IllegalArgumentException("宽限区间起始期至少为 1");
            }
            if (iv.endPeriod() < iv.startPeriod()) {
                throw new IllegalArgumentException(
                        "宽限区间结束期不能小于起始期：第 " + iv.startPeriod() + " 期 ~ 第 " + iv.endPeriod() + " 期");
            }
            if (iv.endPeriod() > policy.remainingPeriods()) {
                throw new IllegalArgumentException(
                        "宽限区间不能超出剩余期数（" + policy.remainingPeriods() + " 期）：第 "
                                + iv.startPeriod() + " 期 ~ 第 " + iv.endPeriod() + " 期");
            }
        }
        intervals.sort(Comparator.comparingInt(DefermentIntervalRequest::startPeriod)
                .thenComparingInt(DefermentIntervalRequest::endPeriod));
        for (int i = 1; i < intervals.size(); i++) {
            DefermentIntervalRequest prev = intervals.get(i - 1);
            DefermentIntervalRequest next = intervals.get(i);
            if (next.startPeriod() <= prev.endPeriod()) {
                throw new IllegalArgumentException(
                        "宽限区间重叠：第 " + prev.startPeriod() + " 期 ~ 第 " + prev.endPeriod()
                                + " 期 与 第 " + next.startPeriod() + " 期 ~ 第 " + next.endPeriod() + " 期");
            }
        }
        return List.copyOf(intervals);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, ROUND);
    }
}
