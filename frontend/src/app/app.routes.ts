import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guard';
import { HomeComponent } from './features/home/home.component';
import { LoginComponent } from './features/auth/login/login.component';
import { RegisterComponent } from './features/auth/register/register.component';
import { AboutComponent } from './features/about/about.component';
import { FaqComponent } from './features/faq/faq.component';
import { ProfileComponent } from './features/profile/profile.component';
import { ProceduresListComponent } from './features/procedures/procedures-list.component';
import { ProcedureDetailComponent } from './features/procedure-detail/procedure-detail.component';
import { ChatComponent } from './features/chat/chat.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'about', component: AboutComponent },
  { path: 'faq', component: FaqComponent },
  { path: 'procedures', component: ProceduresListComponent },
  { path: 'procedures/:slug', component: ProcedureDetailComponent },
  { path: 'chat', component: ChatComponent },
  { path: 'profile', component: ProfileComponent, canActivate: [authGuard] },
  {
    path: 'auth/login',
    component: LoginComponent,
    canActivate: [guestGuard],
  },
  {
    path: 'auth/register',
    component: RegisterComponent,
    canActivate: [guestGuard],
  },
  { path: 'auth', redirectTo: 'auth/login', pathMatch: 'full' },
  { path: '**', redirectTo: '' },
];
