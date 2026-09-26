package com.example.loan.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 一次宽限与延期模拟的记录。政策快照（policyJson）与完整结果（resultJson）
 * 分别以 JSON 保存：之后即使政策被修改，历史结果仍按原快照复现。
 */
@Entity
@Table(name = "deferment_record")
public class DefermentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的模拟合同；手工录入参数时为空。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private LoanContract contract;

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

    /** 提前还款所在期次：0 表示计划开始前立即扣减。 */
    @Column(nullable = false)
    private int prepaymentPeriod;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fee;

    /** 延期政策快照（区间、提前还款安排、合同参数与口径版本），JSON 文本。 */
    @Column(nullable = false, columnDefinition = "text")
    private String policyJson;

    /** 完整模拟结果（原方案 / 延期方案逐期计划、汇总与差异），JSON 文本。 */
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

    public String getPolicyJson() {
        return policyJson;
    }

    public void setPolicyJson(String policyJson) {
        this.policyJson = policyJson;
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
