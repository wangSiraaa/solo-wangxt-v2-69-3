package com.example.loan.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 延期政策：一组可复用的宽限区间配置（JSON 保存）。
 * 政策可被修改；每次模拟保存记录时会冻结当时的政策快照，
 * 因此修改政策不影响历史模拟记录的可复现性。
 */
@Entity
@Table(name = "deferral_policy")
public class DeferralPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 政策名称。 */
    @Column(nullable = false, length = 64)
    private String name;

    /** 宽限区间列表（GraceInterval 的 JSON 数组）。 */
    @Column(nullable = false, columnDefinition = "text")
    private String intervalsJson;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIntervalsJson() {
        return intervalsJson;
    }

    public void setIntervalsJson(String intervalsJson) {
        this.intervalsJson = intervalsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
