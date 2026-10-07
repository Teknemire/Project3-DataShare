# Maintenance de DataShare

> Améliorations de la solution : les mesures de charge, scans et captures antérieurs restent des références historiques.

## À quoi sert ce document ?

Ce guide explique comment garder DataShare fiable dans le temps. Il couvre quatre situations concrètes : surveiller les dépendances, corriger un défaut, préparer une livraison et protéger les données pendant une intervention.

La plupart des contrôles sont regroupés dans un seul lanceur. La personne qui maintient le projet choisit une intention simple, par exemple `validate` ou `release`, et le script affiche ensuite chaque outil exécuté. Il ne modifie jamais les dépendances à la place de la personne responsable.

## Rythme conseillé

| Fréquence | Action |
|---|---|
| Avant chaque livraison | Builds, tests backend/frontend, parcours complets E2E, couverture, `npm audit`, scan Trivy |
| Une fois par mois pendant une période active | Vérifier les versions Angular/npm, Spring Boot/Maven, PostgreSQL, Nginx et images Docker |
| Dès publication d'une alerte critique utilisée par le projet | Évaluer puis mettre à jour la dépendance concernée sans attendre le cycle mensuel |
| Après toute modification de sécurité, stockage ou base | Rejouer les parcours E2E et le test d'intégration complet |

## Mettre à jour les dépendances

Une dépendance est une bibliothèque ou une image utilisée par le projet. La mise à jour se fait par petits groupes afin de comprendre facilement l'origine d'une éventuelle régression.

1. Partir d'un état Git propre et lire les notes de version de la dépendance visée.
2. Mettre à jour une famille cohérente à la fois : Angular, Spring Boot, AWS SDK ou image Docker.
3. Réinstaller avec le gestionnaire normal du projet ; ne pas modifier manuellement les dépendances transitives du lockfile.
4. Exécuter les commandes de validation ci-dessous.
5. Vérifier les changements de configuration, migrations, contrats API et avertissements de dépréciation.
6. Conserver la mise à jour dans un commit isolé pour permettre un retour arrière simple.

Le script [run-maintenance.mjs](../quality/run-maintenance.mjs) regroupe les contrôles par intention. Il se lit comme une liste d'étapes et évite d'avoir à mémoriser plusieurs commandes. Pour consulter les mises à jour disponibles sans modifier les dépendances :

```text
node quality/run-maintenance.mjs dependencies
```

Pour une validation quotidienne :

```text
node quality/run-maintenance.mjs validate
```

Avant une livraison, le mode complet enchaîne les tests, le build, les parcours E2E, les captures de couverture, `npm audit` et les deux scans Trivy. Leurs rapports sont générés localement et ne sont pas versionnés :

```text
node quality/run-maintenance.mjs release
```

Chaque étape est nommée dans le terminal. Le script s'arrête lorsqu'un contrôle obligatoire échoue. La lecture des notes de version, la décision de mise à jour et la vérification du résultat restent humaines.

Les risques à surveiller sont les changements majeurs d'Angular ou Spring Boot, les formats de configuration, les changements du SDK S3, la compatibilité Java 21, les migrations PostgreSQL et la taille du bundle frontend.

## Corriger un défaut

1. Reproduire le problème avec les entrées minimales et noter la réponse HTTP ou le message visible.
2. Identifier la couche responsable : composant Angular, contrôleur, service, repository ou stockage.
3. Ajouter ou adapter un test qui reproduit réellement le comportement lorsque cela protège une règle métier.
4. Appliquer une correction ciblée, sans nouvelle abstraction si elle n'est pas nécessaire.
5. Exécuter d'abord la suite concernée, puis `mvn verify`, la couverture frontend et les E2E si le parcours traverse plusieurs couches.
6. Vérifier qu'aucun secret, token ou contenu de fichier n'apparaît dans les logs ou les erreurs.
7. Documenter seulement les changements qui modifient l'installation, l'API, la sécurité ou une décision d'architecture.

En cas de problème après livraison, Git permet de revenir à la version précédente puis de reconstruire les images. Si la structure de la base a changé de manière incompatible, il faut aussi restaurer une sauvegarde correspondante.

## Protéger les données et le stockage

- Sauvegarder PostgreSQL avant toute évolution de schéma ou suppression massive.
- En stockage local, inclure `backend/storage` dans la stratégie de sauvegarde si les fichiers doivent être conservés.
- En S3, garder le bucket privé et vérifier les règles de cycle de vie avant modification.
- Ne jamais activer local et S3 simultanément. Un changement de stratégie ne migre pas automatiquement les fichiers déjà stockés.
- Après restauration, vérifier la cohérence entre chaque `storageKey` en base et le contenu présent dans le stockage.
- La purge d'expiration doit rester active ; les métadonnées des fichiers possédés restent visibles comme expirées, les transferts anonymes sont supprimés avec leur contenu.

Flyway exécute les migrations versionnées V1 et V2 au démarrage ; Hibernate vérifie leur conformité avec `ddl-auto=validate`. Une migration déjà exécutée ne doit jamais être modifiée : ajouter V3, V4, etc. La procédure de reprise d’une base existante figure plus bas.

## Configuration et secrets

Les valeurs sensibles viennent du fichier `.env` local ou de variables d'environnement : mot de passe PostgreSQL, clé de signature `JWT_SECRET` et identifiants AWS. Seul `.env.example`, qui contient des exemples, peut être partagé. Si la clé JWT change, les jetons de connexion déjà émis deviennent invalides ; il faut donc prévenir les utilisateurs et redémarrer le backend.

Après une modification de configuration :

```text
node quality/run-maintenance.mjs configuration
```

Ce mode valide Compose, reconstruit l'application, affiche l'état des services puis les 100 dernières lignes des journaux backend et frontend.

## Vérifier après une intervention

La vérification minimale comprend :

- ouverture de l'accueil et contrôle de l'affichage mobile ;
- inscription, connexion et appel de `/api/auth/me` ;
- upload d'un petit fichier autorisé et refus d'un exécutable ;
- téléchargement avec et sans mot de passe ;
- consultation de l'historique, suppression du fichier puis du compte ;
- absence d'erreur inattendue dans les logs ;
- espace disque disponible pour PostgreSQL et le stockage local.

Les résultats de référence et commandes complètes sont dans [TESTING.md](TESTING.md) et [PERF.md](PERF.md).

## Reprendre une base existante avec Flyway

Une base neuve exécute V1 puis V2 automatiquement. Une base créée auparavant par Hibernate n’a pas de table `flyway_schema_history` : le démarrage échoue par défaut, afin de ne pas l’adopter silencieusement.

1. Arrêter les services applicatifs avec `docker compose stop frontend backend`.
2. Sauvegarder la base et le stockage. Pour obtenir un dump sans conversion de l’encodage par PowerShell :

```text
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" -f /tmp/datashare-before-flyway.sql'
docker compose cp postgres:/tmp/datashare-before-flyway.sql ./datashare-before-flyway.sql
```

3. Examiner le schéma existant et vérifier qu’il correspond à V1 : `users`, `file_metadata`, `file_tag`, colonnes, types, contraintes et relations. Tester la reprise sur une copie restaurée de la sauvegarde. Le test automatisé de reprise utilise H2 ; il ne remplace pas cet essai PostgreSQL sur les données réelles.
4. Pour ce premier démarrage uniquement, définir `FLYWAY_BASELINE_ON_MIGRATE=true` dans `.env`. Flyway inscrit une baseline de version 1, conserve les données puis applique V2.
5. Redémarrer avec `docker compose up -d --build`, vérifier la santé et les parcours. Remettre ensuite `FLYWAY_BASELINE_ON_MIGRATE=false` dans `.env` et recréer le backend avec `docker compose up -d backend`.

Ne jamais activer la baseline pour contourner une divergence de schéma. Le dump contient des données personnelles : le garder hors Git, avec le stockage sauvegardé et les accès nécessaires à sa restauration. Les tables et colonnes ajoutées doivent ensuite être migrées par une nouvelle version SQL.

## Santé et événements de sécurité

`GET /actuator/health` est public et renvoie le statut global ; les noms des groupes de sondes standard peuvent aussi apparaître dans un conteneur. La base est vérifiée par Actuator. Compose attend le backend sain avant de démarrer le frontend. Les autres endpoints Actuator ne sont pas exposés. Ce contrôle ne teste pas continuellement le contenu de chaque objet local/S3.

Les lignes `event=security` consignent les connexions/inscriptions, refus d’authentification, suppressions et refus pour dépassement de débit. Les événements contiennent une action, une catégorie d’endpoint, un résultat et un statut ; aucun identifiant personnel, chemin de partage ou secret. Consulter avec `docker compose logs backend` et rechercher `event=security`. Configurer la collecte, la rotation, l’accès et la conservation des journaux selon l’environnement ; ce journal applicatif n’est pas un registre d’audit immuable.
