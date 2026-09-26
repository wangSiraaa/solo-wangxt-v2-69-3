package com.example.loan.service;

import com.example.loan.api.DeferralComparison;
import com.example.loan.api.DeferralPolicyRequest;
import com.example.loan.api.DeferralPolicyView;
import com.example.loan.api.DeferralRecordSummaryView;
import com.example.loan.api.DeferralReplayResponse;
import com.example.loan.api.DeferralSimulateRequest;
import com.example.loan.api.DeferralSimulationResponse;
import com.example.loan.api.GraceInterval;
import com.example.loan.domain.DeferralPolicy;
import com.example.loan.domain.DeferralSimulationRecord;
import com.example.loan.domain.LoanContract;
import com.example.loan.domain.RepaymentMethod;
import com.example.loan.repo.DeferralPolicyRepository;
import com.example.loan.repo.DeferralSimulationRecordRepository;
import com.example.loan.repo.LoanContractRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 编排宽限与延期模拟：延期政策维护、模拟计算与记录持久化。
 * 每次模拟把当时使用的宽限区间作为政策快照随记录保存，
 * 政策之后被修改，历史记录仍按原快照复现。
 */
@Service
public class DeferralService {

    private final DeferralSimulationService simulationService;
    private final DeferralPolicyRepository policyRepository;
    private final DeferralSimulationRecordRepository recordRepository;
    private final LoanContractRepository contractRepository;
    private final ObjectMapper objectMapper;

    public DeferralService(DeferralSimulationService simulationService,
                           DeferralPolicyRepository policyRepository,
                           DeferralSimulationRecordRepository recordRepository,
                           LoanContractRepository contractRepository,
                           ObjectMapper objectMapper) {
        this.simulationService = simulationService;
        this.policyRepository = policyRepository;
        this.recordRepository = recordRepository;
        this.contractRepository = contractRepository;
        this.objectMapper = objectMapper;
    }

    // ---------- 延期政策 ----------

    @Transactional(readOnly = true)
    public List<DeferralPolicyView> listPolicies() {
        return policyRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(this::toPolicyView)
                .toList();
    }

    @Transactional
    public DeferralPolicyView createPolicy(DeferralPolicyRequest req) {
        List<GraceInterval> intervals = simulationService.validateIntervals(req.intervals());
        DeferralPolicy policy = new DeferralPolicy();
        policy.setName(req.name().trim());
        policy.setIntervalsJson(intervalsToJson(intervals));
        return toPolicyView(policyRepository.save(policy));
    }

    /** 修改政策。已保存的模拟记录持有各自的政策快照，不受本次修改影响。 */
    @Transactional
    public DeferralPolicyView updatePolicy(long id, DeferralPolicyRequest req) {
        DeferralPolicy policy = policyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("延期政策不存在: id=" + id));
        List<GraceInterval> intervals = simulationService.validateIntervals(req.intervals());
        policy.setName(req.name().trim());
        policy.setIntervalsJson(intervalsToJson(intervals));
        policy.setUpdatedAt(Instant.now());
        return toPolicyView(policyRepository.save(policy));
    }

    // ---------- 模拟与记录 ----------

    /**
     * 执行延期模拟并保存记录（含政策快照与完整结果）。
     */
    @Transactional
    public DeferralSimulationResponse simulateAndSave(DeferralSimulateRequest req) {
        LoanContract contract = null;
        RepaymentMethod method;
        BigDecimal annualRate;
        BigDecimal remainingPrincipal;
        int remainingPeriods;

        if (req.contractId() != null) {
            contract = contractRepository.findById(req.contractId())
                    .orElseThrow(() -> new IllegalArgumentException("模拟合同不存在: id=" + req.contractId()));
            method = contract.getMethod();
            annualRate = contract.getAnnualRate();
            remainingPrincipal = contract.getRemainingPrincipal();
            remainingPeriods = contract.getRemainingPeriods();
        } else {
            method = req.method();
            annualRate = req.annualRate();
            remainingPrincipal = req.remainingPrincipal();
            if (req.remainingPeriods() == null) {
                throw new IllegalArgumentException("剩余期数不能为空");
            }
            remainingPeriods = req.remainingPeriods();
        }

        // 宽限区间：政策 或 内联，二选一
        DeferralPolicy policy = null;
        String policyName = null;
        List<GraceInterval> intervals;
        if (req.policyId() != null) {
            if (req.intervals() != null && !req.intervals().isEmpty()) {
                throw new IllegalArgumentException("延期政策与内联区间只能二选一");
            }
            policy = policyRepository.findById(req.policyId())
                    .orElseThrow(() -> new IllegalArgumentException("延期政策不存在: id=" + req.policyId()));
            policyName = policy.getName();
            intervals = intervalsFromJson(policy.getIntervalsJson());
        } else {
            intervals = req.intervals();
        }
        // 校验并排序（模拟引擎内部会再次校验，这里先得到排序后的区间作为快照）
        List<GraceInterval> snapshot = simulationService.validateIntervals(intervals, remainingPeriods);

        DeferralComparison comparison = simulationService.simulate(
                method, annualRate, remainingPrincipal, remainingPeriods,
                req.prepaymentAmount(), req.prepaymentPeriod(), req.fee(), snapshot);
        int prepayAt = req.prepaymentPeriod() == null ? 1 : req.prepaymentPeriod();

        DeferralSimulationRecord record = new DeferralSimulationRecord();
        record.setContract(contract);
        record.setPolicy(policy);
        record.setPolicyName(policyName);
        record.setMethod(method);
        record.setAnnualRate(annualRate);
        record.setRemainingPrincipal(remainingPrincipal);
        record.setRemainingPeriods(remainingPeriods);
        record.setPrepaymentAmount(req.prepaymentAmount());
        record.setPrepaymentPeriod(prepayAt);
        record.setFee(req.fee());
        record.setPolicySnapshotJson(intervalsToJson(snapshot));
        record.setResultJson(toJson(comparison));
        recordRepository.save(record);

        return new DeferralSimulationResponse(record.getId(), method, policyName, snapshot, comparison);
    }

    @Transactional(readOnly = true)
    public List<DeferralRecordSummaryView> listRecords() {
        return recordRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toSummaryView)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeferralSimulationResponse getRecord(long id) {
        DeferralSimulationRecord record = findRecord(id);
        return new DeferralSimulationResponse(record.getId(), record.getMethod(), record.getPolicyName(),
                intervalsFromJson(record.getPolicySnapshotJson()), fromJson(record.getResultJson()));
    }

    /**
     * 按记录保存的政策快照与输入参数重新计算，验证历史结果可复现。
     * 即使关联政策之后被修改，重算仍使用记录自身的快照。
     */
    @Transactional(readOnly = true)
    public DeferralReplayResponse replay(long id) {
        DeferralSimulationRecord record = findRecord(id);
        List<GraceInterval> snapshot = intervalsFromJson(record.getPolicySnapshotJson());
        DeferralComparison recomputed = simulationService.simulate(
                record.getMethod(), record.getAnnualRate(), record.getRemainingPrincipal(),
                record.getRemainingPeriods(), record.getPrepaymentAmount(), record.getPrepaymentPeriod(),
                record.getFee(), snapshot);
        DeferralComparison stored = fromJson(record.getResultJson());
        return new DeferralReplayResponse(record.getId(), stored.equals(recomputed), recomputed);
    }

    // ---------- 内部 ----------

    private DeferralSimulationRecord findRecord(long id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("延期模拟记录不存在: id=" + id));
    }

    private DeferralPolicyView toPolicyView(DeferralPolicy policy) {
        return new DeferralPolicyView(policy.getId(), policy.getName(),
                intervalsFromJson(policy.getIntervalsJson()), policy.getCreatedAt(), policy.getUpdatedAt());
    }

    private DeferralRecordSummaryView toSummaryView(DeferralSimulationRecord record) {
        DeferralComparison result = fromJson(record.getResultJson());
        int intervalCount = intervalsFromJson(record.getPolicySnapshotJson()).size();
        LoanContract contract = record.getContract();
        return new DeferralRecordSummaryView(
                record.getId(),
                record.getCreatedAt(),
                contract == null ? null : contract.getContractNo(),
                record.getPolicyName(),
                record.getMethod(),
                record.getAnnualRate(),
                record.getRemainingPrincipal(),
                record.getRemainingPeriods(),
                record.getPrepaymentAmount(),
                record.getPrepaymentPeriod(),
                record.getFee(),
                intervalCount,
                result.original().summary().periods(),
                result.deferred().summary().periods(),
                result.diff().periodDiff(),
                result.deferred().summary().deferredInterest(),
                result.deferred().summary().capitalizedAmount(),
                result.diff().interestDiff());
    }

    private String intervalsToJson(List<GraceInterval> intervals) {
        try {
            return objectMapper.writeValueAsString(intervals);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("宽限区间序列化失败", e);
        }
    }

    private List<GraceInterval> intervalsFromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("宽限区间反序列化失败", e);
        }
    }

    private String toJson(DeferralComparison result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("模拟结果序列化失败", e);
        }
    }

    private DeferralComparison fromJson(String json) {
        try {
            return objectMapper.readValue(json, DeferralComparison.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("模拟记录反序列化失败", e);
        }
    }
}
