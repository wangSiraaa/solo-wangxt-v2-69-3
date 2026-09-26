package com.example.loan.service;

import com.example.loan.api.DeferralComparison;
import com.example.loan.api.DeferralEventType;
import com.example.loan.api.DeferralPlan;
import com.example.loan.api.DeferralRow;
import com.example.loan.api.GraceInterval;
import com.example.loan.domain.DeferralMode;
import com.example.loan.domain.RepaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 宽限与延期模拟引擎测试。
 *
 * 等额本息示例：剩余本金 100,000.00，年利率 12%（月利率 0.01），剩余 12 期，
 * 月供 M = 8,884.88。逐期锚点（无区间时）：
 * 第 1 期 息 1,000.00 / 本 7,884.88 / 余 92,115.12；
 * 第 2 期 息 921.15 / 本 7,963.73 / 余 84,151.39；
 * 第 3 期 息 841.51 / 本 8,043.37 / 余 76,108.02；
 * 第 4 期 息 761.08 / 本 8,123.80 / 余 67,984.22。
 */
class DeferralSimulationServiceTest {

    private final DeferralSimulationService service =
            new DeferralSimulationService(new AmortizationService());

    private static final BigDecimal PRINCIPAL = new BigDecimal("100000.00");
    private static final BigDecimal RATE = new BigDecimal("0.12"); // 月利率 0.01
    private static final int PERIODS = 12;
    private static final BigDecimal PAYMENT = new BigDecimal("8884.88");
    private static final BigDecimal ZERO_FEE = BigDecimal.ZERO;

    private DeferralComparison simulateEi(List<GraceInterval> intervals) {
        return simulateEi(intervals, BigDecimal.ZERO, null);
    }

    private DeferralComparison simulateEi(List<GraceInterval> intervals,
                                          BigDecimal prepayAmount, Integer prepayPeriod) {
        return service.simulate(RepaymentMethod.EQUAL_INSTALLMENT, RATE, PRINCIPAL, PERIODS,
                prepayAmount, prepayPeriod, ZERO_FEE, intervals);
    }

    // ---------- 只还利息：期间本金不变，期限顺延 ----------

    @Test
    void interestOnly_principalUnchangedDuringGrace() {
        DeferralComparison result = simulateEi(List.of(
                new GraceInterval(3, 5, DeferralMode.INTEREST_ONLY)));
        DeferralPlan deferred = result.deferred();
        List<DeferralRow> rows = deferred.schedule();

        assertEquals(new BigDecimal("8884.88"), deferred.summary().monthlyPayment());
        // 前两期正常还款后余额 84,151.39
        assertMoney("84151.39", rows.get(1).balance());
        // 第 3~5 期只还利息：本金为 0、余额不变、月供 = 当期利息
        for (int p = 3; p <= 5; p++) {
            DeferralRow row = rows.get(p - 1);
            assertEquals(DeferralMode.INTEREST_ONLY, row.mode(), "第 " + p + " 期模式");
            assertMoney("841.51", row.interest());
            assertMoney("841.51", row.payment());
            assertEquals(0, row.principal().signum(), "只还利息期间本金应为 0");
            assertMoney("84151.39", row.balance());
        }
        // 宽限结束后第一期：计息基数与还款水平不变
        DeferralRow after = rows.get(5);
        assertEquals(DeferralMode.NORMAL, after.mode());
        assertMoney("841.51", after.interest());
        assertMoney("8043.37", after.principal());
        assertMoney("76108.02", after.balance());

        // 期限顺延 3 期（新增期数 = 区间长度），尾期精确结清
        assertEquals(12, result.original().summary().periods());
        assertEquals(15, deferred.summary().periods());
        assertEquals(3, result.diff().periodDiff());
        assertMoney("0.00", rows.get(rows.size() - 1).balance());

        // 延期利息 = 区间内 3 期利息；正常利息与原方案总利息一致（正常期次完全平移）
        assertMoney("2524.53", deferred.summary().deferredInterest());
        assertMoney("6618.53", deferred.summary().normalInterest());
        assertMoney("6618.53", result.original().summary().totalInterest());
        assertMoney("2524.53", result.diff().interestDiff());
        assertMoney("0.00", deferred.summary().capitalizedAmount());
        assertPlanConsistent(deferred, PRINCIPAL, BigDecimal.ZERO);
    }

    // ---------- 利息资本化：下一期计息基数增加 ----------

    @Test
    void capitalize_interestBaseIncreasesNextPeriod() {
        DeferralComparison result = simulateEi(List.of(
                new GraceInterval(3, 4, DeferralMode.CAPITALIZE)));
        DeferralPlan deferred = result.deferred();
        List<DeferralRow> rows = deferred.schedule();

        // 第 3 期：利息 841.51 全部资本化，余额 84,151.39 + 841.51 = 84,992.90
        DeferralRow p3 = rows.get(2);
        assertEquals(DeferralMode.CAPITALIZE, p3.mode());
        assertEquals(List.of(DeferralEventType.CAPITALIZE), p3.events());
        assertEquals(0, p3.payment().signum());
        assertMoney("841.51", p3.capitalized());
        assertMoney("84992.90", p3.balance());

        // 第 4 期：计息基数 = 资本化后的余额，利息 849.93 > 841.51
        DeferralRow p4 = rows.get(3);
        assertMoney("849.93", p4.interest());
        assertTrue(p4.interest().compareTo(p3.interest()) > 0, "资本化后下一期计息基数应增加");
        assertMoney("85842.83", p4.balance());

        // 第 5 期（退出后）：在更高的余额上计提利息 858.43
        DeferralRow p5 = rows.get(4);
        assertEquals(DeferralMode.NORMAL, p5.mode());
        assertMoney("858.43", p5.interest());
        assertMoney("8026.45", p5.principal());
        assertMoney("77816.38", p5.balance());

        // 资本化金额 = 两期利息合计；计划内本金 = 原剩余本金 + 资本化金额
        assertMoney("1691.44", deferred.summary().capitalizedAmount());
        assertMoney("1691.44", deferred.summary().deferredInterest());
        assertMoney("101691.44", deferred.summary().totalPrincipal());

        // 期限顺延且尾期精确结清（15 期，末期还款 1,887.07）
        assertEquals(15, deferred.summary().periods());
        assertEquals(3, result.diff().periodDiff());
        DeferralRow last = rows.get(rows.size() - 1);
        assertMoney("0.00", last.balance());
        assertMoney("1887.07", last.payment());
        assertPlanConsistent(deferred, PRINCIPAL, BigDecimal.ZERO);
    }

    // ---------- 相邻区间可处理，重叠区间被拒 ----------

    @Test
    void adjacentIntervalsAccepted() {
        DeferralComparison result = simulateEi(List.of(
                new GraceInterval(4, 5, DeferralMode.CAPITALIZE),
                new GraceInterval(2, 3, DeferralMode.INTEREST_ONLY))); // 乱序输入，内部排序
        List<DeferralRow> rows = result.deferred().schedule();
        assertEquals(DeferralMode.INTEREST_ONLY, rows.get(1).mode());
        assertEquals(DeferralMode.INTEREST_ONLY, rows.get(2).mode());
        assertEquals(DeferralMode.CAPITALIZE, rows.get(3).mode());
        assertEquals(DeferralMode.CAPITALIZE, rows.get(4).mode());
        assertEquals(DeferralMode.NORMAL, rows.get(5).mode());
        assertMoney("0.00", rows.get(rows.size() - 1).balance());
    }

    @Test
    void overlappingIntervalsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                simulateEi(List.of(
                        new GraceInterval(2, 4, DeferralMode.INTEREST_ONLY),
                        new GraceInterval(4, 6, DeferralMode.CAPITALIZE))));
        assertTrue(e.getMessage().contains("重叠"), e.getMessage());

        // 完全包含也算重叠
        assertThrows(IllegalArgumentException.class, () ->
                simulateEi(List.of(
                        new GraceInterval(2, 6, DeferralMode.INTEREST_ONLY),
                        new GraceInterval(3, 4, DeferralMode.CAPITALIZE))));
    }

    @Test
    void invalidIntervalsRejected() {
        // 结束期早于起始期
        assertThrows(IllegalArgumentException.class, () ->
                simulateEi(List.of(new GraceInterval(5, 3, DeferralMode.INTEREST_ONLY))));
        // 超出剩余期数
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                simulateEi(List.of(new GraceInterval(10, 13, DeferralMode.INTEREST_ONLY))));
        assertTrue(e.getMessage().contains("超出剩余期数"), e.getMessage());
        // 空区间列表
        assertThrows(IllegalArgumentException.class, () -> simulateEi(List.of()));
        // NORMAL 不是合法的区间模式
        assertThrows(IllegalArgumentException.class, () ->
                simulateEi(List.of(new GraceInterval(2, 3, DeferralMode.NORMAL))));
    }

    // ---------- 宽限结束日与提前还款同日：日内顺序稳定 ----------

    @Test
    void lumpSumExitAndPrepaymentSameDay_stableOrder() {
        // 「暂停后一次性补缴」区间 [3,4]，退出期（第 5 期）当天同时提前还款 10,000
        DeferralComparison result = simulateEi(
                List.of(new GraceInterval(3, 4, DeferralMode.DEFER_LUMPSUM)),
                new BigDecimal("10000.00"), 5);
        List<DeferralRow> rows = result.deferred().schedule();

        // 第 3、4 期挂账：当期不还款，应还本息 8,884.88 全部挂账
        for (int p = 3; p <= 4; p++) {
            DeferralRow row = rows.get(p - 1);
            assertEquals(DeferralMode.DEFER_LUMPSUM, row.mode());
            assertEquals(List.of(DeferralEventType.DEFER_ACCRUAL), row.events());
            assertEquals(0, row.payment().signum());
            assertMoney("8884.88", row.deferred());
            assertMoney("84151.39", row.balance());
        }

        // 第 5 期（宽限结束日）：先补缴、再提前还款、最后当期还款，顺序固定
        DeferralRow exit = rows.get(4);
        assertEquals(List.of(
                DeferralEventType.EXIT_LUMPSUM,
                DeferralEventType.PREPAYMENT,
                DeferralEventType.PERIOD_PAYMENT), exit.events());
        // 补缴 = 两期挂账 17,769.76（本金 16,086.74）；提前还款 10,000；当期还款 8,884.88
        assertMoney("17769.76", exit.lumpSumPaid());
        assertMoney("16086.74", exit.lumpSumPrincipal());
        assertMoney("10000.00", exit.prepayment());
        assertMoney("8884.88", exit.payment());
        assertMoney("8043.37", exit.principal());
        assertMoney("841.51", exit.interest());
        // 84,151.39 − 16,086.74（补缴本金）− 10,000（提前还款）− 8,043.37（当期本金）= 50,021.28
        assertMoney("50021.28", exit.balance());

        // 一次性补缴使期限回到原轨道：期数与原方案一致，但延期利息更高
        assertEquals(11, result.original().summary().periods());
        assertEquals(11, result.deferred().summary().periods());
        assertEquals(0, result.diff().periodDiff());
        assertMoney("1683.02", result.deferred().summary().deferredInterest());
        assertMoney("257.00", result.diff().interestDiff());
        assertMoney("0.00", rows.get(rows.size() - 1).balance());
        assertPlanConsistent(result.deferred(), PRINCIPAL, new BigDecimal("10000.00"));

        // 同样输入重复模拟，结果完全一致（顺序稳定）
        DeferralComparison again = simulateEi(
                List.of(new GraceInterval(3, 4, DeferralMode.DEFER_LUMPSUM)),
                new BigDecimal("10000.00"), 5);
        assertEquals(result, again);
    }

    @Test
    void capitalizeAndPrepaymentSameDay_stableOrder() {
        // 资本化区间 [3,4]，提前还款发生在区间最后一期（第 4 期）还款日
        DeferralComparison result = simulateEi(
                List.of(new GraceInterval(3, 4, DeferralMode.CAPITALIZE)),
                new BigDecimal("10000.00"), 4);
        DeferralRow p4 = result.deferred().schedule().get(3);
        // 当日顺序：先利息资本化结转，再提前还款
        assertEquals(List.of(DeferralEventType.CAPITALIZE, DeferralEventType.PREPAYMENT), p4.events());
        // 84,992.90 + 849.93（资本化）− 10,000（提前还款）= 75,842.83
        assertMoney("849.93", p4.capitalized());
        assertMoney("10000.00", p4.prepayment());
        assertMoney("75842.83", p4.balance());
        assertPlanConsistent(result.deferred(), PRINCIPAL, new BigDecimal("10000.00"));
    }

    // ---------- 等额本金 ----------

    @Test
    void equalPrincipal_interestOnly() {
        DeferralComparison result = service.simulate(
                RepaymentMethod.EQUAL_PRINCIPAL, RATE, new BigDecimal("120000.00"), 12,
                BigDecimal.ZERO, null, ZERO_FEE,
                List.of(new GraceInterval(2, 3, DeferralMode.INTEREST_ONLY)));
        List<DeferralRow> rows = result.deferred().schedule();

        // 月还本金 10,000；第 1 期后余额 110,000
        assertNull(result.deferred().summary().monthlyPayment());
        assertMoney("10000.00", rows.get(0).principal());
        assertMoney("110000.00", rows.get(0).balance());
        // 区间内只还利息 1,100.00，本金不变
        for (int p = 2; p <= 3; p++) {
            assertMoney("1100.00", rows.get(p - 1).payment());
            assertEquals(0, rows.get(p - 1).principal().signum());
            assertMoney("110000.00", rows.get(p - 1).balance());
        }
        // 期限顺延 2 期至 14 期，尾期精确结清
        assertEquals(14, result.deferred().summary().periods());
        assertEquals(2, result.diff().periodDiff());
        assertMoney("2200.00", result.deferred().summary().deferredInterest());
        assertMoney("0.00", rows.get(rows.size() - 1).balance());
        assertPlanConsistent(result.deferred(), new BigDecimal("120000.00"), BigDecimal.ZERO);
    }

    @Test
    void equalPrincipal_capitalizeExtendsTerm() {
        DeferralComparison result = service.simulate(
                RepaymentMethod.EQUAL_PRINCIPAL, RATE, new BigDecimal("120000.00"), 12,
                BigDecimal.ZERO, null, ZERO_FEE,
                List.of(new GraceInterval(2, 3, DeferralMode.CAPITALIZE)));
        List<DeferralRow> rows = result.deferred().schedule();

        // 第 2 期资本化 1,100.00 → 111,100；第 3 期计息基数增加 → 1,111.00 → 112,211
        assertMoney("1100.00", rows.get(1).capitalized());
        assertMoney("111100.00", rows.get(1).balance());
        assertMoney("1111.00", rows.get(2).interest());
        assertMoney("112211.00", rows.get(2).balance());
        // 退出后月还本金仍为 10,000，期限顺延至 15 期，尾期精确结清
        assertMoney("10000.00", rows.get(3).principal());
        assertEquals(15, result.deferred().summary().periods());
        assertMoney("2211.00", result.deferred().summary().capitalizedAmount());
        assertMoney("0.00", rows.get(rows.size() - 1).balance());
        assertPlanConsistent(result.deferred(), new BigDecimal("120000.00"), BigDecimal.ZERO);
    }

    // ---------- 参数校验 ----------

    @Test
    void prepaymentExceedingBalanceRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                simulateEi(List.of(new GraceInterval(2, 3, DeferralMode.INTEREST_ONLY)),
                        new BigDecimal("999999.00"), 1));
        assertTrue(e.getMessage().contains("全额结清"), e.getMessage());
    }

    @Test
    void prepaymentPeriodOutOfRangeRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                simulateEi(List.of(new GraceInterval(2, 3, DeferralMode.INTEREST_ONLY)),
                        new BigDecimal("1000.00"), 13));
    }

    @Test
    void zeroPrepaymentHasNoPrepaymentEvent() {
        DeferralComparison result = simulateEi(List.of(
                new GraceInterval(2, 3, DeferralMode.INTEREST_ONLY)));
        assertTrue(result.deferred().schedule().stream()
                .flatMap(r -> r.events().stream())
                .noneMatch(e -> e == DeferralEventType.PREPAYMENT));
        assertEquals(0, result.deferred().summary().prepayment().signum());
    }

    // ---------- 工具 ----------

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "期望 " + expected + "，实际 " + actual);
    }

    /**
     * 核对延期计划的自洽性：逐期余额结转、尾期精确结清、无负余额、
     * 本金守恒（计划内本金 + 提前还款 = 原剩余本金 + 资本化金额）、
     * 利息分列守恒（总利息 = 正常利息 + 延期利息）、现金守恒（总还款 = 原剩余本金 + 总利息）。
     */
    private static void assertPlanConsistent(DeferralPlan plan, BigDecimal principal0, BigDecimal prepayment) {
        BigDecimal balance = principal0;
        for (DeferralRow row : plan.schedule()) {
            BigDecimal expected = balance
                    .subtract(row.lumpSumPrincipal())
                    .subtract(row.prepayment())
                    .add(row.capitalized())
                    .subtract(row.principal());
            assertEquals(0, expected.compareTo(row.balance()),
                    "第 " + row.period() + " 期余额结转错误");
            assertTrue(row.balance().signum() >= 0, "第 " + row.period() + " 期出现负余额");
            balance = row.balance();
        }
        var s = plan.summary();
        assertEquals(0, BigDecimal.ZERO.compareTo(balance), "末期后余额应恰好为 0");
        assertEquals(0, principal0.add(s.capitalizedAmount())
                        .compareTo(s.totalPrincipal().add(s.prepayment())),
                "本金守恒：计划内本金 + 提前还款 = 原剩余本金 + 资本化金额");
        assertEquals(0, s.totalInterest().compareTo(s.normalInterest().add(s.deferredInterest())),
                "总利息 = 正常利息 + 延期利息");
        assertEquals(0, s.totalPaid().compareTo(principal0.add(s.totalInterest())),
                "现金守恒：总还款 = 原剩余本金 + 总利息");
        assertEquals(0, s.totalPaid().compareTo(
                        s.scheduledPayments().add(s.lumpSumPaid()).add(s.prepayment())),
                "总还款 = 计划还款 + 一次性补缴 + 提前还款");
        assertEquals(0, s.totalCost().compareTo(s.totalPaid().add(s.fee())));
        assertEquals(0, prepayment.compareTo(s.prepayment()));
    }
}
