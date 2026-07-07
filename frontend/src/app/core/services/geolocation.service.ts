import { Injectable, signal } from '@angular/core';
import { UserLocation } from '../models/office.model';

@Injectable({ providedIn: 'root' })
export class GeolocationService {
  readonly location = signal<UserLocation | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  requestLocation(): Promise<UserLocation | null> {
    if (!('geolocation' in navigator)) {
      this.error.set('La géolocalisation n\'est pas disponible sur cet appareil.');
      return Promise.resolve(null);
    }

    this.loading.set(true);
    this.error.set(null);

    return new Promise((resolve) => {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const loc = { lat: pos.coords.latitude, lng: pos.coords.longitude };
          this.location.set(loc);
          this.loading.set(false);
          resolve(loc);
        },
        () => {
          this.loading.set(false);
          this.error.set('Autorisez la localisation pour trouver le bureau le plus proche.');
          resolve(null);
        },
        { enableHighAccuracy: true, timeout: 12000, maximumAge: 60000 },
      );
    });
  }

  clearError(): void {
    this.error.set(null);
  }
}
