import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CalculationResponse,
  CompareRequest,
  DeferralPolicy,
  DeferralPolicyRequest,
  DeferralRecordSummaryView,
  DeferralReplayResponse,
  DeferralSimulateRequest,
  DeferralSimulationResponse,
  LoanContract,
  RecordSummaryView,
} from './models';

@Injectable({ providedIn: 'root' })
export class LoanApiService {
  private readonly http = inject(HttpClient);

  listContracts(): Observable<LoanContract[]> {
    return this.http.get<LoanContract[]>('/api/contracts');
  }

  compare(req: CompareRequest): Observable<CalculationResponse> {
    return this.http.post<CalculationResponse>('/api/calculations/compare', req);
  }

  listRecords(): Observable<RecordSummaryView[]> {
    return this.http.get<RecordSummaryView[]>('/api/calculations');
  }

  getRecord(id: number): Observable<CalculationResponse> {
    return this.http.get<CalculationResponse>(`/api/calculations/${id}`);
  }

  /* ---------- 宽限与延期模拟 ---------- */

  listDeferralPolicies(): Observable<DeferralPolicy[]> {
    return this.http.get<DeferralPolicy[]>('/api/deferral/policies');
  }

  createDeferralPolicy(req: DeferralPolicyRequest): Observable<DeferralPolicy> {
    return this.http.post<DeferralPolicy>('/api/deferral/policies', req);
  }

  updateDeferralPolicy(id: number, req: DeferralPolicyRequest): Observable<DeferralPolicy> {
    return this.http.put<DeferralPolicy>(`/api/deferral/policies/${id}`, req);
  }

  simulateDeferral(req: DeferralSimulateRequest): Observable<DeferralSimulationResponse> {
    return this.http.post<DeferralSimulationResponse>('/api/deferral/simulations', req);
  }

  listDeferralRecords(): Observable<DeferralRecordSummaryView[]> {
    return this.http.get<DeferralRecordSummaryView[]>('/api/deferral/simulations');
  }

  getDeferralRecord(id: number): Observable<DeferralSimulationResponse> {
    return this.http.get<DeferralSimulationResponse>(`/api/deferral/simulations/${id}`);
  }

  replayDeferralRecord(id: number): Observable<DeferralReplayResponse> {
    return this.http.post<DeferralReplayResponse>(`/api/deferral/simulations/${id}/replay`, null);
  }
}
