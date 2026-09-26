import { Component, EventEmitter, Input, OnChanges, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DefermentRecordView, METHOD_LABELS } from '../models';
import { LoanApiService } from '../loan-api.service';

/**
 * 延期模拟历史记录：展示每次模拟的关键指标，可回查完整结果，
 * 并可按保存的政策快照复现（政策之后被修改也不影响复现结果）。
 */
@Component({
  selector: 'app-deferment-history',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './deferment-history.component.html',
  styleUrl: './deferment-history.component.css',
})
export class DefermentHistoryComponent implements OnInit, OnChanges {
  private readonly api = inject(LoanApiService);

  /** 每次模拟完成后由父组件递增，触发刷新。 */
  @Input() refreshToken = 0;
  /** 点击「查看」时发出记录 ID。 */
  @Output() viewRecord = new EventEmitter<number>();

  records: DefermentRecordView[] = [];
  readonly methodLabels = METHOD_LABELS;
  /** 每条记录的复现结果：true = 与保存结果一致。 */
  reproduceStatus = new Map<number, boolean>();
  reproducing = new Set<number>();

  ngOnInit(): void {
    this.reload();
  }

  ngOnChanges(): void {
    this.reload();
  }

  private reload(): void {
    this.api.listDeferments().subscribe({
      next: (records) => (this.records = records),
      error: () => (this.records = []),
    });
  }

  reproduce(id: number): void {
    this.reproducing.add(id);
    this.api.reproduceDeferment(id).subscribe({
      next: (res) => {
        this.reproduceStatus.set(id, res.matched);
        this.reproducing.delete(id);
      },
      error: () => this.reproducing.delete(id),
    });
  }
}
