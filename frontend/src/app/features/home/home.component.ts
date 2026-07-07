import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProcedureApiService } from '../../core/services/procedure-api.service';
import { ProcedureSummary } from '../../core/models/procedure.model';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit {
  private readonly api = inject(ProcedureApiService);
  featured: ProcedureSummary[] = [];
  loading = true;

  ngOnInit(): void {
    this.api.list({ size: 3 }).subscribe({
      next: (page) => {
        this.featured = page.content;
        this.loading = false;
      },
      error: () => (this.loading = false),
    });
  }
}
