# Contrat de l'API VigieChiro

L'application dépose les nuits (participations + fichiers) et réimporte les résultats Tadarida via l'**API
REST VigieChiro** (backend [Python-Eve](https://docs.python-eve.org/)). Cette API est un **tiers que nous ne
contrôlons pas** : son schéma peut évoluer sans préavis. Pour ne pas nous faire surprendre, notre
compréhension de l'API est **exécutable** : une suite qui tape l'API réelle et échoue si elle a bougé.

!!! info "Trois couches, une seule source de vérité"
    - **REST-assured** (`ContratApiVigieChiroLiveTest`, `@Tag("api-live")`) : le contrat **autoritatif**,
      dans le repo, exécuté à la main avant/après toute évolution touchant l'API. Il valide l'API brute
      **et** exerce notre `ClientVigieChiro` (détection de dérive côté client).
    - **JSON Schema** (`src/test/resources/vigiechiro/{participation,site}.schema.json`) : **la** définition
      machine-lisible du schéma observé. REST-assured valide les réponses contre lui.
    - **Postman + Newman** (`dev-docs/api/vigiechiro.postman_collection.json`) : couche
      **exploration/partage** + smoke run headless. Vérifications légères seulement (pas de re-encodage du
      schéma).

## Lancer la vérification

!!! warning "Jamais en CI"
    Ces vérifications frappent l'API **de production** et exigent un token 14 j. Elles sont **exclues du
    build par défaut** (`surefire.excludedGroups=api-live`) et ne tournent qu'à la demande.

Récupérer un token : sur le site VigieChiro connecté, exécuter le marque-page qui lit
`localStorage['auth-session-token']`.

=== "REST-assured (référence)"

    ```bash
    # Lecture seule (idempotent, sûr) :
    ./mvnw -Papi-live test -Dvigiechiro.token=XXXX

    # + probes d'écriture (POST/PATCH/upload, sur une participation « banc d'essai ») :
    ./mvnw -Papi-live test -Dvigiechiro.token=XXXX -Dvigiechiro.write=true
    ```

    Sans `-Dvigiechiro.token`, la suite se **skippe** proprement (aucun échec accidentel).

=== "Newman (smoke)"

    ```bash
    newman run dev-docs/api/vigiechiro.postman_collection.json --env-var token=XXXX
    # rapport HTML : ... --reporters cli,htmlextra
    ```

    Collection : [vigiechiro.postman_collection.json](api/vigiechiro.postman_collection.json) (importable
    aussi dans Postman pour l'exploration interactive).

## Ce que nous savons de l'API

Base : `https://vigiechiro.herokuapp.com/api/v1`. Auth : `Authorization: Basic base64("<token>:")` (token en
*username*, mot de passe vide).

### Endpoints utilisés

| Méthode | Chemin | Usage |
|---|---|---|
| GET | `/moi` | profil de l'observateur connecté (valide le token) |
| GET | `/moi/participations` | collection Eve `_items` de mes participations (+ sites embarqués) |
| GET | `/participations/{id}` | participation détaillée (schéma canonique, `_etag`, `traitement`) |
| GET | `/participations/{id}/donnees` | résultats Tadarida (paginé) — sert à l'import |
| GET | `/taxons/liste` | référentiel taxons |
| POST | `/sites/{id}/participations` | crée une participation |
| PATCH | `/participations/{id}` (`If-Match: _etag`) | pousse météo/config depuis la modale du passage |
| POST | `/fichiers` puis `PUT` S3 signé puis POST `/fichiers/{id}` | téléverse un fichier (3 temps, `PUT` **en flux**) |

### Objet `participation` (schéma canonique)

```json
{
  "point": "Z41",
  "date_debut": "2026-07-03T19:00:00+00:00",
  "date_fin": "2026-07-04T04:00:00+00:00",
  "meteo": { "vent": "FAIBLE", "couverture": "0-25" },
  "configuration": {
    "detecteur_enregistreur_type": "PassiveRecorder",
    "detecteur_enregistreur_numserie": "1997632",
    "micro0_type": "ICS", "micro0_position": "CANOPEE", "micro0_hauteur": "4"
  },
  "traitement": { "etat": "FINI" },
  "_etag": "83555259248249459dbab1ba734c1faa"
}
```

!!! danger "Pièges vérifiés en réel (ils nous ont mordus)"
    - **Pas de champ `numero`** : Eve le refuse (`422 {"numero": "invalid field"}`).
    - **Dates** : Eve **refuse l'ISO 8601** en entrée (`422 must be of datetime type`) ; il faut du
      **RFC 1123** (`Sat, 04 Jul 2026 19:00:00 GMT`). En sortie, Eve renvoie de l'ISO UTC (`+00:00`).
    - **`meteo`** ne contient **que** `vent` (`NUL|FAIBLE|MOYEN|FORT`) et `couverture`
      (`0-25|25-50|50-75|75-100`) : **pas de températures**.
    - **`_etag`** est requis en en-tête `If-Match` pour tout `PATCH`/`PUT`/`DELETE` (concurrence Eve).
    - **`traitement.etat`** (`FINI`, …) est un signal léger « résultats Tadarida prêts ».

### Objet `site`

Le `site` porte un tableau `localites` (chacune = un **point d'écoute** : `nom` + géométrie `Point`), plus
`titre`, `protocole`, `grille_stoc`, `_etag`. Détail dans `src/test/resources/vigiechiro/site.schema.json`.

### Objet `donnee` (résultats Tadarida) et ancrage des corrections

`GET /participations/{id}/donnees` renvoie une collection Eve **paginée** (`_meta.max_results = 20` par
page ; la participation canonique `6a4961f5…` en a **4806**, soit 241 pages). Chaque **donnée** correspond
à **un fichier WAV** traité :

```json
{
  "_id": "6a4fcaa2842983a29ba25363",
  "titre": "Car130711-2026-Pass1-Z41-PaRecPR1997632_20260703_220529_000",
  "publique": true,
  "observations": [
    {
      "frequence_mediane": 153.0, "temps_debut": 0.1, "temps_fin": 5.0,
      "tadarida_probabilite": 0.9,
      "tadarida_taxon": { "_id": "5526cd5a…", "libelle_court": "noise", "libelle_long": "bruit" },
      "tadarida_taxon_autre": [ { "taxon": { "_id": "…", "libelle_court": "Tetvir" }, "probabilite": 0.02 } ]
    }
  ],
  "_etag": "…"
}
```

Points vérifiés en réel (reconnaissance #1135, 2026-07-12, lecture seule) :

- la **donnée porte un `_id`** (et un `titre` = nom du WAV **sans extension**) : elle est adressable ;
- une **observation n'a PAS d'`_id`** : c'est un **sous-document positionnel** de `observations`. Ses champs
  sont `frequence_mediane`, `temps_debut`, `temps_fin`, `tadarida_probabilite`, `tadarida_taxon` (le **taxon
  complet embarqué**, pas un simple objectid) et `tadarida_taxon_autre` (liste rangée `{taxon, probabilite}`) ;
- **aucun champ `observateur_*`** n'est présent tant qu'aucune correction n'a été poussée.

!!! warning "Ancrage d'une correction (#723 / #1139)"
    Les observations n'ayant pas d'`_id`, une correction se cible par le couple **(`donnee._id`, indice dans
    `observations`)**, pas par un identifiant d'observation. Le modèle `PATCH /donnees/{id}/observations/
    {id_observation}` du périmètre initial de #723 est donc à revoir : l'observation n'est pas une ressource
    REST autonome. À trancher lors de l'ancrage (#1139) : soit un `PATCH` de la **donnée** avec son tableau
    `observations` mis à jour (`If-Match: _etag`), soit une sous-ressource **positionnelle** si Eve l'expose.
    Le taxon à envoyer est un **objectid** (cf. mapping code ↔ objectid #717).

## Cycle de vie d'une participation (EPIC #941)

Le cycle est **naturel** depuis la refonte : la participation est **créée à l'import** (best-effort,
si connecté et site rattaché), **synchronisée** depuis la modale « Modifier le passage » (push
météo/micro à la validation, pull « Synchroniser depuis VigieChiro »), puis **réutilisée au dépôt**
via le lien `ENTITE_PASSAGE` (créée en repli si l'import s'est fait hors connexion). La passerelle
`SynchronisationParticipation` (feature `passage`) porte créer/pousser/tirer ; `DepotVigieChiro`
(feature `lot`) ne fait que l'upload.

### Dépôt reprenable par unité

Le dépôt persiste son avancement **fichier par fichier** (table `depot_unite` : statut
`a_deposer|en_cours|depose|echec`, id distant, raison d'échec). Statuts **honnêtes** du passage :
« Dépôt en cours » dès le premier téléversement entamé, « Déposé » **seulement** quand toutes les
unités sont en ligne. Une interruption laisse « Dépôt en cours » ; la tentative suivante ne
re-téléverse que les unités non confirmées (« Retenter les échecs » dans M-Lot). Détail : moteur
`lot/model/DepotVigieChiro`, suivi IHM `SuiviDepot` → table de dépôt (socle « suivi par unité »,
cf. [Patterns](patterns.md)).

### Réconciliation avec le serveur avant dépôt (#1046)

Deux garde-fous s'exécutent au début de chaque dépôt (IHM et CLI) :

- **Pré-vol « la bonne nuit au bon endroit »** : la participation liée est relue
  (`GET /participations/{id}`) et comparée au passage local — même **point** (code localité) et même
  **nuit** (date UTC de `date_debut` : le mappeur pousse la date du passage telle quelle en UTC).
  Tout écart (point différent, nuit différente, participation injoignable) **refuse le dépôt** avec
  le détail, avant toute écriture. Porté par `SynchronisationParticipation.ecartsAvecDistant`.
- **Réconciliation des unités** : les titres de `donnees` (nom du WAV **sans extension**) marquent
  `depose` les unités WAV **déjà traitées** côté plateforme, qui ne seront jamais re-téléversées.
  Limites : `donnees` n'existe qu'**après traitement** (un fichier téléversé mais pas encore traité
  sera re-téléversé, sans conséquence) ; les archives **ZIP ne sont pas appariables** par titre
  (contenu inconnu localement) ; il n'existe **aucun inventaire lisible des uploads avant
  traitement** (`GET /fichiers` et `GET /participations/{id}/fichiers` → 403) — mais **après**
  traitement, le journal (§ ci-dessous) en fournit un complet.

### Journal de traitement d'une participation (#1132)

Le serveur trace le traitement de chaque participation dans un journal texte, accessible avec le
token en trois requêtes :

1. `GET /participations/{id}` → champ **`logs`** = document `fichiers` (`_id`, `disponible`) ;
2. `GET /fichiers/{logs._id}/acces` → `{"s3_signed_url": …}` (ou `302` avec `?redirection=true`) ;
3. `GET` de l'URL signée, **sans** en-tête `Authorization` (la signature de l'URL fait foi, un
   en-tête surnuméraire est refusé par S3).

Contenu observé (participation canonique `6a4961f5…`, ~1 Mo) : extraction de chaque archive avec
**inventaire** (`Archive contained: {'application/zip': 1, 'audio/wav': N}` — somme = 4806, le
compte exact des `donnees`), **chaque WAV nommé** dans la sortie TadaridaD, suppression des zips de
S3 après extraction, TadaridaD en **expansion x10**. C'est la **vérification a posteriori** d'un
dépôt — la seule capable de vérifier un dépôt en **ZIP** — portée par `ClientVigieChiro
.journalTraitement`, `lot/model/VerificationDepot` et la commande CLI `verifier-depot-vigiechiro`.
Même limite que `donnees` : le journal n'existe qu'après le passage du pipeline serveur.

### Verdicts des probes d'écriture (exécutées le 2026-07-11)

- **ZIP (pilier B, #984)** : la plateforme **accepte** un `.zip` (déclaration, `PUT` S3
  `application/zip`, finalisation) **et l'ingère** : la participation canonique `6a4961f5…` a été
  déposée **en zip via le site web** et ses 4806 `donnees` listent les WAV individuellement. Reste à
  valider que **notre chemin d'upload** (API directe) produit le même résultat : option expérimentale
  `deposer-vigiechiro --archives` (#1043), essai prévu sur une vraie nuit — verdict à lire avec
  `verifier-depot-vigiechiro` (journal de traitement, § ci-dessus).
- **PATCH `/sites/{id}`** : **HTTP 403** pour un observateur → le **push point→site est abandonné** ;
  le pull (`RapprochementSites`) reste la seule direction de synchronisation des sites — exécuté à la
  connexion, et rejouable **à la demande** depuis M-Sites (« Synchroniser depuis VigieChiro », #1045,
  passerelle `SynchronisationSites` activée par `OptionalBinder`).

### Méthodes autorisées et récupération (exploration du 2026-07-11, lecture seule)

Sondé via `OPTIONS` (en-tête `Allow`) avec un token d'observateur — **aucune suppression testée**.

| Ressource | `Allow` observé | Réalité |
|---|---|---|
| `/participations/{id}` | `GET, PATCH, DELETE…` | PATCH **fonctionne** (sync modale) ; **DELETE annoncé** — une participation est supprimable par son propriétaire (non testé, destructif ; `If-Match` requis, convention Eve) |
| `/sites/{id}` | `GET, PATCH, DELETE…` | PATCH réel → **403** : `Allow` reflète le **schéma Eve**, pas l'autorisation par rôle. Écriture/suppression réservées (MNHN/propriétaire) |
| `/moi/participations` | `GET` seul | lecture seule, **paginée** (`_meta.total` fiable) |
| `/fichiers` (collection) | `POST, GET…` | `GET` réel → **403** : on peut créer des fichiers, pas les relire |
| `/participations/{id}/donnees` | `GET, POST` | GET = résultats Tadarida (import) ; POST vraisemblablement réservé au pipeline serveur |

**Ce qui est récupérable depuis la plateforme** (restauration possible, cf. issue dédiée) :

- **toutes ses participations** (pagination `_meta` — attention : `mesSites()`/`mesParticipations()` ne
  lisent aujourd'hui que la **première page**) ;
- pour chacune : son **site complet embarqué** (`localites` = les points) et son `point` (code
  localité) → sites/points reconstruisibles **sans aucune donnée locale** ;
- ses **observations** (`donnees` : titre du fichier + observations Tadarida) → rejouables dans
  l'application (import « depuis VigieChiro » existant).

**Ce qui ne l'est pas** : les **WAV téléversés** — aucun lien de téléchargement dans les `donnees`,
collection `/fichiers` interdite en lecture. Les enregistrements audio d'origine n'existent que
localement : la sauvegarde du workspace reste indispensable.

!!! note "Faire évoluer le contrat"
    Quand notre compréhension change (nouveau champ, nouvel endpoint), on met à jour **le JSON Schema**
    (source de vérité) puis, si besoin, les assertions REST-assured et la collection Postman. Un échec de la
    suite `api-live` signale une **dérive** de l'API à instruire avant toute autre évolution.
