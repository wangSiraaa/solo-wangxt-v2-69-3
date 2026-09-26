package com.example.loan.api;

/**
 * 按记录保存的政策快照重算的结果。
 *
 * @param recordId     记录 ID
 * @param matchesStored 重算结果与存档结果是否一致（政策被修改后历史记录仍应可复现）
 * @param comparison   按原快照重算的完整结果
 */
public record DeferralReplayResponse(Long recordId,
                                     boolean matchesStored,
                                     DeferralComparison comparison) {
}
