/** 与后端 API 对应的类型定义。金额单位：元；利率为小数（0.049 = 4.9%）。 */

export type RepaymentMethod = 'EQUAL_INSTALLMENT' | 'EQUAL_PRINCIPAL';

export const METHOD_LABELS: Record<RepaymentMethod, string> = {
  EQUAL_INSTALLMENT: '等额本息',
  EQUAL_PRINCIPAL: '等额本金',
};

export interface LoanContract {
  id: number;
  contractNo: string;
  borrowerName: string;
  method: RepaymentMethod;
  annualRate: number;
  remainingPrincipal: number;
  remainingPeriods: number;
  createdAt: string;
}

export interface ScheduleRow {
  period: number;
  payment: number;
  principal: number;
  interest: number;
  balance: number;
}

export interface PlanSummary {
  periods: number;
  firstPayment: number;
  lastPayment: number;
  monthlyPayment: number | null;
  totalPrincipal: number;
  totalInterest: number;
  totalPayment: number;
  fee: number;
  totalCost: number;
}

export interface PlanResult {
  code: string;
  label: string;
  summary: PlanSummary;
  schedule: ScheduleRow[];
}

export interface DiffSummary {
  periodDiff: number;
  monthlyPaymentDiff: number | null;
  firstPaymentDiff: number;
  interestDiff: number;
  totalCostDiff: number;
}

export interface ComparisonResult {
  baseline: PlanSummary;
  shortenTerm: PlanResult;
  reducePayment: PlanResult;
  diff: DiffSummary;
}

export interface CalculationResponse {
  recordId: number;
  method: RepaymentMethod;
  comparison: ComparisonResult;
}

export interface CompareRequest {
  contractId?: number | null;
  method?: RepaymentMethod | null;
  annualRate?: number | null;
  remainingPrincipal?: number | null;
  remainingPeriods?: number | null;
  prepaymentAmount: number;
  fee: number;
}

export interface RecordSummaryView {
  id: number;
  createdAt: string;
  contractNo: string | null;
  method: RepaymentMethod;
  annualRate: number;
  remainingPrincipal: number;
  remainingPeriods: number;
  prepaymentAmount: number;
  fee: number;
  shortenPeriods: number;
  reducePeriods: number;
  shortenTotalInterest: number;
  reduceTotalInterest: number;
  interestDiff: number;
}

/* ---------- 宽限与延期模拟 ---------- */

/** 宽限区间处理方式；NORMAL 仅用于计划行展示（不在任何区间）。 */
export type DeferralMode = 'NORMAL' | 'INTEREST_ONLY' | 'CAPITALIZE' | 'DEFER_LUMPSUM';

export const DEFERRAL_MODE_LABELS: Record<DeferralMode, string> = {
  NORMAL: '正常还款',
  INTEREST_ONLY: '只还利息',
  CAPITALIZE: '暂停并资本化',
  DEFER_LUMPSUM: '暂停后一次性补缴',
};

/** 可配置的区间模式（NORMAL 仅展示用）。 */
export const GRACE_MODES: DeferralMode[] = ['INTEREST_ONLY', 'CAPITALIZE', 'DEFER_LUMPSUM'];

/** 同一还款日内的事件，列表顺序即执行顺序。 */
export type DeferralEventType =
  | 'EXIT_LUMPSUM'
  | 'CAPITALIZE'
  | 'DEFER_ACCRUAL'
  | 'PREPAYMENT'
  | 'PERIOD_PAYMENT';

export const DEFERRAL_EVENT_LABELS: Record<DeferralEventType, string> = {
  EXIT_LUMPSUM: '一次性补缴',
  CAPITALIZE: '利息资本化',
  DEFER_ACCRUAL: '挂账',
  PREPAYMENT: '提前还款',
  PERIOD_PAYMENT: '当期还款',
};

export interface GraceInterval {
  startPeriod: number;
  endPeriod: number;
  mode: DeferralMode;
}

export interface DeferralPolicy {
  id: number;
  name: string;
  intervals: GraceInterval[];
  createdAt: string;
  updatedAt: string;
}

export interface DeferralPolicyRequest {
  name: string;
  intervals: GraceInterval[];
}

export interface DeferralRow {
  period: number;
  mode: DeferralMode;
  events: DeferralEventType[];
  payment: number;
  principal: number;
  interest: number;
  lumpSumPaid: number;
  lumpSumPrincipal: number;
  capitalized: number;
  deferred: number;
  prepayment: number;
  balance: number;
}

export interface DeferralPlanSummary {
  periods: number;
  monthlyPayment: number | null;
  firstPayment: number;
  lastPayment: number;
  scheduledPayments: number;
  lumpSumPaid: number;
  prepayment: number;
  totalPaid: number;
  totalPrincipal: number;
  normalInterest: number;
  deferredInterest: number;
  totalInterest: number;
  capitalizedAmount: number;
  fee: number;
  totalCost: number;
}

export interface DeferralPlan {
  summary: DeferralPlanSummary;
  schedule: DeferralRow[];
}

export interface DeferralDiff {
  periodDiff: number;
  interestDiff: number;
  totalPaidDiff: number;
  totalCostDiff: number;
}

export interface DeferralComparison {
  original: DeferralPlan;
  deferred: DeferralPlan;
  diff: DeferralDiff;
}

export interface DeferralSimulateRequest {
  contractId?: number | null;
  method?: RepaymentMethod | null;
  annualRate?: number | null;
  remainingPrincipal?: number | null;
  remainingPeriods?: number | null;
  policyId?: number | null;
  intervals?: GraceInterval[] | null;
  prepaymentAmount: number;
  prepaymentPeriod?: number | null;
  fee: number;
}

export interface DeferralSimulationResponse {
  recordId: number;
  method: RepaymentMethod;
  policyName: string | null;
  intervals: GraceInterval[];
  comparison: DeferralComparison;
}

export interface DeferralRecordSummaryView {
  id: number;
  createdAt: string;
  contractNo: string | null;
  policyName: string | null;
  method: RepaymentMethod;
  annualRate: number;
  remainingPrincipal: number;
  remainingPeriods: number;
  prepaymentAmount: number;
  prepaymentPeriod: number;
  fee: number;
  intervalCount: number;
  originalPeriods: number;
  deferredPeriods: number;
  addedPeriods: number;
  deferredInterest: number;
  capitalizedAmount: number;
  interestDiff: number;
}

export interface DeferralReplayResponse {
  recordId: number;
  matchesStored: boolean;
  comparison: DeferralComparison;
}
