import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  name = '';
  email = '';
  password = '';
  error = '';
  loading = false;

  submit(): void {
    this.error = '';
    if (!this.name.trim() || !this.email.trim() || this.password.length < 6) {
      this.error = 'Nom, email et mot de passe (6 caractères min.) requis.';
      return;
    }

    this.loading = true;
    this.auth.register(this.name, this.email, this.password).subscribe({
      next: () => {
        this.loading = false;
        void this.router.navigate(['/procedures']);
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error = err.error?.message ?? 'Inscription impossible. Réessayez.';
      },
    });
  }
}
