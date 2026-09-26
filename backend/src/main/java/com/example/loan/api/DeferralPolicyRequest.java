package com.example.loan.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新建 / 修改延期政策请求。政策是一组可复用的宽限区间配置；
 * 修改政策不影响已保存的模拟记录（记录保存时冻结政策快照）。
 */
public record DeferralPolicyRequest(
        @NotBlank(message = "政策名称不能为空")
        @Size(max = 64, message = "政策名称不能超过 64 个字符")
        String name,

        @NotEmpty(message = "至少配置一个宽限区间")
        List<@Valid GraceInterval> intervals) {
}
