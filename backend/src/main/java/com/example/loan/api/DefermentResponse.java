package com.example.loan.api;

/**
 * 宽限与延期模拟响应：recordId 为本次模拟记录的持久化 ID。
 */
public record DefermentResponse(Long recordId, DefermentComparison comparison) {
}
