import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NearbyOffice, RouteInfo, UserLocation } from '../../../core/models/office.model';
import { OfficeMapComponent } from '../office-map/office-map.component';

@Component({
  selector: 'app-office-locator-panel',
  standalone: true,
  imports: [OfficeMapComponent],
  templateUrl: './office-locator-panel.component.html',
  styleUrl: './office-locator-panel.component.scss',
})
export class OfficeLocatorPanelComponent {
  @Input() offices: NearbyOffice[] = [];
  @Input() userLocation: UserLocation | null = null;
  @Input() selectedOffice: NearbyOffice | null = null;
  @Input() route: RouteInfo | null = null;
  @Input() title = 'Bureaux à proximité';
  @Output() officeSelected = new EventEmitter<NearbyOffice>();

  selectOffice(office: NearbyOffice): void {
    this.officeSelected.emit(office);
  }

  officeTypeLabel(type: string): string {
    const labels: Record<string, string> = {
      POLICE: 'Commissariat',
      FINANCE: 'Recette des finances',
      POST: 'Bureau de poste',
      MUNICIPALITY: 'Municipalité',
      ATTT: 'ATTT',
      BUSINESS: 'APII / RNE',
      PROCEDURE_OFFICE: 'Bureau officiel',
    };
    return labels[type] ?? type;
  }

  openExternal(url: string): void {
    window.open(url, '_blank', 'noopener');
  }
}
