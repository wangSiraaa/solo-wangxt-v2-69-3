package com.example.loan.domain;

/**
 * 宽限区间类型。
 */
public enum DefermentType {

    /** 只还利息：区间内每期只支付当期利息，本金不变。 */
    INTEREST_ONLY("只还利息"),

    /** 完全暂停且利息资本化：区间内不还款，当期利息计入本金。 */
    CAPITALIZE("暂停且利息资本化"),

    /** 暂停后一次性补缴：区间内不还款，利息与应还本金挂账，区间结束期一次性补缴。 */
    LUMP_SUM("暂停后一次性补缴");

    private final String label;

    DefermentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
