package com.example.loan.api;

/**
 * 历史记录复现结果：按记录中保存的政策快照重新计算，并与保存的结果比对。
 *
 * @param matched    复现结果与保存结果是否完全一致
 * @param recomputed 按快照重新计算出的结果
 */
public record ReproduceResult(Long recordId, boolean matched, DefermentComparison recomputed) {
}
