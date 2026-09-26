package com.example.loan.api;

import com.example.loan.domain.DeferralMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 一个宽限区间：[startPeriod, endPeriod] 为期次闭区间（从 1 开始，含两端），
 * 区间内所有期次按 mode 处理。多个区间允许相邻（前一区间结束期的下一期即为后一区间起始期），
 * 不允许重叠。
 */
public record GraceInterval(
        @Min(value = 1, message = "区间起始期至少为 1")
        int startPeriod,

        @Min(value = 1, message = "区间结束期至少为 1")
        int endPeriod,

        @NotNull(message = "区间处理方式不能为空")
        DeferralMode mode) {
}
