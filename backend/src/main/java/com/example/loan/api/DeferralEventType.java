package com.example.loan.api;

/**
 * 同一期次还款日内发生的事件。列表顺序即执行顺序，固定为：
 * 一次性补缴 → 利息资本化 / 挂账 → 提前还款 → 当期还款。
 */
public enum DeferralEventType {

    /** 区间退出一次性补缴：「暂停后一次性补缴」区间结束后的第一期，补缴全部挂账本息。 */
    EXIT_LUMPSUM("一次性补缴"),

    /** 利息资本化：「暂停并资本化」区间内，当期利息计入本金。 */
    CAPITALIZE("利息资本化"),

    /** 挂账：「暂停后一次性补缴」区间内，当期应还本息记入递延台账。 */
    DEFER_ACCRUAL("挂账"),

    /** 提前还款：在当期还款日冲减本金。 */
    PREPAYMENT("提前还款"),

    /** 当期还款：按当前还款方式正常扣款（只还利息区间内为利息扣款）。 */
    PERIOD_PAYMENT("当期还款");

    private final String label;

    DeferralEventType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
