# Performance de DataShare

> Améliorations de la solution : les mesures de charge, scans et captures antérieurs restent des références historiques.

## En bref

Les mesures cherchent à répondre à trois questions simples : l'envoi courant reste-t-il rapide, la limite réelle de 1 Go fonctionne-t-elle, et que se passe-t-il lorsque plusieurs gros fichiers arrivent en même temps ? Le poids du frontend et un téléchargement ralenti sont également contrôlés.

Les mesures ont été exécutées le 24 septembre 2026 sur la machine de développement, avec l'application complète lancée par Docker Compose et le stockage local. Elles décrivent uniquement cet environnement : elles ne garantissent pas les mêmes résultats sur un hébergement distant ou avec S3.

Le terme **p95** signifie que 95 % des mesures sont inférieures ou égales à la valeur indiquée. Il donne une meilleure idée des requêtes lentes qu'une moyenne seule.

## Upload courant avec k6

k6 est un outil de test de charge. Il crée ici cinq utilisateurs virtuels, c'est-à-dire cinq clients automatiques qui répètent le même envoi pendant 15 secondes. Il ne simule pas cinq personnes avec cinq comptes différents.

Scénario dans [k6-upload.js](../quality/k6-upload.js) :

- 5 utilisateurs virtuels constants pendant 15 secondes ;
- compte dédié créé dans `setup`, puis authentifié ;
- fichier texte multipart d'environ 112 kB ;
- suppression de chaque fichier après l'upload ;
- suppression du compte dans `teardown` ;
- seuils : 100 % des contrôles réussis, moins de 1 % de requêtes en erreur, p95 de l'upload inférieur à 1 000 ms.

Pour relancer ce scénario depuis la racine :

```text
node quality/run-performance.mjs k6
```

Le lanceur explique le scénario puis exécute une version fixe de k6 dans Docker. La logique du test reste lisible dans [k6-upload.js](../quality/k6-upload.js) : créer un compte, envoyer les fichiers, contrôler les réponses, nettoyer les données et vérifier les seuils. Après l'exécution, le lanceur retire du rapport une donnée technique contenant le JWT temporaire du compte de test. Les mesures ne sont pas modifiées.

Résultats :

| Mesure | Résultat |
|---|---:|
| Itérations d'upload | 375 |
| Requêtes HTTP totales | 753, soit 49,11 requêtes/s |
| Contrôles | 1 128 réussis, 0 échec |
| Taux de requêtes en erreur | 0 % |
| Upload moyen | 131,80 ms |
| Upload médian | 123,74 ms |
| Upload p95 | 139,75 ms |
| Upload maximal | 489,61 ms |
| Durée HTTP globale p95 | 133,24 ms |

Tous les seuils sont respectés. Dans ce scénario local, 95 % des uploads prennent au plus 139,75 ms, donc nettement moins que le budget fixé à une seconde. Le résultat complet se trouve dans [k6-upload-summary.json](../quality/k6-upload-summary.json).

## Test réel de la limite de 1 Go

Un second scénario isolé a vérifié la limite avec de vrais transferts HTTP. Le backend a été temporairement lancé en stockage local afin d'éviter environ 3 Go de trafic et de stockage AWS, puis la configuration S3 d'origine a été restaurée.

Le script [large-file-boundary-test.ps1](../quality/large-file-boundary-test.ps1) :

1. crée un compte dédié ;
2. génère un fichier de `1 000 000 000` octets sans le charger en mémoire ;
3. l'envoie en multipart à travers Nginx et Spring Boot ;
4. le télécharge en flux ;
5. compare sa taille et son SHA-256 ;
6. supprime ce fichier ;
7. génère `1 000 000 001` octets et vérifie le refus métier ;
8. supprime le compte et tous les fichiers temporaires.

Résultats du 24 septembre 2026 :

| Mesure | Résultat |
|---|---:|
| Taille exacte envoyée | 1 000 000 000 octets |
| Statut de l'upload exact | `201 Created` |
| Durée de l'upload exact | 12,44 s |
| Débit moyen d'upload | 80,38 MB/s |
| Taille téléchargée | 1 000 000 000 octets |
| Statut du téléchargement | `200 OK` |
| Durée du téléchargement | 12,20 s |
| Débit moyen de téléchargement | 81,94 MB/s |
| Contrôle d'intégrité | SHA-256 identique |
| Taille refusée | 1 000 000 001 octets |
| Réponse du dépassement | `413 FILE_TOO_LARGE` en 6,18 s |
| Nettoyage | Compte et fichiers supprimés |

Le rapport brut est disponible dans [large-file-boundary-result.json](../quality/large-file-boundary-result.json). Les logs backend confirment un corps multipart de `1 000 000 424` octets accepté puis `1 000 000 425` octets refusé. La différence de 424 octets correspond à l'enveloppe multipart et aux autres champs du formulaire.

Après avoir démarré le backend avec `STORAGE_TYPE=local`, ce contrôle se relance depuis la racine avec :

```text
node quality/run-performance.mjs limit-1gb --confirm-local-storage
```

Le lanceur vérifie lui-même le mode de stockage actif avant d'appeler le scénario. La confirmation reste obligatoire car ce test transfère environ 3 Go et sollicite fortement le disque. Sous Windows, il appelle le script PowerShell existant ; sur un autre système, `pwsh` doit être disponible.

Ce test valide trois propriétés concrètes : la limite exacte est inclusive, le téléchargement restitue les mêmes octets, et le premier octet au-dessus de la limite est refusé avec l'erreur prévue. Il ne s'agit pas d'un test de charge simultanée avec plusieurs fichiers de 1 Go.

## Comparaison avec cinq uploads simultanés

Un test de charge complémentaire a lancé cinq clients HTTP en parallèle pour trois tailles. `curl` a été retenu à la place de k6 pour ces gros corps multipart : chaque processus lit le fichier en flux, alors que k6 doit construire les corps de requête en mémoire et risquerait de consommer plusieurs gigaoctets dans le générateur de charge.

Le scénario utilise un compte de performance unique et cinq clients concurrents partageant son JWT. Cela correspond à cinq utilisateurs virtuels qui sollicitent simultanément l'endpoint ; le test mesure la capacité de traitement de l'upload, pas l'isolation entre cinq identités. Chaque fichier créé est supprimé avant le palier suivant.

Résultats du 24 septembre 2026, stockage local et boucle réseau Docker :

| Taille par client | Volume utile total | Succès | Durée murale | Moyenne par requête | p50 | p95 | Débit utile agrégé |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 100 Mo | 500 Mo | 5/5 | 2,63 s | 1,86 s | 1,87 s | 1,91 s | 189,83 MB/s |
| 500 Mo | 2,5 Go | 5/5 | 10,09 s | 9,50 s | 9,47 s | 9,71 s | 247,73 MB/s |
| 1 Go | 5 Go | 5/5 | 19,91 s | 19,31 s | 19,31 s | 19,43 s | 251,07 MB/s |

Les **15 uploads sur 15** ont retourné `201 Created`. Docker a comptabilisé environ 8,01 Go reçus par le backend, ce qui confirme que les fichiers ont réellement traversé le réseau local du conteneur. Aucun `OutOfMemoryError`, manque d'espace ou `StorageException` n'a été observé pendant l'exécution.

Le débit total atteint un plateau proche de 250 MB/s à partir du palier de 500 Mo. La durée par requête augmente presque proportionnellement à la taille : environ 1,9 seconde pour 100 Mo, 9,5 secondes pour 500 Mo et 19,3 secondes pour 1 Go. Cela indique un traitement régulier ; le disque, la copie multipart ou le réseau local entre les conteneurs devient probablement la ressource partagée principale.

L'upload isolé de 1 Go prenait 12,44 secondes. Avec cinq uploads simultanés, chaque requête prend environ 19,31 secondes en moyenne, soit environ 55 % de plus, tandis que le débit total atteint 251 MB/s. La concurrence améliore donc le débit global sans multiplier la capacité par cinq.

Avec seulement cinq mesures par palier, le p95 correspond pratiquement à la requête la plus lente. Ce chiffre facilite la comparaison entre les trois paliers, mais il n'a pas la stabilité statistique d'un test contenant des centaines d'itérations.

Le script reproductible est [concurrent-large-upload-test.ps1](../quality/concurrent-large-upload-test.ps1). Les résultats sont disponibles au format [JSON détaillé](../quality/concurrent-large-upload-result.json) et [CSV synthétique](../quality/concurrent-large-upload-summary.csv).

Après avoir démarré le backend en stockage local, le test se relance avec :

```text
node quality/run-performance.mjs concurrent-large --confirm-local-storage
```

Le lanceur contrôle la valeur réellement utilisée par le conteneur. La confirmation explicite protège contre un lancement involontaire : ce scénario transfère 8 Go utiles et doit rester séparé des vérifications quotidiennes.

## Métriques serveur

`ApiRequestMetricsFilter` produit une ligne `key=value` pour chaque appel `/api/**` :

```text
event=http_request method=POST endpoint_group=files status=201 duration_ms=119 request_bytes=112486
```

Pendant le test, les lignes d'upload observées se situent principalement entre 113 et 147 ms pour 112 486 octets reçus. Un extrait est conservé dans [backend-metrics-sample.log](../quality/backend-metrics-sample.log).

Le regroupement des routes évite d'inscrire les tokens de partage dans les journaux. `duration_ms` indique le temps de réponse, `request_bytes` le volume reçu et `status` le résultat HTTP. Ces journaux structurés couvrent les besoins de suivi actuels ; aucun serveur de métriques séparé n'est ajouté.

## Budget et métriques du frontend

Les budgets Angular sont :

- avertissement à 500 kB pour le bundle initial ;
- erreur à 1 MB pour le bundle initial ;
- avertissement à 6 kB et erreur à 8 kB par feuille de style de composant.

Le build de production réussit avec un bundle initial de **278,61 kB bruts** et **78,83 kB estimés transférés**. Il respecte donc le budget avec environ 221 kB de marge avant l'avertissement.

La page d'accueil a aussi été mesurée en navigateur Edge automatisé, application locale déjà démarrée :

| Mesure | Résultat |
|---|---:|
| Time to First Byte | 3,1 ms |
| DOMContentLoaded | 55,9 ms |
| Événement `load` | 56,5 ms |
| First Contentful Paint | 156 ms |
| Ressources transférées | 299 365 octets |
| JavaScript transféré | 283 388 octets |

Le résultat brut est dans [browser-performance.json](../quality/browser-performance.json). La mesure reproductible est :

```text
node quality/run-performance.mjs browser
```

## Vérification du téléchargement ralenti — 27 septembre 2026

Le test [slow-download-test.mjs](../quality/slow-download-test.mjs) suspend pendant 40 secondes la lecture d'un fichier de 64 Mio. Avec le délai asynchrone par défaut, le transfert était tronqué. `spring.mvc.async.request-timeout` utilise maintenant `DOWNLOAD_STREAM_TIMEOUT`, soit une heure par défaut. Cette durée concerne le transfert ; le ticket court doit seulement être valide à son ouverture.

La vérification locale du 27 septembre reçoit les 67 108 864 octets et retrouve le même SHA-256, en environ 40,3 secondes incluant la pause. Le [résultat brut](../quality/slow-download-result.json) est conservé. Ce résultat valide ce scénario de connexion ralentie ; il ne garantit pas les performances sur tout réseau.

```text
node quality/run-performance.mjs slow-download --confirm-local-storage
```

## Analyse et limites

Le téléchargement est transmis en flux par Spring et déclenché avec une navigation native du navigateur. Angular ne construit donc pas un `Blob` contenant tout le fichier en mémoire. Nginx désactive également le buffering du proxy sur `/api/`.

Le test k6 utilise un fichier modeste pour répéter assez de requêtes en peu de temps. Le test de frontière vérifie l'intégrité et la limite, puis le test concurrent compare cinq gros uploads simultanés. Une vérification sur un autre disque, un vrai réseau ou S3 produirait des chiffres différents.

Les métriques navigateur viennent des chronomètres intégrés au navigateur, pas d'un score Lighthouse. Cette solution réutilise Playwright, déjà présent pour les tests. Pour comparer deux versions de manière honnête, il faut conserver le même navigateur, la même machine et un état de cache comparable.

Sans argument, `node quality/run-performance.mjs` affiche les cinq scénarios disponibles et leur coût. Cette aide intégrée évite de mémoriser les commandes et empêche le lancement accidentel d'un test lourd.

## Améliorations de la solution

L’écriture du contenu se fait sans transaction ouverte ; une transaction courte enregistre ensuite les métadonnées. Une erreur de cette transaction déclenche une tentative de nettoyage. Un arrêt brutal entre les étapes peut laisser un fichier orphelin : prévoir une réconciliation du stockage si ce cas doit être garanti.

L’historique est paginé en SQL avec un ordre stable (date puis UUID), 20 éléments par défaut et 100 au maximum. Les filtres ACTIVE/EXPIRED s’appliquent en base avant pagination. Les pages très profondes restent un cas à optimiser par curseur si nécessaire.

La purge traite au plus 100 contenus expirés par passage et marque les contenus des transferts possédés comme supprimés. Un échec de suppression reste éligible au passage suivant. Un backlog important peut augmenter le délai de purge ; des échecs persistants sur les premiers éléments nécessitent une intervention.

Le limiteur refuse l’excès de requêtes en `429` avec `Retry-After`. Il est actif par défaut et ses budgets sont séparés pour les authentifications, uploads et autorisations de téléchargement. Derrière un proxy, la limite utilise l’adresse socket du proxy ; un déploiement public doit mettre un limiteur partagé au niveau du proxy et définir les adresses de confiance. Aucune adresse déclarée arbitrairement dans `X-Forwarded-For` n’est acceptée par le limiteur applicatif.

Le scénario k6 existant dépasse volontairement les limites de protection usuelles. Sur une instance locale réservée aux mesures, activer les limites plus élevées :

```text
docker compose -f compose.yaml -f compose.performance.yaml up -d backend
node quality/run-performance.mjs k6
docker compose up -d backend
```

Le lanceur vérifie cette configuration avant k6. Le dernier appel restaure les limites normales. Ne pas utiliser ce profil sur un service ouvert au public. Les anciens résultats k6 restent historiques ; ils ne mesurent pas la nouvelle version et ne constituent pas une preuve de résistance aux abus.
