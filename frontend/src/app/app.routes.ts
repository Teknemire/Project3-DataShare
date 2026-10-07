import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'legal',
    loadComponent: () => import('./features/legal/legal').then(component => component.Legal),
    title: 'Mentions légales et confidentialité | DataShare',
  },
  {
    path: '',
    loadComponent: () => import('./features/home/home').then((component) => component.Home),
    title: 'DataShare | Partage de fichiers',
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register').then((component) => component.Register),
    title: 'Créer un compte | DataShare',
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login').then((component) => component.Login),
    title: 'Se connecter | DataShare',
  },
  {
    path: 'upload',
    loadComponent: () =>
      import('./features/upload/upload').then((component) => component.Upload),
    title: 'Partager un fichier | DataShare',
  },
  {
    path: 'share/:token',
    loadComponent: () =>
      import('./features/share/share').then((component) => component.Share),
    title: 'Télécharger un fichier | DataShare',
  },
  {
    path: 'account',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/account/account').then((component) => component.Account),
    title: 'Mon espace | DataShare',
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/profile/profile').then((component) => component.Profile),
    title: 'Mon profil | DataShare',
  },
  { path: '**', redirectTo: '' },
];
