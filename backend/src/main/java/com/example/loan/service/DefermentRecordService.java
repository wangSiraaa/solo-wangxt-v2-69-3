package com.example.loan.service;

import com.example.loan.api.DefermentComparison;
import com.example.loan.api.DefermentPolicySnapshot;
import com.example.loan.api.DefermentRecordView;
import com.example.loan.api.DefermentRequest;
import com.example.loan.api.DefermentResponse;
import com.example.loan.api.ReproduceResult;
import com.example.loan.domain.DefermentRecord;
import com.example.loan.domain.LoanContract;
import com.example.loan.domain.RepaymentMethod;
import com.example.loan.repo.DefermentRecordRepository;
import com.example.loan.repo.LoanContractRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 编排宽限与延期模拟：生成政策快照、执行计算并持久化；
 * 历史记录按保存的快照复现，与之后政策的修改互不影响。
 */
@Service
public class DefermentRecordService {

    private final DefermentService defermentService;
    private final DefermentRecordRepository recordRepository;
    private final LoanContractRepository contractRepository;
    private final ObjectMapper objectMapper;

    public DefermentRecordService(DefermentService defermentService,
                                  DefermentRecordRepository recordRepository,
                                  LoanContractRepository contractRepository,
                                  ObjectMapper objectMapper) {
        this.defermentService = defermentService;
        this.recordRepository = recordRepository;
        this.contractRepository = contractRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行模拟并保存政策快照与结果。
     */
    @Transactional
    public DefermentResponse simulateAndSave(DefermentRequest req) {
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

        int prepaymentPeriod = req.prepaymentPeriod() == null ? 0 : req.prepaymentPeriod();
        DefermentPolicySnapshot policy = new DefermentPolicySnapshot(
                DefermentPolicySnapshot.CURRENT_VERSION,
                method, annualRate, remainingPrincipal, remainingPeriods,
                req.prepaymentAmount(), prepaymentPeriod, req.fee(),
                req.intervals() == null ? List.of() : req.intervals());

        DefermentComparison result = defermentService.simulate(policy);

        DefermentRecord record = new DefermentRecord();
        record.setContract(contract);
        record.setMethod(method);
        record.setAnnualRate(annualRate);
        record.setRemainingPrincipal(remainingPrincipal);
        record.setRemainingPeriods(remainingPeriods);
        record.setPrepaymentAmount(req.prepaymentAmount());
        record.setPrepaymentPeriod(prepaymentPeriod);
        record.setFee(req.fee());
        // 保存归一化后的快照（区间已排序），复现时以它为准
        record.setPolicyJson(toJson(result.policy()));
        record.setResultJson(toJson(result));
        recordRepository.save(record);

        return new DefermentResponse(record.getId(), result);
    }

    @Transactional(readOnly = true)
    public List<DefermentRecordView> listRecords() {
        return recordRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public DefermentResponse getRecord(long id) {
        DefermentRecord record = findRecord(id);
        return new DefermentResponse(record.getId(), fromJson(record.getResultJson(), DefermentComparison.class));
    }

    /**
     * 按记录中保存的政策快照重新计算，并与保存的结果比对。
     * 政策（区间、提前还款安排等）之后被修改也不影响本记录的复现。
     */
    @Transactional(readOnly = true)
    public ReproduceResult reproduce(long id) {
        DefermentRecord record = findRecord(id);
        DefermentPolicySnapshot snapshot = fromJson(record.getPolicyJson(), DefermentPolicySnapshot.class);
        DefermentComparison recomputed = defermentService.simulate(snapshot);
        DefermentComparison stored = fromJson(record.getResultJson(), DefermentComparison.class);
        return new ReproduceResult(record.getId(), recomputed.equals(stored), recomputed);
    }

    private DefermentRecord findRecord(long id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("延期模拟记录不存在: id=" + id));
    }

    private DefermentRecordView toView(DefermentRecord record) {
        DefermentComparison result = fromJson(record.getResultJson(), DefermentComparison.class);
        LoanContract contract = record.getContract();
        return new DefermentRecordView(
                record.getId(),
                record.getCreatedAt(),
                contract == null ? null : contract.getContractNo(),
                record.getMethod(),
                record.getAnnualRate(),
                record.getRemainingPrincipal(),
                record.getRemainingPeriods(),
                record.getPrepaymentAmount(),
                record.getPrepaymentPeriod(),
                result.policy().intervals().size(),
                result.original().summary().periods(),
                result.deferred().summary().periods(),
                result.diff().addedPeriods(),
                result.deferred().summary().defermentInterest(),
                result.deferred().summary().capitalizedAmount(),
                result.diff().interestDiff());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("延期模拟结果序列化失败", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("延期模拟记录反序列化失败", e);
        }
    }
}
