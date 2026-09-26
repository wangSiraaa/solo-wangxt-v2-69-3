package com.example.loan.service;

import com.example.loan.api.DefermentComparison;
import com.example.loan.api.DefermentIntervalRequest;
import com.example.loan.api.DefermentPolicySnapshot;
import com.example.loan.api.DefermentRow;
import com.example.loan.domain.DefermentType;
import com.example.loan.domain.RepaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 宽限与延期模拟的验收测试。
 *
 * 主要示例：剩余本金 100,000.00，年利率 12%（月利率 0.01），剩余 12 期，
 * 不提前还款、无手续费；需要时另设提前还款。
 */
class DefermentServiceTest {

    private final DefermentService service = new DefermentService(new AmortizationService());

    private static final BigDecimal P = new BigDecimal("100000.00");
    private static final BigDecimal RATE = new BigDecimal("0.12"); // 月利率 0.01，便于手算核对
    private static final int PERIODS = 12;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private static DefermentPolicySnapshot policy(RepaymentMethod method, BigDecimal principal,
                                                  BigDecimal rate, int periods,
                                                  BigDecimal prepay, int prepayPeriod,
                                                  List<DefermentIntervalRequest> intervals) {
        return new DefermentPolicySnapshot(DefermentPolicySnapshot.CURRENT_VERSION,
                method, rate, principal, periods, prepay, prepayPeriod, ZERO, intervals);
    }

    private static DefermentIntervalRequest interval(int start, int end, DefermentType type) {
        return new DefermentIntervalRequest(start, end, type);
    }

    private static DefermentRow row(DefermentComparison c, int period) {
        return c.deferred().schedule().get(period - 1);
    }

    // ---------- 只还利息：期间本金不变 ----------

    @Test
    void interestOnly_principalUnchangedDuringInterval() {
        DefermentComparison c = service.simulate(policy(RepaymentMethod.EQUAL_INSTALLMENT,
                P, RATE, PERIODS, ZERO, 0,
                List.of(interval(3, 5, DefermentType.INTEREST_ONLY))));

        BigDecimal balanceBefore = row(c, 2).balance();
        for (int p = 3; p <= 5; p++) {
            DefermentRow r = row(c, p);
            assertEquals(0, r.principal().compareTo(ZERO), "第 " + p + " 期不应还本金");
            assertEquals(r.accruedInterest(), r.interest(), "第 " + p + " 期利息应全额实付");
            assertEquals(r.accruedInterest(), r.payment(), "第 " + p + " 期月供 = 当期利息");
            assertEquals(balanceBefore, r.balance(), "第 " + p + " 期本金不变");
            assertEquals(List.of("INTEREST_ONLY"), r.events());
        }
        // 延期利息 = 区间内三期计提利息（基数不变，三期相同）
        BigDecimal perPeriod = row(c, 3).accruedInterest();
        assertEquals(perPeriod.multiply(BigDecimal.valueOf(3)),
                c.deferred().summary().defermentInterest());
        assertEquals(ZERO, c.deferred().summary().capitalizedAmount());
        // 区间后余额回到原方案第 2 期水平，但本金偿还暂停了 3 期，期限顺延 3 期
        assertEquals(PERIODS + 3, c.deferred().summary().periods());
        assertEquals(3, c.diff().addedPeriods());
        // 只还利息期间少还了本金，后续计息基数更高，总利息更多
        assertTrue(c.diff().interestDiff().signum() > 0);
        assertPlanConsistent(c.deferred().schedule(), P, ZERO,
                c.deferred().summary().capitalizedAmount());
    }

    // ---------- 利息资本化：下一期计息基数增加 ----------

    @Test
    void capitalize_increasesNextPeriodInterestBase() {
        DefermentComparison c = service.simulate(policy(RepaymentMethod.EQUAL_INSTALLMENT,
                P, RATE, PERIODS, ZERO, 0,
                List.of(interval(2, 2, DefermentType.CAPITALIZE))));

        DefermentRow r1 = row(c, 1);
        DefermentRow r2 = row(c, 2);
        DefermentRow r3 = row(c, 3);

        // 第 2 期：不还款，利息资本化
        assertEquals(0, r2.payment().compareTo(ZERO));
        assertEquals(r2.accruedInterest(), r2.capitalized());
        assertEquals(r1.balance().add(r2.accruedInterest()), r2.balance());
        assertEquals(List.of("CAPITALIZE"), r2.events());

        // 第 3 期计息基数 = 资本化后的余额，利息随之增加
        assertEquals(r2.balance().multiply(new BigDecimal("0.01")).setScale(2, java.math.RoundingMode.HALF_UP),
                r3.accruedInterest());
        assertTrue(r3.accruedInterest().compareTo(r2.accruedInterest()) > 0,
                "资本化后下一期计息基数增加");

        // 汇总：资本化金额 = 第 2 期计提利息；延期利息 = 第 2 期计提利息
        assertEquals(r2.accruedInterest(), c.deferred().summary().capitalizedAmount());
        assertEquals(r2.accruedInterest(), c.deferred().summary().defermentInterest());
        assertEquals(r2.accruedInterest(), c.diff().capitalizedAmount());
        assertPlanConsistent(c.deferred().schedule(), P, ZERO,
                c.deferred().summary().capitalizedAmount());
    }

    // ---------- 相邻区间可处理，重叠区间被拒 ----------

    @Test
    void adjacentIntervalsAllowed() {
        // [2,3] 与 [4,6] 相邻（前一区间结束期 + 1 = 后一区间起始期），且乱序传入也可归一化
        DefermentComparison c = service.simulate(policy(RepaymentMethod.EQUAL_INSTALLMENT,
                P, RATE, PERIODS, ZERO, 0,
                List.of(interval(4, 6, DefermentType.CAPITALIZE),
                        interval(2, 3, DefermentType.INTEREST_ONLY))));

        assertEquals(List.of("INTEREST_ONLY"), row(c, 2).events());
        assertEquals(List.of("INTEREST_ONLY"), row(c, 3).events());
        assertEquals(List.of("CAPITALIZE"), row(c, 4).events());
        // 快照中的区间已按起始期排序
        assertEquals(2, c.policy().intervals().get(0).startPeriod());
        assertEquals(4, c.policy().intervals().get(1).startPeriod());
        assertPlanConsistent(c.deferred().schedule(), P, ZERO,
                c.deferred().summary().capitalizedAmount());
    }

    @Test
    void overlappingIntervalsRejected() {
        DefermentPolicySnapshot overlapping = policy(RepaymentMethod.EQUAL_INSTALLMENT,
                P, RATE, PERIODS, ZERO, 0,
                List.of(interval(2, 4, DefermentType.INTEREST_ONLY),
                        interval(4, 6, DefermentType.CAPITALIZE)));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.simulate(overlapping));
        assertTrue(e.getMessage().contains("重叠"), e.getMessage());

        // 完全包含也算重叠
        DefermentPolicySnapshot nested = policy(RepaymentMethod.EQUAL_INSTALLMENT,
                P, RATE, PERIODS, ZERO, 0,
                List.of(interval(2, 6, DefermentType.INTEREST_ONLY),
                        interval(3, 4, DefermentType.CAPITALIZE)));
        assertThrows(IllegalArgumentException.class, () -> service.simulate(nested));
    }

    // ---------- 宽限结束期与提前还款同期：日内顺序稳定 ----------

    @Test
    void graceEndSamePeriodAsPrepayment_capitalizeThenPrepay() {
        BigDecimal prepay = new BigDecimal("10000.00");
        DefermentPolicySnapshot snapshot = policy(RepaymentMethod.EQUAL_INSTALLMENT,
                P, RATE, PERIODS, prepay, 4,
                List.of(interval(2, 4, DefermentType.CAPITALIZE)));

        DefermentComparison c = service.simulate(snapshot);
        DefermentRow r3 = row(c, 3);
        DefermentRow r4 = row(c, 4);

        // 日内顺序：计提利息 → 资本化 → 提前还款。第 4 期事件顺序固定。
        assertEquals(List.of("CAPITALIZE", "PREPAYMENT"), r4.events());
        // 先资本化（余额增加当期利息），再扣提前还款
        assertEquals(r3.balance().add(r4.accruedInterest()).subtract(prepay), r4.balance());
        assertEquals(r4.accruedInterest(), r4.capitalized());

        // 顺序稳定：同一快照重复计算结果完全一致
        DefermentComparison again = service.simulate(snapshot);
        assertEquals(c, again);
    }

    @Test
    void graceEndSamePeriodAsPrepayment_catchUpThenPrepay() {
        BigDecimal prepay = new BigDecimal("10000.00");
        DefermentComparison c = service.simulate(policy(RepaymentMethod.EQUAL_INSTALLMENT,
                P, RATE, PERIODS, prepay, 4,
                List.of(interval(2, 4, DefermentType.LUMP_SUM))));

        DefermentRow r4 = row(c, 4);
        // 日内顺序：先一次性补缴，再提前还款
        assertEquals(List.of("CATCH_UP", "PREPAYMENT"), r4.events());
        DefermentRow r3 = row(c, 3);
        assertEquals(r3.balance().subtract(r4.principal()).subtract(prepay), r4.balance());
        assertPlanConsistent(c.deferred().schedule(), P, prepay,
                c.deferred().summary().capitalizedAmount());
    }

    // ---------- 延期导致期限变化：尾期精确结清 ----------

    @Test
    void termExtended_finalPeriodSettlesExactly() {
        // 尾部资本化区间推高余额，期限相应延长
        DefermentComparison c = service.simulate(policy(RepaymentMethod.EQUAL_INSTALLMENT,
                P, RATE, PERIODS, ZERO, 0,
                List.of(interval(10, 11, DefermentType.CAPITALIZE))));

        assertEquals(PERIODS, c.original().summary().periods());
        assertTrue(c.deferred().summary().periods() > PERIODS, "延期后期限应变长");
        assertEquals(c.deferred().summary().periods() - PERIODS, c.diff().addedPeriods());

        List<DefermentRow> rows = c.deferred().schedule();
        DefermentRow last = rows.get(rows.size() - 1);
        // 尾期精确结清：末期本金 = 上期期末余额，结清后余额恰好为 0
        assertEquals(rows.get(rows.size() - 2).balance(), last.principal());
        assertEquals(0, last.balance().compareTo(ZERO));
        assertPlanConsistent(rows, P, ZERO, c.deferred().summary().capitalizedAmount());
    }

    // ---------- 暂停后一次性补缴 ----------

    @Test
    void lumpSum_deferredThenCaughtUp() {
        DefermentComparison c = service.simulate(policy(RepaymentMethod.EQUAL_INSTALLMENT,
                P, RATE, PERIODS, ZERO, 0,
                List.of(interval(3, 4, DefermentType.LUMP_SUM))));

        BigDecimal paymentLevel = c.deferred().summary().monthlyPayment();
        DefermentRow r2 = row(c, 2);
        DefermentRow r3 = row(c, 3);
        DefermentRow r4 = row(c, 4);

        // 第 3 期（暂停期）：不还款，本息挂账，余额不变
        assertEquals(0, r3.payment().compareTo(ZERO));
        assertEquals(r2.balance(), r3.balance());
        assertEquals(List.of("DEFER"), r3.events());
        BigDecimal accrued3 = r3.accruedInterest();
        BigDecimal virtualPrincipal3 = paymentLevel.subtract(accrued3);

        // 第 4 期（结束期）：一次性补缴挂账本息 + 当期应还
        assertEquals(List.of("CATCH_UP"), r4.events());
        BigDecimal accrued4 = r4.accruedInterest();
        BigDecimal virtualPrincipal4 = paymentLevel.subtract(accrued4);
        assertEquals(accrued3.add(accrued4), r4.interest(), "补缴利息 = 挂账利息 + 当期利息");
        assertEquals(virtualPrincipal3.add(virtualPrincipal4), r4.principal(), "补缴本金 = 两期应还本金");
        assertEquals(r2.balance().subtract(r4.principal()), r4.balance());
        // 挂账利息不计复利：第 4 期计提基数仍是第 2 期后的余额
        assertEquals(accrued3, accrued4);

        // 延期利息 = 区间内两期计提利息；无资本化
        assertEquals(accrued3.add(accrued4), c.deferred().summary().defermentInterest());
        assertEquals(ZERO, c.deferred().summary().capitalizedAmount());
        assertPlanConsistent(c.deferred().schedule(), P, ZERO, ZERO);
    }

    // ---------- 无宽限区间：延期方案与原方案一致 ----------

    @Test
    void emptyIntervals_deferredEqualsOriginal() {
        DefermentComparison c = service.simulate(policy(RepaymentMethod.EQUAL_INSTALLMENT,
                P, RATE, PERIODS, new BigDecimal("20000.00"), 0, List.of()));

        assertEquals(c.original().summary(), c.deferred().summary());
        assertEquals(c.original().schedule(), c.deferred().schedule());
        assertEquals(0, c.diff().addedPeriods());
        assertEquals(0, c.diff().interestDiff().compareTo(ZERO));
        assertEquals(ZERO, c.deferred().summary().capitalizedAmount());
        assertEquals(ZERO, c.deferred().summary().defermentInterest());
    }

    // ---------- 提前还款期次：0 = 计划开始前立即扣减 ----------

    @Test
    void prepaymentAtPeriodZero_appliesBeforeFirstPeriod() {
        BigDecimal prepay = new BigDecimal("20000.00");
        DefermentComparison c = service.simulate(policy(RepaymentMethod.EQUAL_INSTALLMENT,
                P, RATE, PERIODS, prepay, 0, List.of()));

        // 首期计息基数 = 100,000 − 20,000 = 80,000
        assertEquals(new BigDecimal("800.00"), row(c, 1).accruedInterest());
        assertFalse(row(c, 1).events().contains("PREPAYMENT"));
        assertPlanConsistent(c.deferred().schedule(), P, prepay, ZERO);
    }

    // ---------- 等额本金 ----------

    @Test
    void equalPrincipal_capitalizeExtendsTerm() {
        // 120,000 / 12 期：月还本金 10,000
        DefermentComparison c = service.simulate(policy(RepaymentMethod.EQUAL_PRINCIPAL,
                new BigDecimal("120000.00"), RATE, PERIODS, ZERO, 0,
                List.of(interval(2, 2, DefermentType.CAPITALIZE))));

        List<DefermentRow> rows = c.deferred().schedule();
        assertEquals(new BigDecimal("1200.00"), rows.get(0).accruedInterest());
        assertEquals(new BigDecimal("10000.00"), rows.get(0).principal());
        // 第 2 期资本化：计提 1,100.00 计入本金
        assertEquals(new BigDecimal("1100.00"), rows.get(1).capitalized());
        assertEquals(new BigDecimal("111100.00"), rows.get(1).balance());
        // 第 3 期计息基数增加：111,100 × 0.01 = 1,111.00
        assertEquals(new BigDecimal("1111.00"), rows.get(2).accruedInterest());
        // 期限延长：111,100 需 12 期还本（11 × 10,000 + 1,100），共 14 期
        assertEquals(PERIODS, c.original().summary().periods());
        assertEquals(14, c.deferred().summary().periods());
        assertEquals(2, c.diff().addedPeriods());
        DefermentRow last = rows.get(rows.size() - 1);
        assertEquals(new BigDecimal("1100.00"), last.principal());
        assertEquals(0, last.balance().compareTo(ZERO));
        assertNull(c.deferred().summary().monthlyPayment(), "等额本金无固定月供");
        assertPlanConsistent(rows, new BigDecimal("120000.00"), ZERO,
                c.deferred().summary().capitalizedAmount());
    }

    // ---------- 参数校验 ----------

    @Test
    void validation_invalidIntervalsAndPrepayment() {
        // 结束期小于起始期
        assertThrows(IllegalArgumentException.class, () -> service.simulate(policy(
                RepaymentMethod.EQUAL_INSTALLMENT, P, RATE, PERIODS, ZERO, 0,
                List.of(interval(5, 3, DefermentType.INTEREST_ONLY)))));
        // 区间超出剩余期数
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.simulate(policy(
                RepaymentMethod.EQUAL_INSTALLMENT, P, RATE, PERIODS, ZERO, 0,
                List.of(interval(10, 13, DefermentType.INTEREST_ONLY)))));
        assertTrue(e.getMessage().contains("超出剩余期数"), e.getMessage());
        // 提前还款期次超出剩余期数
        assertThrows(IllegalArgumentException.class, () -> service.simulate(policy(
                RepaymentMethod.EQUAL_INSTALLMENT, P, RATE, PERIODS,
                new BigDecimal("1000.00"), 13, List.of())));
        // 提前还款金额 ≥ 剩余本金
        assertThrows(IllegalArgumentException.class, () -> service.simulate(policy(
                RepaymentMethod.EQUAL_INSTALLMENT, P, RATE, PERIODS,
                P, 0, List.of())));
        // 提前还款金额 ≥ 应用时点的剩余本金（第 6 期时余额已不足 99,999）
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> service.simulate(policy(
                RepaymentMethod.EQUAL_INSTALLMENT, P, RATE, PERIODS,
                new BigDecimal("99999.00"), 6, List.of())));
        assertTrue(e2.getMessage().contains("第 6 期"), e2.getMessage());
    }

    // ---------- 复现：同一快照重复计算结果一致 ----------

    @Test
    void reproduce_sameSnapshotSameResult() {
        DefermentPolicySnapshot snapshot = policy(RepaymentMethod.EQUAL_INSTALLMENT,
                P, RATE, PERIODS, new BigDecimal("5000.00"), 7,
                List.of(interval(2, 3, DefermentType.INTEREST_ONLY),
                        interval(5, 6, DefermentType.CAPITALIZE),
                        interval(8, 9, DefermentType.LUMP_SUM)));
        DefermentComparison first = service.simulate(snapshot);
        DefermentComparison second = service.simulate(snapshot);
        assertEquals(first, second, "同一政策快照重复计算必须完全一致");
        assertPlanConsistent(first.deferred().schedule(), P, new BigDecimal("5000.00"),
                first.deferred().summary().capitalizedAmount());
    }

    // ---------- 通用一致性校验 ----------

    /**
     * 校验一份延期模拟计划的通用不变量：
     * 每期月供 = 本金 + 实付利息；余额逐期推演正确（含资本化与提前还款）；
     * 全程无负余额；尾期后余额为 0；本金合计 = 期初本金 − 提前还款 + 资本化金额。
     */
    private static void assertPlanConsistent(List<DefermentRow> rows, BigDecimal principal,
                                             BigDecimal prepayment, BigDecimal capitalizedAmount) {
        // 提前还款若未出现在任何期次事件中，说明在计划开始前（期次 0）已扣减
        boolean prepayInRows = rows.stream().anyMatch(r -> r.events().contains("PREPAYMENT"));
        BigDecimal balance = prepayment.signum() > 0 && !prepayInRows
                ? principal.subtract(prepayment)
                : principal;
        BigDecimal totalPrincipal = BigDecimal.ZERO;
        for (DefermentRow row : rows) {
            assertEquals(row.principal().add(row.interest()), row.payment(),
                    "第 " + row.period() + " 期月供 = 本金 + 利息");
            balance = balance.add(row.capitalized()).subtract(row.principal());
            if (row.events().contains("PREPAYMENT")) {
                balance = balance.subtract(prepayment);
            }
            assertEquals(0, balance.compareTo(row.balance()),
                    "第 " + row.period() + " 期余额推演不一致: " + balance + " vs " + row.balance());
            assertTrue(row.balance().signum() >= 0, "第 " + row.period() + " 期出现负余额");
            totalPrincipal = totalPrincipal.add(row.principal());
        }
        assertEquals(0, balance.compareTo(ZERO), "尾期后余额必须为 0");
        assertEquals(0, totalPrincipal.compareTo(principal.subtract(prepayment).add(capitalizedAmount)),
                "本金合计 = 期初本金 − 提前还款 + 资本化金额");
    }
}
