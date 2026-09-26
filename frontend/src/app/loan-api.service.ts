import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CalculationResponse,
  CompareRequest,
  DefermentRecordView,
  DefermentRequest,
  DefermentResponse,
  LoanContract,
  RecordSummaryView,
  ReproduceResult,
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

  simulateDeferment(req: DefermentRequest): Observable<DefermentResponse> {
    return this.http.post<DefermentResponse>('/api/deferments/simulate', req);
  }

  listDeferments(): Observable<DefermentRecordView[]> {
    return this.http.get<DefermentRecordView[]>('/api/deferments');
  }

  getDeferment(id: number): Observable<DefermentResponse> {
    return this.http.get<DefermentResponse>(`/api/deferments/${id}`);
  }

  reproduceDeferment(id: number): Observable<ReproduceResult> {
    return this.http.get<ReproduceResult>(`/api/deferments/${id}/reproduce`);
  }
}
