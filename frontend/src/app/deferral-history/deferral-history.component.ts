import { Component, EventEmitter, Input, OnChanges, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DeferralRecordSummaryView, METHOD_LABELS } from '../models';
import { LoanApiService } from '../loan-api.service';

interface ReplayState {
  loading: boolean;
  matches?: boolean;
}

/**
 * 延期模拟历史记录：展示每次模拟的关键指标，可回查完整结果，
 * 或按记录保存的政策快照重算（验证政策修改后历史结果仍可复现）。
 */
@Component({
  selector: 'app-deferral-history',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './deferral-history.component.html',
  styleUrl: './deferral-history.component.css',
})
export class DeferralHistoryComponent implements OnInit, OnChanges {
  private readonly api = inject(LoanApiService);

  @Input() refreshToken = 0;
  @Output() viewRecord = new EventEmitter<number>();

  records: DeferralRecordSummaryView[] = [];
  replayState: Record<number, ReplayState> = {};
  readonly methodLabels = METHOD_LABELS;

  ngOnInit(): void {
    this.reload();
  }

  ngOnChanges(): void {
    this.reload();
  }

  replay(id: number): void {
    this.replayState[id] = { loading: true };
    this.api.replayDeferralRecord(id).subscribe({
      next: (resp) => (this.replayState[id] = { loading: false, matches: resp.matchesStored }),
      error: () => (this.replayState[id] = { loading: false }),
    });
  }

  stateOf(id: number): ReplayState {
    return this.replayState[id] ?? { loading: false };
  }

  private reload(): void {
    this.api.listDeferralRecords().subscribe({
      next: (records) => (this.records = records),
      error: () => (this.records = []),
    });
  }
}
