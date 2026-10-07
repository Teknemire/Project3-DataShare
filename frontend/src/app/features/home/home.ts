import { SiteHeader } from '../../shared/site-header/site-header';
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-home',
  imports: [SiteHeader, RouterLink],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {

  protected readonly shareDestination = '/upload';
}
