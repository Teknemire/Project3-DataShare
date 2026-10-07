# Sécurité de DataShare

## En bref

DataShare protège les comptes, les liens de partage et les fichiers à plusieurs niveaux. Le navigateur ne décide jamais seul des droits : le backend revérifie l'identité, le propriétaire, la taille, le type du fichier et l'autorisation de téléchargement.

Ce document présente les protections réellement mises en place et les scans réalisés jusqu'au 29 septembre 2026. Il ne s'agit pas d'un audit de sécurité complet ni d'une certification.

## Résultats des scans

`npm audit` recherche les vulnérabilités connues dans les dépendances JavaScript. Trivy effectue un contrôle comparable sur les fichiers de dépendances et sur le JAR, c'est-à-dire l'application backend réellement construite.

| Outil | Périmètre | Résultat |
|---|---|---|
| `npm audit` — revérifié le 29 septembre | Toutes les dépendances npm installées, production et développement | 0 vulnérabilité, tous niveaux confondus |
| Trivy 0.74.0 — 29 septembre | `frontend/package-lock.json` et `backend/pom.xml`, vulnérabilités corrigibles HIGH/CRITICAL | 0 vulnérabilité HIGH ou CRITICAL détectée |
| Trivy 0.74.0 `rootfs` — 29 septembre | 146 dépendances réellement contenues dans le JAR construit | 0 vulnérabilité HIGH ou CRITICAL détectée |

Le contrôle du JAR du 27 septembre avait signalé trois vulnérabilités connues, ou CVE, sur `tomcat-embed-core:11.0.24` : `CVE-2026-65182`, `CVE-2026-65905` et `CVE-2026-68525`. Le 29 septembre, les modules Tomcat embarqués ont été alignés sur `11.0.26`. Cette version a été retenue plutôt que `11.0.25`, car les [avis Apache](https://tomcat.apache.org/security-11.html) publiés depuis signalent aussi des défauts affectant la version intermédiaire.

Le premier rescan du 29 septembre a ensuite détecté `CVE-2026-68497` dans les deux générations de Jackson présentes dans le JAR. Les BOM Jackson 2 et 3 ont été mis à jour respectivement vers `2.21.6` et `3.1.6`. Après reconstruction, les 76 tests backend et les 6 scénarios Playwright passent ; le rescan final du JAR ne détecte plus de vulnérabilité HIGH ou CRITICAL.

Le scan des manifestes avertit qu'il ne peut pas résoudre certaines versions Maven héritées. Son zéro ne doit donc pas être présenté seul comme un bilan de toutes les dépendances Java : le scan du JAR construit est la vérification complémentaire. Le [rapport JAR](../quality/trivy-jar-report.json) conserve le résultat complet.

Le script [run-security.mjs](../quality/run-security.mjs) permet de refaire le même contrôle sans retenir toute la suite de commandes. Il construit et teste d'abord le backend, puis affiche les contrôles dans leur ordre d'exécution :

```text
node quality/run-security.mjs
```

Ses quatre étapes sont : tests et construction Maven, `npm audit`, scan Trivy des fichiers de dépendances, puis scan du contenu réel du JAR. Le cache Maven est seulement lu par Trivy pour identifier les versions déjà téléchargées. Le script s'arrête si un contrôle obligatoire échoue et signale séparément les résultats de `npm audit`.

Les preuves brutes sont conservées dans `quality/`, hors des répertoires `backend/` et `frontend/` :

- [résultat npm audit](../quality/npm-audit.json) ;
- [résultat Trivy](../quality/trivy-report.txt).

Les options exactes de `npm audit` et Trivy restent lisibles dans le script. Elles sont donc versionnées et revues comme le code, au lieu de dépendre d'une suite de commandes mémorisée.

Une tentative de mesure avec une dépendance Lighthouse a fait apparaître des vulnérabilités de développement transitives. Cette dépendance n'était pas nécessaire au produit : elle a été retirée, puis `npm audit` a été relancé avec un résultat nul. Les métriques navigateur sont obtenues avec Playwright, déjà utilisé par les E2E.

## Mesures appliquées

### Authentification et autorisation

- Les mots de passe des comptes et des partages sont transformés avec BCrypt en une empreinte irréversible, appelée hash ; les valeurs en clair ne sont ni enregistrées ni renvoyées. Leur taille est limitée à 72 octets UTF-8 avant le hash, sans troncature. Un dépassement à la création produit `400`, et lors de la vérification il est traité comme un mot de passe incorrect.
- Spring Security valide les JWT, c'est-à-dire les jetons de connexion signés, leur émetteur et leur expiration. Il vérifie aussi en base le couple `uid` / email. Un jeton associé à un compte supprimé reste refusé après recréation avec le même email. Le secret vient de `JWT_SECRET` et reste hors de Git.
- Le backend déduit le propriétaire depuis l'identité authentifiée. Aucun `userId` envoyé par le navigateur ne décide de l'autorisation.
- Les routes privées sont sans session serveur. Le JWT est conservé dans `sessionStorage` et ajouté uniquement aux appels concernés.
- La suppression du compte exige le mot de passe courant et une confirmation explicite dans l'interface.

### Partage et fichiers

- Le token public est produit avec `SecureRandom` et une contrainte d'unicité en base.
- Un fichier protégé retourne la même erreur `401 DOWNLOAD_AUTH_FAILED` si le mot de passe est absent ou incorrect.
- Après autorisation, le serveur produit un ticket signé lié au lien et valable au plus 60 secondes. La signature HMAC-SHA-256 permet de détecter toute modification. Le contenu est vérifié à nouveau avant ouverture et l'encodage Base64 URL doit être canonique, afin qu'une représentation textuelle modifiée soit refusée même si elle produit les mêmes octets décodés.
- Les téléchargements utilisent `Content-Disposition: attachment`, `Cache-Control: no-store` et `X-Content-Type-Options: nosniff`.
- La taille maximale est de 1 000 000 000 octets. Une liste blanche d'extensions usuelles est appliquée, avec refus des exécutables/scripts et détection simple des signatures PE, ELF et Mach-O.
- Le nom original est nettoyé et n'est jamais utilisé comme chemin. Le stockage repose sur une clé UUID ; le stockage local vérifie que le chemin résolu reste dans sa racine.
- Le bucket S3 doit rester privé. Si S3 est sélectionné et échoue, la requête échoue sans copie silencieuse en local.

### Navigateur et API

Nginx ajoute une politique CSP restrictive, refuse l'intégration dans une frame, désactive la détection approximative des types MIME et limite caméra, micro et géolocalisation. Les réponses vérifiées exposent :

```text
Content-Security-Policy: default-src 'self'; ... object-src 'none'; frame-ancestors 'none'
Permissions-Policy: camera=(), microphone=(), geolocation=()
Referrer-Policy: no-referrer
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
```

Angular affiche les données avec son échappement normal ; aucune insertion HTML non fiable ni désactivation du sanitizer n'est utilisée. JPA paramètre les requêtes applicatives.

La protection CSRF, conçue notamment contre l'envoi automatique de cookies à l'insu de l'utilisateur, est désactivée ici car l'API utilise un JWT explicitement ajouté par Angular et aucun cookie d'authentification. Ce choix devra être revu si l'authentification passe un jour à des cookies.

## Journaux et secrets

Le filtre de métriques journalise uniquement la méthode HTTP, un groupe d'endpoint, le statut, la durée et la taille de requête. Il n'enregistre ni URL complète, ni token de partage, ni JWT, ni mot de passe, ni contenu de fichier. Le journal d'accès Nginx est désactivé, car son format standard inscrirait les tokens et tickets présents dans les URLs publiques.

Le lanceur du test k6 retire également `setup_data` du rapport JSON après son écriture. Cette section, ajoutée automatiquement par k6, contient les données transmises de `setup()` aux utilisateurs virtuels, dont le JWT temporaire du compte de performance. Les métriques de charge sont conservées.

Les secrets et identifiants AWS sont fournis par variables d'environnement. `.env` ne doit pas être versionné ; `.env.example` ne contient que des valeurs factices.

## Limites connues

- Pas d'antivirus ni d'analyse profonde des archives ; elles sont stockées et téléchargées sans extraction.
- Pas de limitation automatique du débit ou des tentatives de connexion.
- HTTPS doit être assuré par l'hébergement ou un reverse proxy en cas d'exposition distante ; le Compose local utilise HTTP.
- Trivy avertit que certaines versions Maven héritées du parent ne sont pas directement lisibles dans le `pom.xml`. Les tests Maven et la veille de dépendances restent donc nécessaires.
- Aucun test d'intrusion complet ni certification RGAA/RGPD n'est revendiqué.
