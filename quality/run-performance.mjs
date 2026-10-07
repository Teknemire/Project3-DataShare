/**
 * Point d'entree des mesures de performance DataShare.
 *
 * Un scenario doit etre choisi explicitement : aucune mesure lourde ne part par accident.
 * Utilisez : k6, browser, slow-download, limit-1gb ou concurrent-large.
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { command, finish, paths, readOutput, run, section } from './script-utils.mjs';

const scenario = process.argv[2];
const localStorageConfirmed = process.argv.includes('--confirm-local-storage');

function showHelp() {
  console.log(`
Usage : node quality/run-performance.mjs <scenario> [--confirm-local-storage]

Scenarios :
  k6                5 utilisateurs virtuels pendant 15 secondes
  browser            metriques de chargement de la page d'accueil
  slow-download      pause la lecture de 64 Mio pendant 40 secondes
  limit-1gb          teste 1 Go accepte puis 1 Go + 1 octet refuse
  concurrent-large   lance 5 uploads simultanes de 100 Mo, 500 Mo et 1 Go

Les trois derniers scenarios exigent --confirm-local-storage.
`);
}

function requireLocalStorage() {
  if (!localStorageConfirmed) {
    throw new Error('Ajoutez --confirm-local-storage apres avoir verifie le mode de stockage.');
  }
  const storageType = readOutput('docker', ['compose', 'exec', '-T', 'backend', 'printenv', 'STORAGE_TYPE']);
  if (storageType !== 'local') {
    throw new Error(`Le backend utilise STORAGE_TYPE=${storageType || '(vide)'} au lieu de local.`);
  }
}

function powershellProgram() {
  return process.platform === 'win32' ? 'powershell.exe' : 'pwsh';
}

function sanitizeK6Summary() {
  const summaryPath = resolve(paths.quality, 'k6-upload-summary.json');
  const summary = JSON.parse(readFileSync(summaryPath, 'utf8'));

  // k6 exporte la valeur de setup(), qui contient le JWT temporaire du scenario.
  delete summary.setup_data;
  writeFileSync(summaryPath, `${JSON.stringify(summary, null, 2)}\n`, 'utf8');
}

if (!scenario || ['--help', '-h'].includes(scenario)) {
  showHelp();
  process.exit(0);
}

switch (scenario) {
  case 'k6':
    {
      const uploadLimit = Number(readOutput('docker', ['compose', 'exec', '-T', 'backend', 'printenv', 'RATE_LIMIT_UPLOADS_PER_MINUTE']));
      if (!Number.isFinite(uploadLimit) || uploadLimit < 10000) {
        throw new Error('Le scénario k6 exige une instance de test configurée avec compose.performance.yaml. Consulter docs/PERF.md.');
      }
    }
    section(1, 'Charge k6', '5 utilisateurs virtuels repetent un upload de 112 kB pendant 15 secondes.');
    run('docker', [
      'run', '--rm', '-i',
      '-v', `${paths.quality}:/scripts`,
      'grafana/k6:0.54.0', 'run',
      '--summary-export=/scripts/k6-upload-summary.json',
      '/scripts/k6-upload.js',
    ]);
    sanitizeK6Summary();
    finish('scenario k6 termine.');
    break;

  case 'browser':
    section(1, 'Build Angular', 'La mesure porte sur le bundle de production.');
    run(command('npm'), ['run', 'build'], { cwd: paths.frontend });
    section(2, 'Mesure du navigateur', 'Playwright releve Navigation Timing, Paint Timing et les octets transferes.');
    run(command('npm'), ['run', 'perf:browser'], { cwd: paths.frontend });
    finish('mesures navigateur enregistrees dans quality/browser-performance.json.');
    break;

  case 'slow-download':
    requireLocalStorage();
    section(1, 'Telechargement ralenti', 'Le client ouvre le flux, attend 40 secondes puis controle la taille et le SHA-256.');
    run(process.execPath, ['quality/slow-download-test.mjs', '--confirm-local-storage']);
    finish('test de telechargement ralenti termine.');
    break;

  case 'limit-1gb':
    requireLocalStorage();
    section(1, 'Frontiere de 1 Go', 'Le script accepte exactement 1 Go, verifie le SHA-256 puis refuse 1 Go + 1 octet.');
    run(powershellProgram(), [
      '-NoProfile', '-ExecutionPolicy', 'Bypass',
      '-File', 'quality/large-file-boundary-test.ps1', '-ConfirmLocalStorage',
    ]);
    finish('test de la limite de 1 Go termine.');
    break;

  case 'concurrent-large':
    requireLocalStorage();
    section(1, 'Uploads volumineux concurrents', '5 clients envoient en parallele 100 Mo, 500 Mo puis 1 Go.');
    run(powershellProgram(), [
      '-NoProfile', '-ExecutionPolicy', 'Bypass',
      '-File', 'quality/concurrent-large-upload-test.ps1', '-ConfirmLocalStorage',
    ]);
    finish('test concurrent termine.');
    break;

  default:
    showHelp();
    throw new Error(`Scenario inconnu : ${scenario}`);
}
