/**
 * Validation quotidienne de DataShare.
 *
 * Sans option : tests backend, couverture frontend et build Angular.
 * Avec --all : ajoute Docker, les tests E2E et les captures de couverture.
 */
import { existsSync } from 'node:fs';
import { command, finish, paths, run, section } from './script-utils.mjs';

const runEverything = process.argv.includes('--all');
const testEnvironment = { ...process.env };

if (process.argv.some((argument) => ['--help', '-h'].includes(argument))) {
  console.log(`
Usage : node quality/run-tests.mjs [--all]

Sans option : tests backend, couverture frontend et build Angular.
Avec --all : ajoute Docker, les tests E2E et les captures de couverture.
`);
  process.exit(0);
}

// Karma attend Chrome. Sous Windows, Edge utilise le meme moteur et peut servir de navigateur headless.
if (!testEnvironment.CHROME_BIN && process.platform === 'win32') {
  const edge = `${process.env['ProgramFiles(x86)']}\\Microsoft\\Edge\\Application\\msedge.exe`;
  if (existsSync(edge)) testEnvironment.CHROME_BIN = edge;
}

section(1, 'Backend : tests et couverture JaCoCo', 'Maven compile le backend et execute les tests JUnit/MockMvc.');
run(command('mvn'), ['verify'], { cwd: paths.backend });

section(2, 'Frontend : tests et couverture Karma', 'Angular execute les tests unitaires dans un navigateur sans interface.');
run(command('npm'), ['run', 'test:coverage', '--', '--browsers=ChromeHeadless'], {
  cwd: paths.frontend,
  env: testEnvironment,
});

section(3, 'Frontend : build de production', 'Le build verifie aussi les budgets de taille Angular.');
run(command('npm'), ['run', 'build'], { cwd: paths.frontend });

if (runEverything) {
  section(4, 'Application complete', 'Docker reconstruit et demarre Angular, Spring Boot et PostgreSQL.');
  run('docker', ['compose', 'up', '-d', '--build']);

  section(5, 'Parcours de bout en bout', 'Playwright teste les parcours reels dans le navigateur.');
  run(command('npm'), ['run', 'test:e2e'], { cwd: paths.frontend });

  section(6, 'Captures de couverture', 'Les rapports HTML sont captures pour conserver une preuve lisible.');
  run(command('npm'), ['run', 'evidence:coverage'], { cwd: paths.frontend });
}

finish(runEverything ? 'validation complete terminee.' : 'validation quotidienne terminee.');
