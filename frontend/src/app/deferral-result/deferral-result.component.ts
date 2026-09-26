import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  DEFERRAL_EVENT_LABELS,
  DEFERRAL_MODE_LABELS,
  DeferralComparison,
  DeferralMode,
  DeferralPlan,
  GraceInterval,
  RepaymentMethod,
} from '../models';

/**
 * 延期模拟结果：原方案 / 延期方案差额视图（差异条 + 两方案汇总卡片 + 逐期计划表）。
 * 计划表单独列示正常利息、延期利息、资本化金额与一次性补缴、提前还款事件。
 */
@Component({
  selector: 'app-deferral-result',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './deferral-result.component.html',
  styleUrl: './deferral-result.component.css',
})
export class DeferralResultComponent {
  @Input({ required: true }) result!: DeferralComparison;
  @Input({ required: true }) method!: RepaymentMethod;
  /** 本次模拟实际使用的宽限区间（政策快照内容）。 */
  @Input() intervals: GraceInterval[] = [];

  readonly modeLabels = DEFERRAL_MODE_LABELS;
  readonly eventLabels = DEFERRAL_EVENT_LABELS;

  get isInstallment(): boolean {
    return this.method === 'EQUAL_INSTALLMENT';
  }

  get plans(): { label: string; plan: DeferralPlan }[] {
    return [
      { label: '原方案（无宽限区间）', plan: this.result.original },
      { label: '延期方案（应用宽限区间）', plan: this.result.deferred },
    ];
  }

  /** 区间描述，如「第 2~3 期 · 只还利息」。 */
  intervalLabel(iv: GraceInterval): string {
    return `第 ${iv.startPeriod}~${iv.endPeriod} 期 · ${this.modeLabels[iv.mode]}`;
  }

  modeClass(mode: DeferralMode): string {
    return {
      NORMAL: 'mode-normal',
      INTEREST_ONLY: 'mode-io',
      CAPITALIZE: 'mode-cap',
      DEFER_LUMPSUM: 'mode-defer',
    }[mode];
  }

  /** 列合计（与表格可见列严格一致；本金列不含补缴本金与提前还款）。 */
  columnTotal(plan: DeferralPlan, key: 'payment' | 'principal' | 'interest' | 'lumpSumPaid' | 'prepayment' | 'capitalized'): number {
    return plan.schedule.reduce((sum, row) => sum + row[key], 0);
  }

  trackPlan(_: number, item: { label: string }): string {
    return item.label;
  }
}
