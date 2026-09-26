import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormArray, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  DEFERRAL_MODE_LABELS,
  DeferralMode,
  DeferralPolicy,
  DeferralSimulateRequest,
  GRACE_MODES,
  GraceInterval,
  LoanContract,
  METHOD_LABELS,
  RepaymentMethod,
} from '../models';
import { LoanApiService } from '../loan-api.service';

interface IntervalFormValue {
  startPeriod: number | null;
  endPeriod: number | null;
  mode: DeferralMode;
}

/**
 * 宽限与延期模拟表单：合同参数、提前还款、宽限区间编辑器（含区间校验）
 * 与延期政策管理（保存 / 更新政策；模拟时冻结政策快照）。
 */
@Component({
  selector: 'app-deferral-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './deferral-form.component.html',
  styleUrl: './deferral-form.component.css',
})
export class DeferralFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(LoanApiService);

  @Input() loading = false;
  @Output() submitted = new EventEmitter<DeferralSimulateRequest>();

  contracts: LoanContract[] = [];
  policies: DeferralPolicy[] = [];
  loadError = '';
  policyMessage = '';
  newPolicyName = '';
  /** 载入政策后区间是否被修改（被修改则按内联区间提交）。 */
  policyDirty = false;
  /** 正在把政策区间载入编辑器（载入过程不视为用户修改）。 */
  private loadingPolicy = false;

  readonly methodLabels = METHOD_LABELS;
  readonly modeLabels = DEFERRAL_MODE_LABELS;
  readonly graceModes = GRACE_MODES;

  readonly form = this.fb.group({
    contractId: this.fb.control<number | null>(null),
    method: this.fb.control<RepaymentMethod>('EQUAL_INSTALLMENT', { nonNullable: true }),
    annualRatePercent: this.fb.control<number | null>(4.9, [Validators.required, Validators.min(0), Validators.max(36)]),
    remainingPrincipal: this.fb.control<number | null>(1000000, [Validators.required, Validators.min(0.01)]),
    remainingPeriods: this.fb.control<number | null>(240, [Validators.required, Validators.min(1), Validators.max(600)]),
    prepaymentAmount: this.fb.control<number | null>(0, [Validators.required, Validators.min(0)]),
    prepaymentPeriod: this.fb.control<number | null>(1, [Validators.required, Validators.min(1)]),
    fee: this.fb.control<number | null>(0, [Validators.required, Validators.min(0)]),
    policyId: this.fb.control<number | null>(null),
    intervals: this.fb.array<FormGroup>([]),
  });

  get intervals(): FormArray<FormGroup> {
    return this.form.controls.intervals;
  }

  get useContract(): boolean {
    return this.form.controls.contractId.value != null;
  }

  get selectedContract(): LoanContract | undefined {
    return this.contracts.find((c) => c.id === this.form.controls.contractId.value);
  }

  get selectedPolicy(): DeferralPolicy | undefined {
    return this.policies.find((p) => p.id === this.form.controls.policyId.value);
  }

  get prepaymentEnabled(): boolean {
    return (this.form.controls.prepaymentAmount.value ?? 0) > 0;
  }

  /** 区间校验：起止合法、不超出剩余期数、区间不重叠（相邻允许）。 */
  get intervalErrors(): string[] {
    const errors: string[] = [];
    const raw = this.intervals.getRawValue() as IntervalFormValue[];
    if (raw.length === 0) {
      return ['请至少添加一个宽限区间'];
    }
    const maxPeriods = this.selectedContract?.remainingPeriods
      ?? this.form.controls.remainingPeriods.value
      ?? null;
    raw.forEach((iv, i) => {
      if (iv.startPeriod == null || iv.endPeriod == null) {
        errors.push(`第 ${i + 1} 个区间：起始期与结束期必填`);
        return;
      }
      if (iv.startPeriod < 1) {
        errors.push(`第 ${i + 1} 个区间：起始期至少为 1`);
      }
      if (iv.endPeriod < iv.startPeriod) {
        errors.push(`第 ${i + 1} 个区间：结束期不能早于起始期`);
      }
      if (maxPeriods != null && iv.endPeriod > maxPeriods) {
        errors.push(`第 ${i + 1} 个区间：结束期 ${iv.endPeriod} 超出剩余期数 ${maxPeriods}`);
      }
    });
    const sorted = raw
      .map((iv, i) => ({ ...iv, i }))
      .filter((iv) => iv.startPeriod != null && iv.endPeriod != null)
      .sort((a, b) => a.startPeriod! - b.startPeriod!);
    for (let k = 1; k < sorted.length; k++) {
      const prev = sorted[k - 1];
      const cur = sorted[k];
      if (cur.startPeriod! <= prev.endPeriod!) {
        errors.push(
          `区间重叠：[${prev.startPeriod}, ${prev.endPeriod}] 与 [${cur.startPeriod}, ${cur.endPeriod}]（相邻允许，重叠不允许）`,
        );
      }
    }
    return [...new Set(errors)];
  }

  ngOnInit(): void {
    this.api.listContracts().subscribe({
      next: (contracts) => (this.contracts = contracts),
      error: () => (this.loadError = '模拟合同加载失败，请确认后端已启动'),
    });
    this.reloadPolicies();
    this.addInterval(2, 3); // 默认一个示例区间，可直接修改

    const manual = ['method', 'annualRatePercent', 'remainingPrincipal', 'remainingPeriods'] as const;
    this.form.controls.contractId.valueChanges.subscribe((id) => {
      for (const name of manual) {
        const control = this.form.controls[name];
        id != null ? control.disable() : control.enable();
      }
    });
    this.form.controls.policyId.valueChanges.subscribe(() => this.onPolicySelected());
    this.intervals.valueChanges.subscribe(() => {
      if (!this.loadingPolicy) {
        this.policyDirty = true;
      }
    });
  }

  addInterval(start?: number, end?: number, mode: DeferralMode = 'INTEREST_ONLY'): void {
    this.intervals.push(
      this.fb.group({
        startPeriod: this.fb.control<number | null>(start ?? null, [Validators.required, Validators.min(1)]),
        endPeriod: this.fb.control<number | null>(end ?? null, [Validators.required, Validators.min(1)]),
        mode: this.fb.control<DeferralMode>(mode, { nonNullable: true }),
      }),
    );
  }

  removeInterval(index: number): void {
    this.intervals.removeAt(index);
  }

  /** 选择政策后把政策区间载入编辑器（取消选择时保留当前区间）。 */
  onPolicySelected(): void {
    const policy = this.selectedPolicy;
    if (!policy) {
      this.policyMessage = '';
      return;
    }
    this.loadingPolicy = true;
    this.intervals.clear();
    for (const iv of policy.intervals) {
      this.addInterval(iv.startPeriod, iv.endPeriod, iv.mode);
    }
    this.loadingPolicy = false;
    this.policyDirty = false;
    this.policyMessage = `已载入政策「${policy.name}」，模拟时将冻结当前内容为快照`;
  }

  /** 把当前区间另存为新政策。 */
  savePolicyAs(): void {
    const name = this.newPolicyName.trim();
    if (!name || this.intervalErrors.length > 0) {
      return;
    }
    this.api.createDeferralPolicy({ name, intervals: this.currentIntervals() }).subscribe({
      next: (policy) => {
        this.newPolicyName = '';
        this.reloadPolicies(policy.id);
        this.policyMessage = `已保存政策「${policy.name}」`;
      },
      error: (err) => (this.policyMessage = err?.error?.message ?? '政策保存失败'),
    });
  }

  /** 用当前区间更新所选政策（历史模拟记录仍按各自快照复现）。 */
  updatePolicy(): void {
    const policy = this.selectedPolicy;
    if (!policy || this.intervalErrors.length > 0) {
      return;
    }
    this.api.updateDeferralPolicy(policy.id, { name: policy.name, intervals: this.currentIntervals() }).subscribe({
      next: (updated) => {
        this.reloadPolicies(updated.id);
        this.policyMessage = `已更新政策「${updated.name}」；历史模拟记录仍按原快照可复现`;
      },
      error: (err) => (this.policyMessage = err?.error?.message ?? '政策更新失败'),
    });
  }

  submit(): void {
    if (this.form.invalid || this.intervalErrors.length > 0) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const req: DeferralSimulateRequest = {
      prepaymentAmount: v.prepaymentAmount!,
      prepaymentPeriod: v.prepaymentAmount! > 0 ? v.prepaymentPeriod : null,
      fee: v.fee!,
    };
    if (v.contractId != null) {
      req.contractId = v.contractId;
    } else {
      req.method = v.method;
      req.annualRate = v.annualRatePercent! / 100;
      req.remainingPrincipal = v.remainingPrincipal;
      req.remainingPeriods = v.remainingPeriods;
    }
    // 选用政策且未改动区间 → 按政策提交（记录关联政策并冻结快照）；否则按内联区间
    if (v.policyId != null && !this.policyDirty) {
      req.policyId = v.policyId;
    } else {
      req.intervals = this.currentIntervals();
    }
    this.submitted.emit(req);
  }

  private currentIntervals(): GraceInterval[] {
    return (this.intervals.getRawValue() as IntervalFormValue[]).map((iv) => ({
      startPeriod: iv.startPeriod!,
      endPeriod: iv.endPeriod!,
      mode: iv.mode,
    }));
  }

  private reloadPolicies(selectId?: number): void {
    this.api.listDeferralPolicies().subscribe({
      next: (policies) => {
        this.policies = policies;
        if (selectId != null) {
          this.form.controls.policyId.setValue(selectId, { emitEvent: false });
          this.policyDirty = false;
        }
      },
      error: () => (this.policies = []),
    });
  }
}
