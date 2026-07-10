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
| POST | `/fichiers` puis `PUT` S3 signé puis POST `/fichiers/{id}` | téléverse un fichier (3 temps) |

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

!!! note "Faire évoluer le contrat"
    Quand notre compréhension change (nouveau champ, nouvel endpoint), on met à jour **le JSON Schema**
    (source de vérité) puis, si besoin, les assertions REST-assured et la collection Postman. Un échec de la
    suite `api-live` signale une **dérive** de l'API à instruire avant toute autre évolution.
