import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { NearbyOffice } from '../models/office.model';

@Injectable({ providedIn: 'root' })
export class OfficeApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/offices`;

  nearest(params: {
    lat: number;
    lng: number;
    procedureSlug?: string;
    q?: string;
    limit?: number;
  }): Observable<NearbyOffice[]> {
    let httpParams = new HttpParams()
      .set('lat', params.lat)
      .set('lng', params.lng)
      .set('limit', params.limit ?? 5);
    if (params.procedureSlug) httpParams = httpParams.set('procedureSlug', params.procedureSlug);
    if (params.q) httpParams = httpParams.set('q', params.q);
    return this.http.get<NearbyOffice[]>(`${this.base}/nearest`, { params: httpParams });
  }
}
