/**
 * Procedures courantes de maintenance DataShare.
 *
 * Modes : dependencies, validate, release ou configuration.
 * Chaque mode regroupe les commandes dans leur ordre logique.
 */
import { command, finish, paths, run, section } from './script-utils.mjs';

const mode = process.argv[2];

function showHelp() {
  console.log(`
Usage : node quality/run-maintenance.mjs <mode>

Modes :
  dependencies   affiche les mises a jour npm et Maven disponibles
  validate       lance les tests quotidiens
  release        lance tests complets puis controles de securite
  configuration  valide Compose, reconstruit et affiche l'etat et les logs
`);
}

if (!mode || ['--help', '-h'].includes(mode)) {
  showHelp();
  process.exit(0);
}

switch (mode) {
  case 'dependencies':
    section(1, 'Versions npm disponibles', 'Cette etape informe seulement : elle ne modifie aucun paquet.');
    run(command('npm'), ['outdated'], { cwd: paths.frontend, allowFailure: true });
    section(2, 'Versions Maven disponibles', 'Le plugin compare les dependances backend sans les mettre a jour.');
    run(command('mvn'), ['versions:display-dependency-updates'], { cwd: paths.backend });
    finish('veille des dependances terminee.');
    break;

  case 'validate':
    section(1, 'Validation quotidienne', 'Le lanceur de tests verifie backend, frontend et build.');
    run(process.execPath, ['quality/run-tests.mjs']);
    finish('validation de maintenance terminee.');
    break;

  case 'release':
    section(1, 'Validation fonctionnelle complete', 'Les tests unitaires, le build, Docker et les E2E sont executes.');
    run(process.execPath, ['quality/run-tests.mjs', '--all']);
    section(2, 'Validation de securite', 'npm audit et les deux scans Trivy produisent des rapports locaux reproductibles.');
    run(process.execPath, ['quality/run-security.mjs']);
    finish('controle avant livraison termine.');
    break;

  case 'configuration':
    section(1, 'Valider la configuration Compose', 'Docker verifie la syntaxe et les variables requises.');
    run('docker', ['compose', 'config', '--quiet']);
    section(2, 'Reconstruire et demarrer', 'Les images et conteneurs sont remis a jour.');
    run('docker', ['compose', 'up', '-d', '--build']);
    section(3, 'Verifier l etat', 'La liste doit montrer les services attendus en fonctionnement.');
    run('docker', ['compose', 'ps']);
    section(4, 'Lire les derniers journaux', 'Les 100 dernieres lignes permettent de reperer une erreur de demarrage.');
    run('docker', ['compose', 'logs', '--tail', '100', 'backend', 'frontend']);
    finish('controle de configuration termine.');
    break;

  default:
    showHelp();
    throw new Error(`Mode inconnu : ${mode}`);
}
