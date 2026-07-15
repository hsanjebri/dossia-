import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/auth/auth.service';
import { ChatHistoryService } from '../../../core/services/chat-history.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly history = inject(ChatHistoryService);

  email = '';
  password = '';
  error = '';
  loading = false;
  returnUrl = '/chat';

  ngOnInit(): void {
    this.returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/chat';
  }

  submit(): void {
    this.error = '';
    if (!this.email.trim() || !this.password.trim()) {
      this.error = 'Veuillez saisir un email et un mot de passe.';
      return;
    }

    this.loading = true;
    this.auth.login(this.email, this.password).subscribe({
      next: () => {
        this.history.migrateGuestChatsToAccount().subscribe({
          next: (sessionId) => {
            this.loading = false;
            void this.router.navigate([this.returnUrl], {
              queryParams: sessionId ? { session: sessionId } : undefined,
            });
          },
          error: () => {
            this.loading = false;
            void this.router.navigate([this.returnUrl]);
          },
        });
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error = err.error?.message ?? 'Connexion impossible. Réessayez.';
      },
    });
  }
}
