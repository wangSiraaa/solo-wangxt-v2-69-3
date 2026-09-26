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

export type DefermentType = 'INTEREST_ONLY' | 'CAPITALIZE' | 'LUMP_SUM';

export const DEFERMENT_TYPE_LABELS: Record<DefermentType, string> = {
  INTEREST_ONLY: '只还利息',
  CAPITALIZE: '暂停且利息资本化',
  LUMP_SUM: '暂停后一次性补缴',
};

/** 逐期事件标签（与后端 DefermentService 的 events 对应）。 */
export const DEFERMENT_EVENT_LABELS: Record<string, string> = {
  INTEREST_ONLY: '只还利息',
  CAPITALIZE: '利息资本化',
  DEFER: '暂停挂账',
  CATCH_UP: '一次性补缴',
  PREPAYMENT: '提前还款',
};

export interface DefermentInterval {
  startPeriod: number;
  endPeriod: number;
  type: DefermentType;
}

export interface DefermentRequest {
  contractId?: number | null;
  method?: RepaymentMethod | null;
  annualRate?: number | null;
  remainingPrincipal?: number | null;
  remainingPeriods?: number | null;
  prepaymentAmount: number;
  prepaymentPeriod: number;
  fee: number;
  intervals: DefermentInterval[];
}

export interface DefermentPolicySnapshot {
  policyVersion: string;
  method: RepaymentMethod;
  annualRate: number;
  remainingPrincipal: number;
  remainingPeriods: number;
  prepaymentAmount: number;
  prepaymentPeriod: number;
  fee: number;
  intervals: DefermentInterval[];
}

export interface DefermentRow {
  period: number;
  payment: number;
  principal: number;
  interest: number;
  accruedInterest: number;
  capitalized: number;
  balance: number;
  events: string[];
}

export interface DefermentSummary {
  periods: number;
  firstPayment: number;
  lastPayment: number;
  monthlyPayment: number | null;
  totalPayment: number;
  totalPrincipal: number;
  totalInterest: number;
  normalInterest: number;
  defermentInterest: number;
  capitalizedAmount: number;
  fee: number;
  totalCost: number;
}

export interface DefermentPlanResult {
  code: string;
  label: string;
  summary: DefermentSummary;
  schedule: DefermentRow[];
}

export interface DefermentDiff {
  addedPeriods: number;
  interestDiff: number;
  totalPaymentDiff: number;
  totalCostDiff: number;
  defermentInterest: number;
  capitalizedAmount: number;
}

export interface DefermentComparison {
  original: DefermentPlanResult;
  deferred: DefermentPlanResult;
  diff: DefermentDiff;
  policy: DefermentPolicySnapshot;
}

export interface DefermentResponse {
  recordId: number;
  comparison: DefermentComparison;
}

export interface DefermentRecordView {
  id: number;
  createdAt: string;
  contractNo: string | null;
  method: RepaymentMethod;
  annualRate: number;
  remainingPrincipal: number;
  remainingPeriods: number;
  prepaymentAmount: number;
  prepaymentPeriod: number;
  intervalCount: number;
  originalPeriods: number;
  deferredPeriods: number;
  addedPeriods: number;
  defermentInterest: number;
  capitalizedAmount: number;
  interestDiff: number;
}

export interface ReproduceResult {
  recordId: number;
  matched: boolean;
  recomputed: DefermentComparison;
}
