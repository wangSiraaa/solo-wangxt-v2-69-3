package com.example.loan.api;

import com.example.loan.domain.DefermentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 一条宽限区间：[startPeriod, endPeriod] 为期次闭区间（从 1 开始，含端点）。
 * 相邻区间允许（前一区间结束期 + 1 = 后一区间起始期），重叠区间会被拒绝。
 */
public record DefermentIntervalRequest(
        @Min(value = 1, message = "区间起始期至少为 1")
        int startPeriod,

        @Min(value = 1, message = "区间结束期至少为 1")
        int endPeriod,

        @NotNull(message = "宽限类型不能为空")
        DefermentType type) {
}
