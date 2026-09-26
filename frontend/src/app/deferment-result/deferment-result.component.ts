import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  DEFERMENT_EVENT_LABELS,
  DEFERMENT_TYPE_LABELS,
  DefermentComparison,
  DefermentPlanResult,
} from '../models';

/**
 * 宽限与延期模拟结果：差额视图（延期方案 − 原方案）、利息构成
 * （正常利息 / 延期利息 / 资本化金额 / 新增期数）、政策快照与两方案逐期计划。
 */
@Component({
  selector: 'app-deferment-result',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './deferment-result.component.html',
  styleUrl: './deferment-result.component.css',
})
export class DefermentResultComponent {
  @Input({ required: true }) result!: DefermentComparison;

  readonly eventLabels = DEFERMENT_EVENT_LABELS;
  readonly typeLabels = DEFERMENT_TYPE_LABELS;

  get isInstallment(): boolean {
    return this.result.policy.method === 'EQUAL_INSTALLMENT';
  }

  get plans(): DefermentPlanResult[] {
    return [this.result.original, this.result.deferred];
  }

  /** 事件标签（未知事件原样展示）。 */
  eventLabel(event: string): string {
    return this.eventLabels[event] ?? event;
  }

  trackPlan(_: number, plan: DefermentPlanResult): string {
    return plan.code;
  }
}
