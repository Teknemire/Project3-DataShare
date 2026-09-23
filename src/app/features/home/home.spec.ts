import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TokenStorageService } from '../../core/services/token-storage.service';
import { Home } from './home';

describe('Home', () => {
  let tokenStorage: TokenStorageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Home],
      providers: [provideRouter([])],
    });
    tokenStorage = TestBed.inject(TokenStorageService);
    tokenStorage.clear();
  });

  afterEach(() => tokenStorage.clear());

  it('directs a visitor to login before sharing a file', () => {
    const fixture = createComponent();
    const links = fixture.nativeElement.querySelectorAll('a[href="/login"]');

    expect(links.length).toBe(2);
    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain(
      'Tu veux partager un fichier',
    );
  });

  it('directs an authenticated visitor to upload and exposes their account', () => {
    tokenStorage.set('signed-token');
    const fixture = createComponent();

    expect(fixture.nativeElement.querySelectorAll('a[href="/account"]').length).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('a[href="/upload"]').length).toBe(1);
    expect(fixture.nativeElement.querySelector('.account-link')?.textContent).toContain(
      'Mon espace',
    );
  });

  function createComponent(): ComponentFixture<Home> {
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    return fixture;
  }
});
