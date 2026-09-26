package com.example.loan.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 一次宽限与延期模拟的记录。输入参数、政策快照与完整结果全部冻结保存，
 * 政策之后被修改也不影响本记录按原快照复现。
 */
@Entity
@Table(name = "deferral_simulation")
public class DeferralSimulationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的模拟合同；手工录入参数时为空。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private LoanContract contract;

    /** 关联的延期政策；使用内联区间时为空。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id")
    private DeferralPolicy policy;

    /** 政策名称快照（内联区间时为空）。 */
    @Column(length = 64)
    private String policyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RepaymentMethod method;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal annualRate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal remainingPrincipal;

    @Column(nullable = false)
    private int remainingPeriods;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal prepaymentAmount;

    /** 提前还款发生的期次（在该期还款日冲减本金）。 */
    @Column(nullable = false)
    private int prepaymentPeriod;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fee;

    /** 政策快照：本次模拟实际使用的宽限区间（GraceInterval 的 JSON 数组）。 */
    @Column(nullable = false, columnDefinition = "text")
    private String policySnapshotJson;

    /** 完整模拟结果（原方案 / 延期方案 / 差异），JSON 文本。 */
    @Column(nullable = false, columnDefinition = "text")
    private String resultJson;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public LoanContract getContract() {
        return contract;
    }

    public void setContract(LoanContract contract) {
        this.contract = contract;
    }

    public DeferralPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(DeferralPolicy policy) {
        this.policy = policy;
    }

    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    public RepaymentMethod getMethod() {
        return method;
    }

    public void setMethod(RepaymentMethod method) {
        this.method = method;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    public void setAnnualRate(BigDecimal annualRate) {
        this.annualRate = annualRate;
    }

    public BigDecimal getRemainingPrincipal() {
        return remainingPrincipal;
    }

    public void setRemainingPrincipal(BigDecimal remainingPrincipal) {
        this.remainingPrincipal = remainingPrincipal;
    }

    public int getRemainingPeriods() {
        return remainingPeriods;
    }

    public void setRemainingPeriods(int remainingPeriods) {
        this.remainingPeriods = remainingPeriods;
    }

    public BigDecimal getPrepaymentAmount() {
        return prepaymentAmount;
    }

    public void setPrepaymentAmount(BigDecimal prepaymentAmount) {
        this.prepaymentAmount = prepaymentAmount;
    }

    public int getPrepaymentPeriod() {
        return prepaymentPeriod;
    }

    public void setPrepaymentPeriod(int prepaymentPeriod) {
        this.prepaymentPeriod = prepaymentPeriod;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public String getPolicySnapshotJson() {
        return policySnapshotJson;
    }

    public void setPolicySnapshotJson(String policySnapshotJson) {
        this.policySnapshotJson = policySnapshotJson;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
