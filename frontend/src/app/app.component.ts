import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CompareFormComponent } from './compare-form/compare-form.component';
import { PlanResultComponent } from './plan-result/plan-result.component';
import { HistoryComponent } from './history/history.component';
import { DeferralFormComponent } from './deferral-form/deferral-form.component';
import { DeferralResultComponent } from './deferral-result/deferral-result.component';
import { DeferralHistoryComponent } from './deferral-history/deferral-history.component';
import { LoanApiService } from './loan-api.service';
import {
  CompareRequest,
  ComparisonResult,
  DeferralSimulateRequest,
  DeferralSimulationResponse,
  RepaymentMethod,
} from './models';

/**
 * 工作台主界面：提前还款对比 + 宽限与延期模拟两个工作区。
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    CompareFormComponent,
    PlanResultComponent,
    HistoryComponent,
    DeferralFormComponent,
    DeferralResultComponent,
    DeferralHistoryComponent,
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent {
  private readonly api = inject(LoanApiService);

  /** 当前工作区：提前还款对比 / 宽限与延期模拟。 */
  tab: 'prepay' | 'deferral' = 'prepay';

  /* ---------- 提前还款对比 ---------- */

  comparison: ComparisonResult | null = null;
  method: RepaymentMethod = 'EQUAL_INSTALLMENT';
  recordId: number | null = null;
  loading = false;
  error = '';
  /** 每完成一次计算递增，通知历史记录刷新。 */
  historyRefresh = 0;

  onSubmit(req: CompareRequest): void {
    this.loading = true;
    this.error = '';
    this.api.compare(req).subscribe({
      next: (resp) => {
        this.comparison = resp.comparison;
        this.method = resp.method;
        this.recordId = resp.recordId;
        this.loading = false;
        this.historyRefresh++;
      },
      error: (err) => {
        this.error = err?.error?.message ?? '计算失败，请检查输入或确认后端已启动';
        this.loading = false;
      },
    });
  }

  onViewRecord(id: number): void {
    this.loading = true;
    this.error = '';
    this.api.getRecord(id).subscribe({
      next: (resp) => {
        this.comparison = resp.comparison;
        this.method = resp.method;
        this.recordId = resp.recordId;
        this.loading = false;
      },
      error: () => {
        this.error = '历史记录加载失败';
        this.loading = false;
      },
    });
  }

  /* ---------- 宽限与延期模拟 ---------- */

  deferralResult: DeferralSimulationResponse | null = null;
  deferralLoading = false;
  deferralError = '';
  deferralHistoryRefresh = 0;

  onDeferralSubmit(req: DeferralSimulateRequest): void {
    this.deferralLoading = true;
    this.deferralError = '';
    this.api.simulateDeferral(req).subscribe({
      next: (resp) => {
        this.deferralResult = resp;
        this.deferralLoading = false;
        this.deferralHistoryRefresh++;
      },
      error: (err) => {
        this.deferralError = err?.error?.message ?? '模拟失败，请检查输入或确认后端已启动';
        this.deferralLoading = false;
      },
    });
  }

  onViewDeferralRecord(id: number): void {
    this.deferralLoading = true;
    this.deferralError = '';
    this.api.getDeferralRecord(id).subscribe({
      next: (resp) => {
        this.deferralResult = resp;
        this.deferralLoading = false;
      },
      error: () => {
        this.deferralError = '延期模拟记录加载失败';
        this.deferralLoading = false;
      },
    });
  }
}
