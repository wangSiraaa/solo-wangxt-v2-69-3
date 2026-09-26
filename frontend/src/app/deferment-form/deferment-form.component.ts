import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  DEFERMENT_TYPE_LABELS,
  DefermentInterval,
  DefermentRequest,
  DefermentType,
  LoanContract,
  METHOD_LABELS,
  RepaymentMethod,
} from '../models';
import { LoanApiService } from '../loan-api.service';

/** 一个宽限区间对应的表单组。 */
type IntervalFormGroup = FormGroup<{
  startPeriod: FormControl<number | null>;
  endPeriod: FormControl<number | null>;
  type: FormControl<DefermentType>;
}>;

/**
 * 宽限与延期模拟表单：选择模拟合同或手工录入参数，配置提前还款安排
 * 与若干互不重叠的宽限区间（区间内只还利息 / 资本化 / 暂停后补缴）。
 * 区间在提交前做客户端校验：范围合法、起始 ≤ 结束、不重叠（相邻允许）。
 */
@Component({
  selector: 'app-deferment-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './deferment-form.component.html',
  styleUrl: './deferment-form.component.css',
})
export class DefermentFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(LoanApiService);

  /** 父组件传入：是否正在计算（禁用提交按钮）。 */
  @Input() loading = false;
  /** 提交模拟请求。 */
  @Output() submitted = new EventEmitter<DefermentRequest>();

  contracts: LoanContract[] = [];
  loadError = '';
  readonly methodLabels = METHOD_LABELS;
  readonly typeLabels = DEFERMENT_TYPE_LABELS;
  readonly typeOptions: DefermentType[] = ['INTEREST_ONLY', 'CAPITALIZE', 'LUMP_SUM'];

  /** 区间校验结果（客户端先行校验，服务端仍会复核）。 */
  intervalErrors: string[] = [];

  readonly form = this.fb.group({
    contractId: this.fb.control<number | null>(null),
    method: this.fb.control<RepaymentMethod>('EQUAL_INSTALLMENT', { nonNullable: true }),
    annualRatePercent: this.fb.control<number | null>(4.9, [Validators.required, Validators.min(0), Validators.max(36)]),
    remainingPrincipal: this.fb.control<number | null>(1000000, [Validators.required, Validators.min(0.01)]),
    remainingPeriods: this.fb.control<number | null>(240, [Validators.required, Validators.min(1), Validators.max(600)]),
    prepaymentAmount: this.fb.control<number | null>(0, [Validators.required, Validators.min(0)]),
    prepaymentPeriod: this.fb.control<number | null>(0, [Validators.required, Validators.min(0), Validators.max(600)]),
    fee: this.fb.control<number | null>(0, [Validators.required, Validators.min(0)]),
    intervals: this.fb.array<IntervalFormGroup>([]),
  });

  get intervals(): FormArray<IntervalFormGroup> {
    return this.form.controls.intervals;
  }

  get useContract(): boolean {
    return this.form.controls.contractId.value != null;
  }

  get selectedContract(): LoanContract | undefined {
    return this.contracts.find((c) => c.id === this.form.controls.contractId.value);
  }

  /** 当前剩余期数（合同或手工录入），用于区间范围校验。 */
  private get currentPeriods(): number | null {
    const c = this.selectedContract;
    if (c) return c.remainingPeriods;
    return this.form.controls.remainingPeriods.value;
  }

  ngOnInit(): void {
    this.api.listContracts().subscribe({
      next: (contracts) => (this.contracts = contracts),
      error: () => (this.loadError = '模拟合同加载失败，请确认后端已启动'),
    });
    const manual = ['method', 'annualRatePercent', 'remainingPrincipal', 'remainingPeriods'] as const;
    this.form.controls.contractId.valueChanges.subscribe((id) => {
      for (const name of manual) {
        const control = this.form.controls[name];
        id != null ? control.disable() : control.enable();
      }
      this.validateIntervals();
    });
    this.intervals.valueChanges.subscribe(() => this.validateIntervals());
    this.form.controls.remainingPeriods.valueChanges.subscribe(() => this.validateIntervals());
  }

  addInterval(): void {
    this.intervals.push(this.newInterval());
  }

  removeInterval(index: number): void {
    this.intervals.removeAt(index);
  }

  private newInterval(): IntervalFormGroup {
    return this.fb.group({
      startPeriod: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
      endPeriod: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
      type: this.fb.control<DefermentType>('INTEREST_ONLY', { nonNullable: true }),
    });
  }

  /** 客户端区间校验：范围、起始 ≤ 结束、不重叠（相邻允许）。 */
  private validateIntervals(): void {
    const errors: string[] = [];
    const periods = this.currentPeriods;
    const list: DefermentInterval[] = [];
    this.intervals.controls.forEach((group, i) => {
      const no = i + 1;
      const start = group.controls.startPeriod.value;
      const end = group.controls.endPeriod.value;
      if (start == null || end == null) {
        errors.push(`第 ${no} 个区间：起始期与结束期必填`);
        return;
      }
      if (start < 1) errors.push(`第 ${no} 个区间：起始期至少为 1`);
      if (end < start) errors.push(`第 ${no} 个区间：结束期不能小于起始期（${start} ~ ${end}）`);
      if (periods != null && end > periods) {
        errors.push(`第 ${no} 个区间：结束期 ${end} 超出剩余期数（${periods} 期）`);
      }
      list.push({ startPeriod: start, endPeriod: end, type: group.controls.type.value });
    });
    const sorted = [...list].sort((a, b) => a.startPeriod - b.startPeriod || a.endPeriod - b.endPeriod);
    for (let i = 1; i < sorted.length; i++) {
      if (sorted[i].startPeriod <= sorted[i - 1].endPeriod) {
        errors.push(
          `区间重叠：第 ${sorted[i - 1].startPeriod} ~ ${sorted[i - 1].endPeriod} 期 与 ` +
          `第 ${sorted[i].startPeriod} ~ ${sorted[i].endPeriod} 期（相邻允许，重叠不允许）`
        );
      }
    }
    this.intervalErrors = errors;
  }

  submit(): void {
    this.validateIntervals();
    if (this.form.invalid || this.intervalErrors.length > 0) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const req: DefermentRequest = {
      prepaymentAmount: v.prepaymentAmount!,
      prepaymentPeriod: v.prepaymentPeriod!,
      fee: v.fee!,
      intervals: v.intervals.map((g) => ({
        startPeriod: g.startPeriod!,
        endPeriod: g.endPeriod!,
        type: g.type,
      })),
    };
    if (v.contractId != null) {
      req.contractId = v.contractId;
    } else {
      req.method = v.method;
      req.annualRate = v.annualRatePercent! / 100;
      req.remainingPrincipal = v.remainingPrincipal;
      req.remainingPeriods = v.remainingPeriods;
    }
    this.submitted.emit(req);
  }
}
