package com.example.loan.domain;

/**
 * 宽限区间内的还款处理方式。NORMAL 仅用于计划行展示（表示当期不在任何宽限区间内），
 * 配置区间时不允许使用。
 */
public enum DeferralMode {

    /** 正常还款（不在任何宽限区间内）。 */
    NORMAL("正常还款"),

    /** 只还利息：每期只支付当期利息，本金不变，期限相应顺延。 */
    INTEREST_ONLY("只还利息"),

    /** 完全暂停且利息资本化：当期不还款，利息计入本金，下一期计息基数增加。 */
    CAPITALIZE("暂停并资本化"),

    /** 暂停后一次性补缴：当期不还款，应还本息挂账，区间结束后的第一期一次性补缴。 */
    DEFER_LUMPSUM("暂停后一次性补缴");

    private final String label;

    DeferralMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
