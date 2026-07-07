import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  CATEGORY_ICONS,
  CATEGORY_LABELS,
  ProcedureCategory,
  ProcedureSummary,
} from '../../core/models/procedure.model';
import { ProcedureApiService } from '../../core/services/procedure-api.service';

@Component({
  selector: 'app-procedures-list',
  standalone: true,
  imports: [RouterLink, FormsModule],
  templateUrl: './procedures-list.component.html',
  styleUrl: './procedures-list.component.scss',
})
export class ProceduresListComponent implements OnInit {
  private readonly api = inject(ProcedureApiService);

  procedures: ProcedureSummary[] = [];
  query = '';
  selectedCategory: ProcedureCategory | '' = '';
  loading = true;
  error = '';

  readonly categories = Object.entries(CATEGORY_LABELS) as [ProcedureCategory, string][];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.api
      .list({
        q: this.query || undefined,
        category: this.selectedCategory || undefined,
        size: 50,
      })
      .subscribe({
        next: (page) => {
          this.procedures = page.content;
          this.loading = false;
        },
        error: () => {
          this.error = 'Impossible de charger les procédures. Vérifiez que l\'API tourne sur le port 8080.';
          this.loading = false;
        },
      });
  }

  selectCategory(code: ProcedureCategory | ''): void {
    this.selectedCategory = code;
    this.load();
  }

  onSearch(): void {
    this.load();
  }

  categoryLabel(code: ProcedureCategory): string {
    return CATEGORY_LABELS[code] ?? code;
  }

  categoryIcon(code: ProcedureCategory): string {
    return CATEGORY_ICONS[code] ?? 'description';
  }
}
