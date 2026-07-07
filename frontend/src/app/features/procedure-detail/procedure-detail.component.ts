import { DatePipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CATEGORY_ICONS, CATEGORY_LABELS, ProcedureDetail } from '../../core/models/procedure.model';
import { NearbyOffice, RouteInfo } from '../../core/models/office.model';
import { GeolocationService } from '../../core/services/geolocation.service';
import { OfficeApiService } from '../../core/services/office-api.service';
import { ProcedureApiService } from '../../core/services/procedure-api.service';
import { RoutingService } from '../../core/services/routing.service';
import { OfficeLocatorPanelComponent } from '../../shared/components/office-locator-panel/office-locator-panel.component';

@Component({
  selector: 'app-procedure-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, OfficeLocatorPanelComponent],
  templateUrl: './procedure-detail.component.html',
  styleUrl: './procedure-detail.component.scss',
})
export class ProcedureDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ProcedureApiService);
  private readonly officeApi = inject(OfficeApiService);
  private readonly routing = inject(RoutingService);
  readonly geo = inject(GeolocationService);

  procedure: ProcedureDetail | null = null;
  loading = true;
  error = '';
  checkedDocs = new Set<number>();

  nearbyOffices: NearbyOffice[] = [];
  selectedOffice: NearbyOffice | null = null;
  activeRoute: RouteInfo | null = null;
  officesLoading = false;

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const slug = params.get('slug');
      if (!slug) return;
      this.loading = true;
      this.checkedDocs.clear();
      this.nearbyOffices = [];
      this.api.getBySlug(slug).subscribe({
        next: (detail) => {
          this.procedure = detail;
          this.loading = false;
          void this.loadNearbyOffices(slug);
        },
        error: () => {
          this.error = 'Procédure introuvable.';
          this.loading = false;
        },
      });
    });
  }

  async enableLocation(): Promise<void> {
    await this.geo.requestLocation();
    if (this.procedure) {
      await this.loadNearbyOffices(this.procedure.slug);
    }
  }

  onOfficeSelected(office: NearbyOffice): void {
    this.selectedOffice = office;
    this.activeRoute = null;
    const loc = this.geo.location();
    if (!loc) return;
    this.routing.getRoute(loc, office).subscribe((route) => {
      this.activeRoute = route;
    });
  }

  categoryLabel(code: string): string {
    return CATEGORY_LABELS[code as keyof typeof CATEGORY_LABELS] ?? code;
  }

  categoryIcon(code: string): string {
    return CATEGORY_ICONS[code as keyof typeof CATEGORY_ICONS] ?? 'description';
  }

  toggleDoc(sortOrder: number): void {
    if (this.checkedDocs.has(sortOrder)) {
      this.checkedDocs.delete(sortOrder);
    } else {
      this.checkedDocs.add(sortOrder);
    }
  }

  isDocChecked(sortOrder: number): boolean {
    return this.checkedDocs.has(sortOrder);
  }

  private async loadNearbyOffices(slug: string): Promise<void> {
    const loc = this.geo.location();
    if (!loc) return;

    this.officesLoading = true;
    this.officeApi.nearest({ lat: loc.lat, lng: loc.lng, procedureSlug: slug, limit: 5 }).subscribe({
      next: (offices) => {
        this.nearbyOffices = offices;
        this.officesLoading = false;
      },
      error: () => {
        this.officesLoading = false;
      },
    });
  }
}
