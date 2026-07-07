import { Injectable, inject, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, firstValueFrom, of, tap } from 'rxjs';
import { AuthUser } from '../models/auth.model';
import { AuthApiService } from '../services/auth-api.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthApiService);
  private readonly router = inject(Router);
  private readonly userSignal = signal<AuthUser | null>(null);
  private readonly readySignal = signal(false);

  readonly user = this.userSignal.asReadonly();
  readonly isLoggedIn = computed(() => this.userSignal() !== null);
  readonly ready = this.readySignal.asReadonly();

  restoreSession(): Promise<void> {
    return firstValueFrom(
      this.api.me().pipe(
        tap((user) => this.userSignal.set(user)),
        catchError(() => {
          this.userSignal.set(null);
          return of(null);
        }),
      ),
    ).then(() => this.readySignal.set(true));
  }

  login(email: string, password: string) {
    return this.api.login({ email, password }).pipe(tap((user) => this.userSignal.set(user)));
  }

  register(name: string, email: string, password: string) {
    return this.api
      .register({ name, email, password })
      .pipe(tap((user) => this.userSignal.set(user)));
  }

  logout(): void {
    this.api.logout().subscribe({
      next: () => {
        this.userSignal.set(null);
        void this.router.navigate(['/auth/login']);
      },
      error: () => {
        this.userSignal.set(null);
        void this.router.navigate(['/auth/login']);
      },
    });
  }
}
