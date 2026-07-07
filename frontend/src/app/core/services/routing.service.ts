import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';
import { NearbyOffice, RouteInfo, UserLocation } from '../models/office.model';

@Injectable({ providedIn: 'root' })
export class RoutingService {
  private readonly http = inject(HttpClient);
  private readonly osrmBase = 'https://router.project-osrm.org/route/v1/driving';

  getRoute(user: UserLocation, office: NearbyOffice): Observable<RouteInfo | null> {
    const url =
      `${this.osrmBase}/${user.lng},${user.lat};${office.longitude},${office.latitude}` +
      '?overview=full&geometries=geojson&steps=false';

    return this.http.get<OsrmResponse>(url).pipe(
      map((res) => {
        const route = res.routes?.[0];
        if (!route) return null;
        const coords = route.geometry.coordinates.map(([lng, lat]) => [lat, lng] as [number, number]);
        return {
          distanceKm: Math.round((route.distance / 1000) * 10) / 10,
          durationMin: Math.round(route.duration / 60),
          coordinates: coords,
        };
      }),
      catchError(() => of(null)),
    );
  }
}

interface OsrmResponse {
  routes?: Array<{
    distance: number;
    duration: number;
    geometry: { coordinates: [number, number][] };
  }>;
}
