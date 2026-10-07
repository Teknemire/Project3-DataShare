/**
 * Controle de securite reproductible de DataShare.
 *
 * Le script construit le JAR, audite npm, puis analyse le depot et le JAR avec Trivy.
 * Les rapports sont ecrits dans quality/ afin de garder les preuves brutes.
 */
import { homedir } from 'node:os';
import { resolve } from 'node:path';
import { command, finish, paths, run, runAndSave, section } from './script-utils.mjs';

const trivyImage = 'aquasec/trivy:0.74.0';
const mavenCache = resolve(homedir(), '.m2');

if (process.argv.some((argument) => ['--help', '-h'].includes(argument))) {
  console.log(`
Usage : node quality/run-security.mjs

Etapes : build et tests Maven, audit npm, scan Trivy des manifestes,
puis scan Trivy du contenu reel du JAR.
`);
  process.exit(0);
}

let npmAuditHasFindings = false;

section(1, 'Construire et tester le backend', 'Le scan du JAR doit porter sur un artefact actuel et valide.');
run(command('mvn'), ['verify'], { cwd: paths.backend });

section(2, 'Auditer les dependances npm', 'npm recherche les vulnerabilites connues du frontend.');
const npmAudit = runAndSave(
  command('npm'),
  ['audit', '--json'],
  resolve(paths.quality, 'npm-audit.json'),
  { cwd: paths.frontend, allowFailure: true },
);
npmAuditHasFindings = npmAudit.status !== 0;

section(3, 'Scanner les manifestes avec Trivy', 'Le rapport texte couvre package-lock.json et pom.xml.');
run('docker', [
  'run', '--rm',
  '-v', `${paths.root}:/work:ro`,
  '-v', `${paths.quality}:/reports`,
  '-v', `${mavenCache}:/root/.m2:ro`,
  '-v', 'datashare-trivy-cache:/root/.cache/',
  trivyImage, 'fs',
  '--scanners', 'vuln', '--severity', 'HIGH,CRITICAL', '--ignore-unfixed',
  '--skip-dirs', '/work/frontend/node_modules', '--skip-dirs', '/work/backend/target',
  '--format', 'table', '--output', '/reports/trivy-report.txt', '/work',
]);

section(4, 'Scanner le contenu reel du JAR', 'Ce second scan voit les dependances Java embarquees.');
run('docker', [
  'run', '--rm',
  '-v', `${resolve(paths.backend, 'target')}:/scan:ro`,
  '-v', `${paths.quality}:/reports`,
  '-v', 'datashare-trivy-cache:/root/.cache/',
  trivyImage, 'rootfs',
  '--scanners', 'vuln', '--severity', 'HIGH,CRITICAL', '--ignore-unfixed',
  '--format', 'json', '--output', '/reports/trivy-jar-report.json', '/scan',
]);

if (npmAuditHasFindings) {
  throw new Error('npm audit a signale au moins une vulnerabilite. Consultez quality/npm-audit.json.');
}

finish('controles de securite termines ; rapports disponibles dans quality/.');
