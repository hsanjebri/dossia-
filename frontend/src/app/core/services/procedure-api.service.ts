import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Category,
  PagedResponse,
  ProcedureCategory,
  ProcedureDetail,
  ProcedureSummary,
} from '../models/procedure.model';

@Injectable({ providedIn: 'root' })
export class ProcedureApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  list(params: {
    q?: string;
    category?: ProcedureCategory;
    page?: number;
    size?: number;
    lang?: string;
  } = {}): Observable<PagedResponse<ProcedureSummary>> {
    let httpParams = new HttpParams();
    if (params.q) httpParams = httpParams.set('q', params.q);
    if (params.category) httpParams = httpParams.set('category', params.category);
    if (params.page != null) httpParams = httpParams.set('page', params.page);
    if (params.size != null) httpParams = httpParams.set('size', params.size);
    if (params.lang) httpParams = httpParams.set('lang', params.lang);

    return this.http.get<PagedResponse<ProcedureSummary>>(`${this.base}/procedures`, {
      params: httpParams,
    });
  }

  getBySlug(slug: string, lang = 'fr'): Observable<ProcedureDetail> {
    return this.http.get<ProcedureDetail>(`${this.base}/procedures/${slug}`, {
      params: { lang },
    });
  }

  categories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${this.base}/procedures/categories`);
  }
}
