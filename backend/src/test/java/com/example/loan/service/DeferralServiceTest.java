package com.example.loan.service;

import com.example.loan.api.DeferralComparison;
import com.example.loan.api.DeferralPolicyRequest;
import com.example.loan.api.DeferralPolicyView;
import com.example.loan.api.DeferralReplayResponse;
import com.example.loan.api.DeferralSimulateRequest;
import com.example.loan.api.DeferralSimulationResponse;
import com.example.loan.api.GraceInterval;
import com.example.loan.domain.DeferralMode;
import com.example.loan.domain.RepaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 延期模拟的持久化与政策快照测试（H2 内存库）。
 * 验收点：历史结果在政策修改后仍按原快照可复现。
 */
@SpringBootTest
class DeferralServiceTest {

    @Autowired
    private DeferralService deferralService;

    private static DeferralSimulateRequest manualRequest(Long policyId, List<GraceInterval> intervals) {
        return new DeferralSimulateRequest(
                null, RepaymentMethod.EQUAL_INSTALLMENT,
                new BigDecimal("0.12"), new BigDecimal("100000.00"), 12,
                policyId, intervals,
                new BigDecimal("10000.00"), 5, BigDecimal.ZERO);
    }

    @Test
    void historicalRecordReproducibleAfterPolicyModified() {
        // 1. 创建政策：第 2~3 期只还利息
        DeferralPolicyView policy = deferralService.createPolicy(new DeferralPolicyRequest(
                "标准宽限", List.of(new GraceInterval(2, 3, DeferralMode.INTEREST_ONLY))));

        // 2. 按政策模拟并保存记录
        DeferralSimulationResponse created = deferralService.simulateAndSave(manualRequest(policy.id(), null));
        assertNotNull(created.recordId());
        assertEquals("标准宽限", created.policyName());
        assertEquals(List.of(new GraceInterval(2, 3, DeferralMode.INTEREST_ONLY)), created.intervals());

        // 3. 修改政策：区间改为第 2~4 期资本化（政策内容已完全不同）
        deferralService.updatePolicy(policy.id(), new DeferralPolicyRequest(
                "标准宽限 v2", List.of(new GraceInterval(2, 4, DeferralMode.CAPITALIZE))));

        // 4. 历史记录仍按原快照：查看结果与模拟时一致，快照内容未被新政策污染
        DeferralSimulationResponse loaded = deferralService.getRecord(created.recordId());
        assertEquals("标准宽限", loaded.policyName());
        assertEquals(List.of(new GraceInterval(2, 3, DeferralMode.INTEREST_ONLY)), loaded.intervals());
        assertEquals(created.comparison(), loaded.comparison());

        // 5. 按记录快照重算：与存档结果完全一致（可复现）
        DeferralReplayResponse replay = deferralService.replay(created.recordId());
        assertTrue(replay.matchesStored(), "政策修改后历史记录仍应按原快照复现");
        assertEquals(created.comparison(), replay.comparison());

        // 6. 新政策对新的模拟生效（确认政策确实被修改过）
        DeferralSimulationResponse after = deferralService.simulateAndSave(manualRequest(policy.id(), null));
        assertEquals("标准宽限 v2", after.policyName());
        assertNotEquals(created.comparison().deferred().summary(), after.comparison().deferred().summary());
    }

    @Test
    void inlineIntervalsSavedAsSnapshot() {
        // 不使用政策、内联区间：记录同样保存快照并可复现
        List<GraceInterval> intervals = List.of(
                new GraceInterval(3, 4, DeferralMode.DEFER_LUMPSUM),
                new GraceInterval(6, 7, DeferralMode.INTEREST_ONLY));
        DeferralSimulationResponse created = deferralService.simulateAndSave(manualRequest(null, intervals));
        assertNull(created.policyName());

        DeferralReplayResponse replay = deferralService.replay(created.recordId());
        assertTrue(replay.matchesStored());
        assertEquals(created.comparison(), replay.comparison());
        assertEquals(intervals, deferralService.getRecord(created.recordId()).intervals());
    }

    @Test
    void policyAndInlineIntervalsAreExclusive() {
        DeferralPolicyView policy = deferralService.createPolicy(new DeferralPolicyRequest(
                "互斥校验", List.of(new GraceInterval(2, 3, DeferralMode.INTEREST_ONLY))));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                deferralService.simulateAndSave(manualRequest(policy.id(),
                        List.of(new GraceInterval(4, 5, DeferralMode.CAPITALIZE)))));
        assertTrue(e.getMessage().contains("二选一"), e.getMessage());
    }

    @Test
    void overlappingIntervalsRejectedAtService() {
        assertThrows(IllegalArgumentException.class, () ->
                deferralService.simulateAndSave(manualRequest(null, List.of(
                        new GraceInterval(2, 4, DeferralMode.INTEREST_ONLY),
                        new GraceInterval(4, 6, DeferralMode.CAPITALIZE)))));
    }

    @Test
    void comparisonStructurePersisted() {
        // 结果 JSON 完整往返：原方案 / 延期方案 / 差异逐字段一致
        DeferralSimulationResponse created = deferralService.simulateAndSave(manualRequest(null,
                List.of(new GraceInterval(2, 3, DeferralMode.CAPITALIZE))));
        DeferralComparison loaded = deferralService.getRecord(created.recordId()).comparison();
        assertEquals(created.comparison().original(), loaded.original());
        assertEquals(created.comparison().deferred(), loaded.deferred());
        assertEquals(created.comparison().diff(), loaded.diff());
    }
}
