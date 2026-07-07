import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import * as L from 'leaflet';
import { NearbyOffice, RouteInfo, UserLocation } from '../../../core/models/office.model';

@Component({
  selector: 'app-office-map',
  standalone: true,
  template: `<div #mapHost class="office-map-host"></div>`,
  styles: [
    `
      .office-map-host {
        width: 100%;
        height: 280px;
        border-radius: var(--radius-lg);
        overflow: hidden;
        border: 1px solid var(--border-gold);
        z-index: 0;
      }
    `,
  ],
})
export class OfficeMapComponent implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('mapHost', { static: true }) mapHost!: ElementRef<HTMLElement>;

  @Input() userLocation: UserLocation | null = null;
  @Input() offices: NearbyOffice[] = [];
  @Input() selectedOffice: NearbyOffice | null = null;
  @Input() route: RouteInfo | null = null;

  private map: L.Map | null = null;
  private markersLayer = L.layerGroup();
  private routeLayer: L.Polyline | null = null;
  private mapReady = false;

  ngAfterViewInit(): void {
    this.initMap();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.mapReady) return;
    if (changes['offices'] || changes['userLocation'] || changes['selectedOffice'] || changes['route']) {
      this.render();
    }
  }

  ngOnDestroy(): void {
    this.map?.remove();
    this.map = null;
  }

  private initMap(): void {
    const iconDefault = L.icon({
      iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
      iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
      shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
      iconSize: [25, 41],
      iconAnchor: [12, 41],
      popupAnchor: [1, -34],
      shadowSize: [41, 41],
    });
    L.Marker.prototype.options.icon = iconDefault;

    const center = this.userLocation ?? { lat: 36.8065, lng: 10.1815 };
    this.map = L.map(this.mapHost.nativeElement, { scrollWheelZoom: false }).setView([center.lat, center.lng], 12);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap',
      maxZoom: 19,
    }).addTo(this.map);

    this.markersLayer.addTo(this.map);
    this.mapReady = true;
    this.render();

    setTimeout(() => this.map?.invalidateSize(), 100);
  }

  private render(): void {
    if (!this.map) return;

    this.markersLayer.clearLayers();
    if (this.routeLayer) {
      this.routeLayer.remove();
      this.routeLayer = null;
    }

    const bounds: L.LatLngExpression[] = [];

    if (this.userLocation) {
      const userMarker = L.circleMarker([this.userLocation.lat, this.userLocation.lng], {
        radius: 8,
        color: '#446274',
        fillColor: '#446274',
        fillOpacity: 0.9,
      }).bindPopup('Votre position');
      this.markersLayer.addLayer(userMarker);
      bounds.push([this.userLocation.lat, this.userLocation.lng]);
    }

    const officesToShow = this.selectedOffice ? [this.selectedOffice] : this.offices;
    for (const office of officesToShow) {
      const marker = L.marker([office.latitude, office.longitude]).bindPopup(
        `<strong>${office.name}</strong><br>${office.address}`,
      );
      this.markersLayer.addLayer(marker);
      bounds.push([office.latitude, office.longitude]);
    }

    if (this.route?.coordinates.length) {
      this.routeLayer = L.polyline(this.route.coordinates, {
        color: '#7e5700',
        weight: 5,
        opacity: 0.85,
      }).addTo(this.map);
      bounds.push(...this.route.coordinates);
    }

    if (bounds.length > 1) {
      this.map.fitBounds(bounds as L.LatLngBoundsExpression, { padding: [24, 24] });
    } else if (bounds.length === 1) {
      this.map.setView(bounds[0], 14);
    }
  }
}
