# DataShare

DataShare permet d'envoyer temporairement un fichier à une autre personne grâce à un lien de partage. L'expéditeur peut utiliser le service avec ou sans compte, choisir une durée de disponibilité et ajouter un mot de passe. Avec un compte, il retrouve ses envois dans un historique et peut les supprimer.

Ce README donne le chemin le plus court pour comprendre, lancer et vérifier le projet. Les détails techniques restent disponibles dans les documents liés en fin de page.

> Améliorations de la solution : la limitation de débit et les renforcements techniques sont intégrés. Les mesures de charge historiques ne décrivent pas cette version.

## Structure

```text
datashare/
├── backend/          API Java et Spring Boot
├── frontend/         application Angular
├── docs/             documentation technique et suivi qualité
├── quality/          scripts et résultats de contrôle
├── compose.yaml      lancement complet avec PostgreSQL
└── .env.example      variables de configuration documentées
```

Le dépôt contient l'interface, l'API, la documentation et les scripts de contrôle dans une même arborescence. Les spécifications et les maquettes de référence sont conservées séparément.

## Technologies utilisées

- **Angular et TypeScript** pour l'interface affichée dans le navigateur ;
- **Java 21 et Spring Boot** pour l'API et les règles métier ;
- **PostgreSQL** pour les comptes et les informations sur les fichiers ;
- **Spring Security et JWT** pour identifier l'utilisateur sur les routes privées ;
- **stockage local ou AWS S3** pour le contenu des fichiers ;
- **OpenAPI et Swagger UI** pour consulter et essayer le contrat de l'API ;
- **JUnit, Jasmine/Karma et Playwright** pour les tests, **JaCoCo** pour la couverture et **k6** pour la charge.

Un JWT est un jeton de connexion envoyé avec les requêtes privées. Il permet au backend de reconnaître l'utilisateur sans ouvrir une session serveur.

## Lancement avec Docker

Prérequis : Docker Desktop avec Docker Compose. Cette méthode lance l'interface, l'API et PostgreSQL ensemble. Les commandes PowerShell sont complétées par les variantes Linux/macOS plus bas.

Depuis la racine du repository :

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Dans `.env`, remplacez au minimum `JWT_SECRET` par une valeur aléatoire d'au moins 32 caractères. Le mot de passe PostgreSQL fourni dans l'exemple est destiné au développement local ; changez-le si l'environnement est accessible à d'autres personnes. Lancez ensuite :

```powershell
docker compose up -d --build
```

Services disponibles :

- application : `http://localhost:4200` ;
- API : `http://localhost:8080` ;
- Swagger UI : `http://localhost:8080/swagger-ui/index.html` ;
- contrat OpenAPI JSON : `http://localhost:8080/v3/api-docs`.

Le service PostgreSQL crée la base et son utilisateur à partir du fichier `.env`. Flyway applique les migrations SQL versionnées de `backend/src/main/resources/db/migration`, puis Hibernate valide le schéma (`ddl-auto=validate`). Pour une base existante créée par Hibernate, suivre la reprise documentée dans [MAINTENANCE.md](docs/MAINTENANCE.md) avant de relancer.

Pour arrêter l'application :

```powershell
docker compose down
```

La commande suivante supprime également la base PostgreSQL locale ; elle ne doit être utilisée que pour repartir de zéro :

```powershell
docker compose down -v
```

## Lancement en développement

Prérequis : Java 21, Maven 3.9, Node.js compatible avec Angular 20 (version 20 à partir de 20.19, version 22 à partir de 22.12, ou version 24.x), npm et Docker Compose. Le Maven Wrapper a échoué dans l'environnement de vérification ; les commandes ci-dessous utilisent Maven installé (`mvn.cmd`).

Démarrez uniquement PostgreSQL :

```powershell
docker compose up -d postgres
```

Dans un premier terminal :

```powershell
cd backend
$env:JWT_SECRET="remplacer-par-un-secret-de-developpement-de-32-caracteres"
mvn.cmd spring-boot:run
```

Dans un second terminal :

```powershell
cd frontend
npm.cmd ci
npm.cmd start
```

Le serveur Angular transmet les appels `/api` au backend grâce à `proxy.conf.json`.
Les valeurs PostgreSQL par défaut du backend correspondent à `.env.example`. Si vous modifiez `POSTGRES_DB`, `POSTGRES_USER` ou `POSTGRES_PASSWORD` dans `.env`, renseignez aussi `DB_URL`, `DB_USERNAME` et `DB_PASSWORD` dans le terminal qui lance Maven. Le fichier `.env` utilisé par Docker Compose n'est pas chargé automatiquement dans ce terminal.

## Parcours utilisateur

Depuis l'accueil, un visiteur peut déposer un fichier sans compte, puis copier son lien de partage. Après inscription et connexion, il peut aussi retrouver ses transferts dans « Mes fichiers », voir leurs tags et leur état, supprimer un fichier ou supprimer son compte. À l'envoi, la durée est comprise entre 1 et 7 jours ; le mot de passe de téléchargement est facultatif. Le destinataire ouvre le lien, consulte les informations du fichier, saisit le mot de passe si nécessaire et lance le téléchargement. Un transfert anonyme n'apparaît dans aucun historique.

## Stockage

Le mode par défaut est local et conserve les contenus dans `backend/storage`, répertoire ignoré par Git.

Pour utiliser S3, renseignez dans `.env` :

```dotenv
STORAGE_TYPE=s3
S3_BUCKET=nom-du-bucket-prive
S3_REGION=eu-west-3
AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...
```

`DOWNLOAD_STREAM_TIMEOUT=3600s` règle le délai maximal de téléchargement côté Spring MVC. Il est distinct de `DOWNLOAD_TICKET_EXPIRATION=PT1M`, qui limite le temps disponible pour démarrer le téléchargement après autorisation.

`AWS_SESSION_TOKEN` est disponible pour des identifiants temporaires. Lorsqu'une instance utilise S3, une erreur S3 fait échouer l'opération sans sauvegarde silencieuse en local.

## Vérifier le projet

Les scripts du dossier `quality/` servent de points d'entrée : ils affichent chaque étape et lancent les bons outils dans le bon ordre. L'extension `.mjs` indique simplement un fichier JavaScript exécuté par Node.js ; il n'est pas nécessaire de connaître sa syntaxe pour l'utiliser. La validation courante se lance depuis la racine avec :

```text
node quality/run-tests.mjs
```

Pour inclure Docker, les six scénarios de bout en bout et les captures de couverture :

```text
node quality/run-tests.mjs --all
```

Un test de bout en bout, ou E2E, reproduit un vrai parcours dans le navigateur, de l'interface jusqu'à la base de données. Les commandes ci-dessous restent utiles pour exécuter séparément une partie du projet.

Backend, avec contrôle JaCoCo :

```powershell
cd backend
mvn.cmd verify
```

Frontend :

```powershell
cd frontend
npm.cmd ci
$env:CHROME_BIN = "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
npm.cmd run test:coverage -- --browsers=ChromeHeadless
npm.cmd run build
```

Tests de bout en bout, après démarrage de l'application Docker :

```powershell
cd frontend
npm.cmd run test:e2e
```

Les tests navigateur sont configurés pour Microsoft Edge sous Windows. Les commandes détaillées, les résultats et les captures de couverture sont dans [TESTING.md](docs/TESTING.md).

Les tests de performance lourds sont séparés des suites courantes. `node quality/run-performance.mjs` affiche les scénarios disponibles sans en démarrer un. Leurs précautions et résultats sont décrits dans [PERF.md](docs/PERF.md).

## Pour aller plus loin

- [Architecture et conception](docs/architecture.md)
- [Documentation technique selon le modèle fourni](docs/documentation-technique.md)
- [Plan de tests](docs/TESTING.md)
- [Sécurité](docs/SECURITY.md)
- [Performance](docs/PERF.md)
- [Maintenance](docs/MAINTENANCE.md)

Important : ne versionnez jamais `.env`, les identifiants AWS, un JWT, un mot de passe ou les fichiers téléversés localement.

## Linux et macOS

Prérequis : Java 21, Maven 3.9, Node.js 22.12 ou plus récent dans la branche 22.x, npm, Docker et Compose. À la racine :

```bash
test -f .env || cp .env.example .env
# Renseigner JWT_SECRET dans .env.
docker compose up -d --build
curl --fail http://localhost:8080/actuator/health
```

Pour le développement, lancer `docker compose up -d postgres`, puis ouvrir deux terminaux à la racine :

```bash
# Terminal backend
cd backend
export JWT_SECRET="remplacer-par-un-secret-aleatoire-de-32-octets-minimum"
mvn spring-boot:run
```

```bash
# Terminal frontend
cd frontend
npm ci
npm start
```

Pour Karma, installer Chrome ou Chromium et définir `CHROME_BIN` si le navigateur n’est pas détecté. Exemple Linux : `export CHROME_BIN=/usr/bin/chromium` ; exemple macOS : `export CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"`.

Les parcours Playwright utilisent Edge sous Windows et Chromium sous Linux/macOS. Installer ce dernier dans `frontend` avec `npx playwright install chromium` (sous Linux, `npx playwright install --with-deps chromium` si les bibliothèques système manquent). Les lanceurs `node quality/run-tests.mjs` et `node quality/run-maintenance.mjs validate` fonctionnent sur les trois systèmes. Les tests lourds écrits en PowerShell nécessitent `pwsh` sous Unix.

La page `/legal` présente les mentions légales et la politique de confidentialité de l’application.
