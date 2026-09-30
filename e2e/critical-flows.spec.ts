import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { readFile } from 'node:fs/promises';

const password = 'Password123!';

function uniqueEmail(label: string): string {
  return `${label}-${Date.now()}-${Math.random().toString(16).slice(2)}@datashare.test`;
}

async function registerThroughApi(request: APIRequestContext, email: string): Promise<void> {
  const response = await request.post('/api/auth/register', {
    data: { email, password },
  });
  expect(response.status()).toBe(201);
}

async function loginThroughUi(page: Page, email: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mot de passe').fill(password);
  await page.getByRole('button', { name: 'Connexion', exact: true }).click();
  await expect(page).toHaveURL(/\/account$/);
}

async function deleteAccountThroughApi(
  page: Page,
  request: APIRequestContext,
): Promise<void> {
  const accessToken = await page.evaluate(() => sessionStorage.getItem('datashare_access_token'));
  if (!accessToken) {
    return;
  }
  await request.delete('/api/users/me', {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: { password },
  });
}

test('création de compte, connexion et suppression du compte', async ({ page, request }) => {
  const email = uniqueEmail('e2e-account');

  try {
    await page.goto('/register');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Mot de passe', { exact: true }).fill(password);
    await page.getByLabel('Vérification du mot de passe').fill(password);
    await page.getByRole('button', { name: 'Créer mon compte' }).click();

    await expect(page).toHaveURL(/\/login\?registered=true$/);
    await expect(page.getByRole('status')).toContainText('Votre compte a bien été créé');

    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Mot de passe').fill(password);
    await page.getByRole('button', { name: 'Connexion', exact: true }).click();
    await expect(page).toHaveURL(/\/account$/);
    await expect(page.getByRole('heading', { name: 'Mes fichiers' })).toBeVisible();

    await page.goto('/profile');
    await expect(page.locator('dd').filter({ hasText: email })).toBeVisible();
    await page.getByRole('button', { name: 'Supprimer mon compte' }).click();
    const dialog = page.getByRole('dialog', { name: 'Confirmer la suppression' });
    await dialog.getByLabel('Mot de passe actuel').fill(password);
    await dialog.getByLabel('Je comprends que cette action est irréversible.').check();
    await dialog.getByRole('button', { name: 'Supprimer définitivement mon compte' }).click();

    await expect(page.getByRole('heading', { name: 'Votre compte a été supprimé' })).toBeVisible();
  } finally {
    await deleteAccountThroughApi(page, request);
  }
});

test('upload authentifié protégé, historique, téléchargement et suppression', async ({
  page,
  request,
}) => {
  const email = uniqueEmail('e2e-transfer');
  await registerThroughApi(request, email);

  try {
    await loginThroughUi(page, email);
    await page.goto('/upload');
    await page.locator('#file').setInputFiles({
      name: 'preuve-e2e.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('Contenu vérifié par le parcours E2E DataShare.'),
    });
    await page.getByLabel('Mot de passe').fill('partage123');
    await page.getByLabel('Tags').fill('Projet, Urgent');
    await page.getByRole('button', { name: 'Téléverser' }).click();

    await expect(page.getByText('Félicitations')).toBeVisible();
    const shareUrl = await page.getByLabel('Lien de partage').inputValue();

    await page.goto('/account');
    const fileRow = page.getByRole('listitem').filter({ hasText: 'preuve-e2e.txt' });
    await expect(fileRow).toContainText('Tags : Projet, Urgent');

    await page.goto(shareUrl);
    await expect(page.getByRole('heading', { name: 'Télécharger un fichier' })).toBeVisible();
    await page.getByLabel('Mot de passe').fill('partage123');
    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Télécharger' }).click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toBe('preuve-e2e.txt');
    const downloadedPath = await download.path();
    expect(downloadedPath).not.toBeNull();
    const downloadedContent = await readFile(downloadedPath!, 'utf8');
    expect(downloadedContent).toBe('Contenu vérifié par le parcours E2E DataShare.');

    await page.goto('/account');
    const rowToDelete = page.getByRole('listitem').filter({ hasText: 'preuve-e2e.txt' });
    await rowToDelete.getByRole('button', { name: 'Supprimer', exact: true }).click();
    const deletionDialog = page.getByRole('dialog', { name: 'Supprimer ce fichier ?' });
    await deletionDialog.getByRole('button', { name: 'Supprimer définitivement' }).click();
    await expect(page.getByText('Le fichier preuve-e2e.txt a été supprimé.')).toBeVisible();
    await expect(rowToDelete).toHaveCount(0);
  } finally {
    await deleteAccountThroughApi(page, request);
  }
});

test('upload et téléchargement anonymes', async ({ page }) => {
  await page.goto('/upload');
  await expect(page.getByLabel('Tags')).toHaveCount(0);
  await page.locator('#file').setInputFiles({
    name: 'anonyme-e2e.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('Transfert anonyme DataShare.'),
  });
  await page.getByLabel('Expiration').selectOption('1');
  await page.getByRole('button', { name: 'Téléverser' }).click();

  const shareUrl = await page.getByLabel('Lien de partage').inputValue();
  await page.goto(shareUrl);
  await expect(page.getByText('anonyme-e2e.txt')).toBeVisible();
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Télécharger' }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe('anonyme-e2e.txt');
});
