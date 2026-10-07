# Plan de tests DataShare

> Améliorations de la solution : les mesures de charge, scans et captures antérieurs restent des références historiques.

## En bref

Les tests vérifient que DataShare fonctionne comme prévu, aussi bien sur une règle isolée que sur un parcours complet dans le navigateur. La priorité porte sur l'authentification, le transfert, le partage, le téléchargement, l'historique, la suppression et la protection des données.

Les suites courantes ont été revérifiées le 29 septembre 2026. Les mesures lourdes de 1 Go et de charge restent celles du 24 septembre et n'ont pas été rejouées avec les améliorations récentes.

La validation de la solution améliorée est distincte : 85 tests backend, 53 tests frontend, 6 scénarios Playwright sur PostgreSQL, ESLint et PMD sans violation. Les couvertures de lignes sont 89,43 % et 77,40 %. JaCoCo est configuré avec `append=false` pour ne pas cumuler les anciennes exécutions.

Les tests sont répartis à trois niveaux :

- tests unitaires et tests HTTP ciblés pour isoler les règles métier et les contrôleurs ;
- test d'intégration Spring Boot pour traverser la sécurité, les contrôleurs, la base et le stockage local ;
- tests Playwright de bout en bout contre l'application Docker et PostgreSQL. Ces tests E2E reproduisent un parcours réel, de l'écran utilisateur jusqu'au backend et à la base.

## Plan de tests

| Fonctionnalité critique | Type de test | Critère d'acceptation | Couverture actuelle |
|---|---|---|---|
| Inscription et connexion | Backend HTTP, frontend unitaire, E2E | Compte créé, mot de passe hashé, JWT émis, `/me` accessible | Automatisée |
| Contrôle d'accès | Backend service/HTTP | Une route privée refuse un JWT absent ou invalide ; un fichier d'un autre utilisateur reste inaccessible | Automatisée |
| Upload local et S3 | Backend unitaire, intégration, E2E | Métadonnées enregistrées, contenu stocké par la stratégie active, lien non prédictible retourné | Automatisée ; S3 simulé en test et validé manuellement sur AWS |
| Validation du fichier | Backend unitaire/HTTP | Fichier vide, trop volumineux, type interdit ou signature exécutable refusé | Automatisée |
| Mot de passe de partage | Backend service/HTTP, intégration, E2E | Secret absent ou incorrect : même `401 DOWNLOAD_AUTH_FAILED` ; secret correct : ticket court | Automatisée |
| Téléchargement | Backend HTTP/intégration, E2E | Contenu identique, nom conservé, réponse en pièce jointe et transmise en flux | Automatisée |
| Expiration | Backend unitaire | Un partage expiré ne peut plus être téléchargé ; contenu purgé selon le propriétaire | Automatisée |
| Historique et tags | Backend/frontend unitaires, E2E | Seuls les fichiers du compte apparaissent avec état calculé et tags | Automatisée |
| Suppression d'un fichier | Backend/frontend unitaires, intégration, E2E | Seul le propriétaire supprime contenu et métadonnée après confirmation | Automatisée |
| Suppression du compte | Backend/frontend unitaires, E2E | Mot de passe et confirmation requis ; fichiers, métadonnées et compte supprimés | Automatisée |
| Upload anonyme | Backend/frontend unitaires, E2E | Upload possible sans JWT, sans tags ni historique propriétaire | Automatisée |
| Accessibilité et mobile | Tests de composants, axe-core, E2E mobile | Aucun défaut WCAG automatisable sur les écrans testés ; menu mobile utilisable ; aucun débordement horizontal | Automatisée et vérification visuelle |
| Limite réelle de 1 Go | HTTP multipart, téléchargement en flux et SHA-256 | 1 000 000 000 octets acceptés et restitués à l'identique ; 1 000 000 001 octets refusés en `413 FILE_TOO_LARGE` | Automatisée sur stockage local |
| Charge de fichiers volumineux | 5 clients HTTP simultanés à 100 Mo, 500 Mo et 1 Go | 15 uploads sur 15 réussis, sans erreur serveur | Automatisée séparément sur stockage local |

## Relancer les contrôles

Le lanceur [run-tests.mjs](../quality/run-tests.mjs) rassemble les commandes dans leur ordre logique et décrit chaque étape dans le terminal. Un fichier `.mjs` est simplement un script JavaScript lancé avec Node.js : il peut être exécuté sans maîtriser son code. Ce point d'entrée évite de devoir retenir les changements de dossier, les noms des outils et la configuration du navigateur.

Validation quotidienne :

```text
node quality/run-tests.mjs
```

Le script exécute les tests backend, les tests Angular avec mesure de couverture, puis le build de production. Maven produit le rapport JaCoCo et échoue si moins de 70 % des lignes backend sont exécutées par les tests.

Validation complète avant livraison :

```text
node quality/run-tests.mjs --all
```

L'option `--all` ajoute la reconstruction Docker, les scénarios Playwright contre Angular, Spring Boot et PostgreSQL, puis la régénération des captures de couverture. Le fichier est volontairement court et commenté : on peut le relire avant une soutenance ou une livraison pour comprendre précisément ce qui sera lancé.

## Résultats vérifiés le 29 septembre 2026

| Suite | Résultat | Couverture |
|---|---:|---:|
| Spring Boot / JUnit / MockMvc / H2 | 76 tests réussis | 86,68 % des lignes ; 89,20 % des instructions ; 69,77 % des branches |
| Angular / Jasmine / Karma | 51 tests réussis | 78,10 % des lignes ; 78,92 % des instructions déclaratives (`statements`) ; 51,20 % des branches |
| Playwright fonctionnel | 3 scénarios réussis | Parcours critiques réels |
| Playwright accessibilité et mobile | 3 scénarios réussis | 7 écrans contrôlés par axe-core, plus navigation mobile |
| Build Angular de production | Réussi | Bundle initial : 278,61 kB bruts, 78,83 kB estimés transférés |
| Frontière réelle de taille — mesure du 24 septembre | Réussi | 1 Go accepté et vérifié par SHA-256 ; 1 Go + 1 octet refusé |
| Charge concurrente volumineuse — mesure du 24 septembre | Réussi | 5/5 uploads réussis à chacun des trois paliers ; p95 de 1,91 s, 9,71 s et 19,43 s |

L'objectif de 70 % est atteint sur les lignes, côté backend et côté frontend. La couverture indique la part du code parcourue pendant les tests ; elle ne prouve pas à elle seule que tous les comportements sont corrects. La couverture des branches, qui mesure les différents chemins possibles dans les conditions, est plus basse et reste indiquée dans les rapports. Les scénarios critiques complètent donc ces pourcentages en vérifiant les effets réels.

Captures exploitables :

- [rapport backend JaCoCo](evidence/coverage/backend-jacoco.png) ;
- [rapport frontend Karma/Istanbul](evidence/coverage/frontend-karma.png).

Les rapports HTML détaillés sont générés localement dans `backend/target/site/jacoco/index.html` et `frontend/coverage/frontend/index.html`. Ces répertoires générés ne sont pas destinés à Git.

## Régressions corrigées et vérifiées

- Suppression puis recréation avec le même email : l'ancien JWT reçoit `401` pour `/me`, l'historique, la consultation, l'upload et la suppression d'un fichier ; le nouveau compte conserve ses droits.
- Mots de passe UTF-8 : 36 caractères `é` (72 octets) sont acceptés ; 40 (80 octets) sont refusés proprement. Les tests Angular vérifient aussi les frontières ASCII et emoji. Un mot de passe de téléchargement absent ou incorrect garde la même réponse `401`.
- Ticket de téléchargement : une représentation Base64 URL non canonique est refusée, même si elle se décode vers les mêmes octets que la signature attendue. Le test couvre aussi le mauvais token, l'altération et l'expiration.
- OpenAPI : champs dans le multipart, authentification facultative de l'upload, erreurs `ApiError` et réponse binaire contrôlés depuis `/v3/api-docs`.
- Client lent : 64 Mio reçus intégralement après une pause de lecture de 40 secondes, avec un SHA-256 identique. Ce test réel distinct évite d'allonger toutes les suites unitaires.

Depuis la racine, application démarrée en stockage local :

```text
node quality/run-performance.mjs slow-download --confirm-local-storage
```

Le lanceur vérifie d'abord que le conteneur utilise le stockage local, puis appelle le scénario dédié. `BASE_URL` peut désigner un environnement isolé. Le scénario crée son propre compte, nettoie ses données puis termine en erreur si le transfert est tronqué. La preuve est dans [slow-download-result.json](../quality/slow-download-result.json).

## Choix et limites

Le test d'intégration backend utilise H2 en mode de compatibilité PostgreSQL pour rester rapide et autonome. Les tests E2E utilisent la vraie base PostgreSQL du fichier Compose et compensent cette différence sur les parcours principaux.

Les vérifications axe-core couvrent les règles WCAG A/AA automatisables. Elles ne remplacent pas un audit RGAA complet ni un essai approfondi avec plusieurs lecteurs d'écran. Les interactions clavier essentielles utilisent des éléments HTML natifs et sont aussi vérifiées dans les tests de composants.

La stratégie S3 est testée avec un client simulé afin de vérifier les appels et les erreurs ; une validation manuelle a également été réalisée avec le bucket AWS configuré. Aucun vrai bucket n'est requis pour exécuter la suite automatique.

Le test de frontière de 1 Go est volontairement séparé des suites courantes : il transfère environ 3 Go sur la boucle locale et sollicite fortement le disque. Son scénario et son résultat sont conservés dans `quality/large-file-boundary-test.ps1` et `quality/large-file-boundary-result.json`. Le point d'entrée commun `run-performance.mjs` vérifie le stockage local et exige une confirmation explicite afin d'éviter un transfert AWS involontaire.

Le test concurrent est lui aussi séparé de la validation quotidienne. Il transfère 8 Go utiles et lance cinq clients en parallèle pour chaque palier. Les cinq clients réutilisent un compte de performance unique : le scénario mesure la concurrence sur l'upload, pas l'isolation entre cinq comptes. Le script nettoie chaque palier avant le suivant et exige également la confirmation du stockage local.
