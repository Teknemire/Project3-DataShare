# Sécurité de DataShare

> Améliorations de la solution : les mesures de charge, scans et captures antérieurs restent des références historiques.

## En bref

DataShare protège les comptes, les liens de partage et les fichiers à plusieurs niveaux. Le navigateur ne décide jamais seul des droits : le backend revérifie l'identité, le propriétaire, la taille, le type du fichier et l'autorisation de téléchargement.

Ce document présente les protections réellement mises en place et la procédure de contrôle reproductible. Il ne s'agit pas d'un audit de sécurité complet ni d'une certification.

## Contrôle reproductible des dépendances

`npm audit` recherche les vulnérabilités connues dans les dépendances JavaScript. Trivy analyse les manifestes du projet puis le JAR backend réellement construit. Le script [run-security.mjs](../quality/run-security.mjs) enchaîne ces contrôles dans leur ordre d'exécution :

```text
node quality/run-security.mjs
```

Ses quatre étapes sont : tests et construction Maven, `npm audit`, scan Trivy des fichiers de dépendances, puis scan du contenu réel du JAR. Le cache Maven est seulement lu par Trivy pour identifier les versions déjà téléchargées. Le script s'arrête si un contrôle obligatoire échoue.

Le rapport `npm audit` complet conserve aussi les alertes liées aux outils de développement. Le contrôle de livraison bloque sur l'audit `--omit=dev`, qui correspond aux dépendances embarquées en production. Une alerte limitée aux outils de test reste affichée et doit être réévaluée lors de chaque mise à jour ; elle n'est pas masquée par le lanceur.

Les rapports produits dans `quality/` restent locaux et ne sont pas versionnés. Il faut relancer le script avant chaque livraison pour obtenir un résultat fondé sur les bases de vulnérabilités disponibles à cette date. Le scan des manifestes peut manquer certaines versions Maven héritées ; l'analyse du JAR construit complète donc ce contrôle. Les options exactes restent lisibles dans le script versionné.

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
- Limitation en mémoire sur une instance : 120 requêtes API, 10 tentatives de connexion/inscription, 10 uploads et 20 autorisations de téléchargement par minute et par adresse socket. Une architecture distribuée nécessite un limiteur partagé.
- HTTPS doit être assuré par l'hébergement ou un reverse proxy en cas d'exposition distante ; le Compose local utilise HTTP.
- Trivy avertit que certaines versions Maven héritées du parent ne sont pas directement lisibles dans le `pom.xml`. Les tests Maven et la veille de dépendances restent donc nécessaires.
- Aucun test d'intrusion complet ni certification RGAA/RGPD n'est revendiqué.
