import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  email = '';
  password = '';
  error = '';
  loading = false;

  submit(): void {
    this.error = '';
    if (!this.email.trim() || !this.password.trim()) {
      this.error = 'Veuillez saisir un email et un mot de passe.';
      return;
    }

    this.loading = true;
    this.auth.login(this.email, this.password).subscribe({
      next: () => {
        this.loading = false;
        void this.router.navigate(['/procedures']);
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error = err.error?.message ?? 'Connexion impossible. Réessayez.';
      },
    });
  }
}
