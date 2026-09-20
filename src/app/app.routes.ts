import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register').then((component) => component.Register),
    title: 'Créer un compte | DataShare',
  },
  { path: '', pathMatch: 'full', redirectTo: 'register' },
  { path: '**', redirectTo: 'register' },
];
