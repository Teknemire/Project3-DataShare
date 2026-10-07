import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

const password = 'Accessibility123!';

async function expectNoAccessibilityViolation(page: Page, screen: string): Promise<void> {
  const result = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();

  expect(
    result.violations,
    `${screen}: ${JSON.stringify(result.violations, null, 2)}`,
  ).toEqual([]);
}

test('les écrans publics respectent les contrôles WCAG automatisables', async ({ page }) => {
  const screens = [
    { path: '/', heading: 'Tu veux partager un fichier ?' },
    { path: '/register', heading: 'Créer un compte' },
    { path: '/login', heading: 'Connexion' },
    { path: '/upload', heading: 'Ajouter un fichier' },
  ];

  for (const screen of screens) {
    await page.goto(screen.path);
    await expect(page.getByRole('heading', { name: screen.heading })).toBeVisible();
    await expectNoAccessibilityViolation(page, screen.path);
  }
});

test('les écrans authentifiés respectent les contrôles WCAG automatisables', async ({
  page,
  request,
}) => {
  const email = `accessibility-${Date.now()}@datashare.test`;
  const registration = await request.post('/api/auth/register', {
    data: { email, password },
  });
  expect(registration.status()).toBe(201);

  const login = await request.post('/api/auth/login', {
    data: { email, password },
  });
  expect(login.status()).toBe(200);
  const { accessToken } = (await login.json()) as { accessToken: string };

  await page.addInitScript((token: string) => {
    sessionStorage.setItem('datashare_access_token', token);
  }, accessToken);

  try {
    const screens = [
      { path: '/account', heading: 'Mes fichiers' },
      { path: '/profile', heading: 'Mon profil' },
      { path: '/upload', heading: 'Ajouter un fichier' },
    ];

    for (const screen of screens) {
      await page.goto(screen.path);
      await expect(page.getByRole('heading', { name: screen.heading })).toBeVisible();
      await expectNoAccessibilityViolation(page, screen.path);
    }
  } finally {
    await request.delete('/api/users/me', {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: { password },
    });
  }
});

test('la navigation personnelle reste utilisable sur mobile', async ({ page, request }) => {
  const email = `mobile-${Date.now()}@datashare.test`;
  const registration = await request.post('/api/auth/register', {
    data: { email, password },
  });
  expect(registration.status()).toBe(201);

  const login = await request.post('/api/auth/login', {
    data: { email, password },
  });
  expect(login.status()).toBe(200);
  const { accessToken } = (await login.json()) as { accessToken: string };

  await page.addInitScript((token: string) => {
    sessionStorage.setItem('datashare_access_token', token);
  }, accessToken);
  await page.setViewportSize({ width: 390, height: 844 });

  try {
    await page.goto('/account');
    const menuButton = page.getByRole('button', { name: 'Ouvrir le menu' });
    await expect(menuButton).toBeVisible();
    await expect(menuButton).toHaveAttribute('aria-expanded', 'false');

    await menuButton.click();
    await expect(menuButton).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByRole('navigation', { name: "Navigation de l'espace personnel" })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Se déconnecter' })).toBeVisible();

    const hasHorizontalOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    );
    expect(hasHorizontalOverflow).toBe(false);

    await page.locator('.close-menu').click();
    await expect(menuButton).toHaveAttribute('aria-expanded', 'false');
  } finally {
    await request.delete('/api/users/me', {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: { password },
    });
  }
});
