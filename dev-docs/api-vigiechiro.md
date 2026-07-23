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

!!! danger "Ce qu'un test bouchonné ne peut pas voir (#1862)"
    Les quatre défauts d'écriture des suites de l'EPIC #1662 (#1828, #1839, #1844, #1845) partagent une
    propriété : **ils réussissent tous**. Publier une sentinelle « INCONNU » rend `200 OK` ; écrire le n° de
    série sous une clé que le formulaire web ne lit pas rend `200 OK` ; effacer par `PATCH` les champs
    distants non modélisés rend `200 OK`.

    Un test qui bouchonne l'API vérifie **ce que nous croyons envoyer**, jamais ce que la plateforme en
    **fait** : les mocks ont confirmé nos hypothèses fausses avec la même conviction que les justes. D'où la
    **sonde d'aller-retour** (`AllerRetourParticipationLiveTest`) : elle écrit, **relit**, et compare champ
    à champ. Elle traverse `CorrespondanceParticipation`, pour garder le mapping et pas seulement le
    transport.

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

    # + probes d'écriture (POST/PATCH/upload) :
    ./mvnw -Papi-live test -Dvigiechiro.token=XXXX -Dvigiechiro.write=true

    # + probes qui écrivent SUR une participation (corrections #1203, dépôt ZIP #984) :
    # elles exigent EN PLUS la participation de rebut, jamais une participation réelle,
    # car ni une correction posée ni un fichier déclaré ne se retirent :
    ./mvnw -Papi-live test -Dvigiechiro.token=XXXX -Dvigiechiro.write=true \
        -Dvigiechiro.participationEssai=<id-participation>

    # + sonde des messages (#1456) : TROISIÈME verrou, obligatoire. Cette écriture est
    # DÉFINITIVE ($push, aucune route ne retire ni ne modifie un message) :
    ./mvnw -Papi-live test -Dvigiechiro.token=XXXX -Dvigiechiro.write=true \
        -Dvigiechiro.participationEssai=<id-participation> -Dvigiechiro.message=true

    # Sonde d'ALLER-RETOUR (#1862) : écrit, relit, et compare champ à champ. Mêmes
    # verrous que les probes sur participation ; elle restaure la configuration de départ :
    ./mvnw -Papi-live test -Dvigiechiro.token=XXXX -Dvigiechiro.write=true \
        -Dvigiechiro.participationEssai=<id-participation> \
        -Dtest=AllerRetourParticipationLiveTest
    ```

    Sans `-Dvigiechiro.token`, la suite se **skippe** proprement (aucun échec accidentel).

!!! danger "Pourquoi les messages ont leur propre verrou"
    `-Dvigiechiro.write=true` **ne suffit pas** à tirer la sonde des messages, et c'est délibéré.

    Toutes les autres écritures se **rattrapent** : un `PATCH` de correction **remplace**, un
    `POST /participations` se re-modifie, un dépôt se réinitialise. `PUT …/messages`, **non** : le serveur
    **ajoute** par `$push`, et **aucune route** ne permet de supprimer ni de modifier un message. Ce qu'elle
    écrit **reste**, sur des données que lit un validateur du MNHN.

    Sans ce troisième drapeau, qui lance les probes d'écriture pour éprouver les **corrections** laisserait,
    sans le vouloir, une trace **définitive**. Le contrat live **hebdomadaire** (`api-live.yml`) est en
    lecture seule et ne passe **aucun** de ces trois drapeaux : il ne peut pas emporter la sonde avec lui.

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

### Le transport dit ce qu'il est advenu de chaque appel (#1284)

Toutes les méthodes de `ClientVigieChiro` rendent une **issue triée** (`ReponseApi<T>`, sealed) :

| Variante | Sens | Ce qu'en fait l'appelant |
|---|---|---|
| `Succes(valeur)` | 2xx, réponse exploitable | la valeur ; une **liste vide** est un vrai « rien » serveur |
| `NonConnecte` | aucun jeton, l'appel n'a pas eu lieu | silence légitime du hors-ligne |
| `Injoignable(cause)` | réseau, DNS, TLS, délai, corps illisible | « VigieChiro est injoignable (cause) », jamais confondu avec vide |
| `Refuse(statut, corps)` | le serveur a répondu non | remonter statut + corps (9 fois sur 10, bug de notre côté) |

Règles d'accompagnement : la **pagination Eve est tout-ou-rien** (une panne page 3 rend l'issue, pas
les pages 1-2) ; `max_results` est plafonné à 100 (au-delà Eve **rejette** : `422`, cause de #1277) et
la sonde live `refus_serveur_est_un_refuse_explicite` verrouille que ce refus reste un `Refuse` ; la
**garde anti-purge** des rapprocheurs et la garde **anti-relance** du dépôt (fail-safe : état illisible
= pas de lancement sans `--forcer`) s'appuient sur cette distinction. Patron détaillé :
[patterns · Issue d'appel triée](patterns.md#issue-dappel-triee-le-transport-ne-parle-plus-par-silence).

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
| POST | `/fichiers` (`lien_participation`) puis `PUT` S3 signé puis POST `/fichiers/{id}` | téléverse un fichier **rattaché à la participation** (3 temps, `PUT` **en flux**) |
| POST | `/participations/{id}/compute` (corps `{}`) | déclenche le **traitement serveur** (Tadarida) de la participation déposée |
| GET | `/grille_stoc/cercle?lng&lat&r` | mailles du carroyage national autour d'un point |
| GET | `/participations/{id}/pieces_jointes` | pièces jointes d'une participation (audio déposé) |
| GET | `/fichiers/{id}/acces` | URL d'accès (S3 signé) d'un fichier déposé |
| PATCH | `/donnees/{id}/observations/{indice}` | pousse une **correction d'observation** (taxon + certitude) |
| PUT | `/donnees/{id}/observations/{indice}/messages` | ajoute un **message** au fil de discussion d'une observation |

### Objet `participation` (schéma canonique)

```json
{
  "point": "Z41",
  "date_debut": "2026-07-03T19:00:00+00:00",
  "date_fin": "2026-07-04T04:00:00+00:00",
  "meteo": {
    "vent": "FAIBLE", "couverture": "0-25",
    "temperature_debut": 18, "temperature_fin": 11
  },
  "configuration": {
    "detecteur_enregistreur_type": "PassiveRecorder",
    "detecteur_enregistreur_numero_serie": "1997632",
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
    - **`meteo`** porte `vent` (`NUL|FAIBLE|MOYEN|FORT`), `couverture`
      (`0-25|25-50|50-75|75-100`) **et les températures** `temperature_debut` / `temperature_fin`,
      typées **`integer`** : un relevé décimal est **refusé**, il faut arrondir avant l'envoi (#1844).
      *(Cette page a longtemps affirmé l'inverse — « pas de températures ». L'app ne les transportait
      pas, ce qui a fait conclure à tort que le schéma ne les portait pas.)*
    - **`configuration` est un dictionnaire libre, donc un piège** : le `PATCH` le **remplace en
      entier**, et aucune clé n'est validée. D'où deux règles ([ADR 0020](decisions/0020-ecrire-sur-la-plateforme-ne-rien-inventer-ni-effacer.md)) :
      partir de la configuration **distante** avant d'y superposer la nôtre (sinon on efface
      `micro0_numero_serie`, `micro1_*`, `canal_*`), et écrire le n° de série sous la clé **que le
      formulaire web lie** — `detecteur_enregistreur_numero_serie`. L'app a longtemps poussé
      `..._numserie` : accepté par le serveur, **invisible** sur la fiche web. La lecture accepte
      encore les deux ; l'écriture retire l'ancienne.
    - **`_etag`** est requis en en-tête `If-Match` pour tout `PATCH`/`PUT`/`DELETE` (concurrence Eve).
    - **`traitement.etat`** : les **cinq** états de l'analyse serveur (`PLANIFIE`, `EN_COURS`, `FINI`,
      `ERREUR`, `RETRY`), accompagnés de `date_planification` / `date_debut` / `date_fin`, `message`
      (trace d'erreur) et `retry`. Le bloc est **remplacé** à chaque étape, jamais complété. Cf. § « Le
      traitement serveur, après le dépôt » (EPIC #1259).

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

## Écriture des corrections (spike #1203, prérequis de #723)

Contrat établi le 2026-07-13 par **lecture statique du backend** (`Scille/vigiechiro-api`,
`resources/donnees.py` + `xin/resource.py`, master du 2026-06-09), puis **confirmé en réel le jour
même** par les sondes `#1203` de `ContratApiVigieChiroLiveTest` : lecture (3 sondes) et écriture
(2 probes opt-in, sur la participation banc d'essai `6a50f790aede4b981b7942be` : PATCH positionnel
`200` + relecture conforme, verdicts négatifs `422` et `403` observés).
L'hypothèse initiale de l'issue (« PATCH de la donnée avec le tableau `observations` réémis, le plus
probable ») était **fausse** : cette voie est réservée à l'admin. La route positionnelle existe.

**Implémenté (#723)** : `ClientVigieChiro.corrigerObservation` (transport, levier `?no_bilan=true`) +
`PublicationCorrections` (tri poussables / à compléter / sans ancrage / hors référentiel, rafale avec
bilan serveur régénéré par le seul dernier envoi), exposés par l'action ☰ « Publier les corrections
vers VigieChiro » de Sons & validation et la commande `publier-corrections-vigiechiro`.

**La route** : `PATCH /donnees/{donnee_id}/observations/{index}` : l'**indice dans le tableau
`observations` est l'identifiant** de l'observation (`404` si hors bornes).

```http
PATCH /donnees/6a4fcaa2842983a29ba25363/observations/0
{ "observateur_taxon": "5526cd5a…", "observateur_probabilite": "SUR" }
```

Règles imposées par le handler (`donnees.py`, `edit_observation`) :

- **rôle `Observateur` + propriétaire de la donnée uniquement** pour `observateur_*` (`403` sinon) ;
  `validateur_taxon` / `validateur_probabilite` sont réservés Administrateur / Validateur — l'application
  ne peut donc **que les lire**, jamais les écrire (arbitrage #724, livré par #1417) ;
- **`observateur_probabilite` est une énumération `SUR | PROBABLE | POSSIBLE`**, pas un flottant, et
  elle est **obligatoire dès que `observateur_taxon` est envoyé** (`422` sinon). **Arbitrage tranché
  (2026-07-13)** : deux notions distinctes, **aucune conversion**. Le `Double` local
  (`Observation.probObservateur`) est la confiance **Tadarida** (recopiée à la validation un-clic,
  héritage du format `_Vu`) ; l'énumération est la **certitude déclarée manuellement** par
  l'observateur au moment de sa revue, **vide par défaut**, en miroir du site web (listes « Taxon
  observateur » + « Confiance observateur » + bouton OK, rien de prérempli). #1139 ajoute le champ
  local correspondant ; seules les observations avec taxon **et** certitude saisis sont poussables ;
- **`observateur_taxon` est un objectid** (`relation('taxons')`, cast `ObjectId(...)`). Le mapping
  code ↔ objectid existe : `vigiechiro_link` / `ENTITE_TAXON` (`RapprochementTaxons`). Un taxon local
  **hors référentiel** (sans lien) n'est pas poussable : cas normal à afficher, pas une erreur ;
- tout autre champ dans le corps → `422 unknown field` ;
- **pas d'`If-Match`** : le handler ne lit pas cet en-tête (la concurrence est gérée en interne par
  relecture-`$set`). Au passage, le handler `PATCH /participations/{id}` ne le lit pas non plus :
  notre client l'envoie par convention Eve, sans effet réel ;
- **pas d'annulation** : la route ne fait que du `$set`. Une correction posée se **remplace** mais ne
  se **retire** pas : d'où la règle « participation banc d'essai explicite » des probes.

!!! warning "Durabilité : un re-compute efface les corrections"
    Une **relance du traitement supprime toutes les `donnees`** de la participation avant de recalculer
    (`task_participation.py:726-731`, consigné par #1260). Les corrections poussées **ne survivent
    donc pas** à un re-compute : la conception de #723 doit en tenir compte (re-pousser après
    recalcul, ou verrouiller la relance quand des corrections existent).

Effets de bord et leviers :

- chaque `PATCH` déclenche la régénération du **bilan** de la participation
  (`participation_generate_bilan.delay_singleton`), sauf paramètre `?no_bilan=<vrai>` : levier de
  traitement par lot pour #723 (n'omettre le bilan que sur les rafales, jamais sur le dernier envoi) ;
- l'**ancrage local** (#1139) est le couple (`donnee._id`, indice) : le `_id` de la donnée est
  désormais exposé au parsing (`DonneeVigieChiro.id`) ; côté lecture, `observateur_probabilite`
  revenant en **chaîne**, le parseur actuel (`getAsDouble`) la ramène silencieusement à `null` : à
  reprendre dans #1139 ;
- **asymétrie écriture/lecture vérifiée en réel** : on écrit `observateur_taxon` en objectid, on le
  relit **embarqué complet** (objet taxon avec `_id`, `libelle_court`, `libelle_long`, `parents`),
  exactement comme `tadarida_taxon` : le parseur actuel (`codeTaxon`) lit donc déjà son
  `libelle_court` ;
- route des fichiers rattachés **confirmée** (#1565, probe #1568) : `GET
  /participations/{id}/pieces_jointes?<filtre>` (`ta` / `tc` / `wav` / `photos` / `processing_extra`)
  liste les fichiers d'une participation avec `{_id, titre, disponible, s3_id}` ;
  `?processing_extra=true` expose le **CSV d'observations** (§ « Pièces jointes » ci-dessous), `?wav=true`
  croise le repli audio #1244.

### Les trois avis, et le fil : tout arrive déjà dans `GET …/donnees` (#1417)

Le spike de #724 a établi que **rien de nouveau n'était à appeler**. Le schéma de la ressource `donnees`
porte, sur **chaque observation** :

```python
'observateur_taxon':       relation('taxons'),
'observateur_probabilite': choice(['SUR', 'PROBABLE', 'POSSIBLE']),
'validateur_taxon':        relation('taxons'),
'validateur_probabilite':  choice(['SUR', 'PROBABLE', 'POSSIBLE']),
'messages': [ {'message': str, 'auteur': relation('utilisateurs'), 'date': datetime} ],
```

Ces champs arrivaient donc à **chaque import**, dans la même charge utile — et le parseur les jetait.
L'application présentait la correction de l'observateur comme le dernier mot, alors qu'un expert avait pu
la réviser sans qu'on le voie jamais.

Points de contrat :

- la **certitude** partage la même énumération pour l'observateur et le validateur : un seul type local
  (`Certitude`) les porte tous les deux, et son nom le dit depuis la clôture de #1154 ;
- l'**auteur** d'un message est un **objectid** d'`utilisateurs`, jamais un nom. Le résoudre demanderait
  un appel par auteur : on le compare à l'identifiant de notre propre profil (déjà stocké localement à la
  connexion) pour dire « vous » ou « le validateur » ;
- l'auteur revient tantôt **brut** (`"auteur": "5f3a…"`), tantôt **résolu** (`{"_id": "5f3a…", …}`) selon
  les projections : le parseur accepte les deux.

### `PUT /donnees/{id}/observations/{index}/messages` — poster un message (#1418)

```http
PUT /donnees/6a4fcaa2842983a29ba25363/observations/0/messages
{ "message": "Médiane basse pour un Eptser, non ?" }
```

- rôle **`Observateur`** : `_check_access_rights` laisse passer le **propriétaire** de la donnée — notre
  jeton suffit (contrairement à l'avis de validateur, refusé en `403`) ;
- **ancrage positionnel**, le même que la correction : `donnee._id` + indice **brut** ;
- corps à un seul champ ; tout ce qui n'est pas une chaîne → `422` ;
- ni `If-Match`, ni `_etag` : **aucun contrôle de concurrence**. Deux messages simultanés s'**empilent**,
  ils ne s'écrasent pas — c'est le seul point rassurant de cette absence.

!!! danger "Un message posté ne se retire pas"
    Le serveur ajoute par **`$push`**, et **aucune route ne permet de supprimer ni de modifier un
    message**. C'est une écriture **définitive**, sur des données que lit un validateur du MNHN. D'où,
    partout : une confirmation qui **dit** l'irréversibilité et **cite** le texte (on ne consent qu'à ce
    qu'on a compris), `--confirmer` obligatoire en CLI, et une **fonctionnalité désactivable**
    (`discuter-validateur`) — couper l'écriture laisse la lecture du fil intacte.

    Corollaire pour les probes : toute sonde **live** sur cette route est **irréversible**. C'est pourquoi
    elle exige **trois** verrous et non deux (`-Dvigiechiro.write=true` +
    `-Dvigiechiro.participationEssai=<id>` + `-Dvigiechiro.message=true`), vise la participation de
    **rebut**, et n'a **pas** sa place dans `api-live.yml` (contrat hebdomadaire, lecture seule, qui ne
    passe aucun de ces drapeaux).

**Ce contrat est vérifié en vrai** (#1456, tir du 2026-07-14 sur la participation de rebut) :
`probe_put_message_observation` et `probe_message_corps_invalide` dans `ContratApiVigieChiroLiveTest`. Il
n'est donc plus **déduit** du code du backend, il est **constaté** :

| Ce qui est constaté | Verdict |
|---|---|
| `PUT` avec un jeton d'`Observateur` **propriétaire** de la donnée | `200` |
| Le message se **relit** dans le fil juste après | oui - le `$push` a bien eu lieu |
| Le serveur **horodate et signe** lui-même (`auteur`, `date`) | oui - le client n'envoie **que** le texte, et le modèle a raison de les attendre de lui |
| Un corps **non-chaîne** (objet au lieu de texte) | `422` - on ne peut pas glisser une structure dans un fil |

Le message écrit par ce tir **est toujours là** : la route ne permet pas de le retirer. C'est la
démonstration, par l'exemple, de ce que dit l'encadré ci-dessus.

## Cycle de vie d'une participation (EPIC #941)

Le cycle est **naturel** depuis la refonte : la participation est **créée à l'import** (best-effort,
si connecté et site rattaché), **synchronisée** depuis la modale « Modifier le passage » (push
météo/micro à la validation, pull « Récupérer depuis VigieChiro »), puis **réutilisée au dépôt**
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

### Téléversement d'un fichier et déclenchement du traitement (#984)

Le dépôt d'une unité (WAV ou archive ZIP) est un aller-retour en **trois temps**, porté par
`ClientVigieChiro.creerFichier` / `televerserVersS3` / `finaliserFichier` :

1. `POST /fichiers` avec le corps `{"titre": …, "multipart": false, "lien_participation": "<id>"}`
   → renvoie l'`_id` du fichier et une **URL S3 pré-signée** de dépôt ;
2. `PUT <url signée>` du contenu **en flux** (`BodyPublisher` sur le fichier, sans le charger en
   mémoire), en-tête `Content-Type` = le **type MIME renvoyé** par l'étape 1 (`audio/x-wav` ou
   `application/zip`), **sans** en-tête `Authorization` (la signature de l'URL fait foi) ;
3. `POST /fichiers/{id}` (corps `{}`) pour **finaliser** l'enregistrement côté serveur.

!!! danger "`lien_participation` est obligatoire (le bug qui n'a jamais marché avant #984)"
    Sans `lien_participation` à l'étape 1, le fichier est **créé et téléversé sur S3 mais orphelin** :
    il n'est **rattaché à aucune participation**, donc `compute` traite une participation vide et le
    journal serveur affiche `Extracting 0 zipped files`. Le symptôme est trompeur — l'IHM et la CLI
    voient trois requêtes réussies (201/200/200) et annoncent « Déposé », mais **rien n'apparaît sur la
    plateforme**. Le rattachement est résolu par `DepotVigieChiro.participationLiee(idPassage)` (lien
    `ENTITE_PASSAGE`) et propagé jusqu'à `creerFichier(titre, participationId)`.

**Déclencher le traitement** : une fois **toutes** les unités déposées, `POST
/participations/{id}/compute` (corps `{}`) lance le pipeline serveur (extraction des ZIP puis
TadaridaD). C'est l'équivalent du bouton « Lancer la participation » (M-Lot) et de la commande
`lancer-traitement-vigiechiro`. Le serveur **refuse** (`400 «Already»`) si un traitement est déjà
`EN_COURS`/`PLANIFIE` : ce n'est pas une erreur, juste « déjà lancé ». `compute` **n'est pas
automatique après le dépôt** : les fichiers sont sur S3, mais rien n'est traité tant qu'il n'est pas
appelé (par l'application ou depuis la page web de la participation).

### Le traitement serveur, après le dépôt (EPIC #1259)

**`DEPOSE` n'est pas la fin.** Une fois le compute lancé, la plateforme analyse la nuit ; les
observations ne sont récupérables qu'à `FINI`. Avant, `GET /donnees` répond **« 200, liste vide »** —
pas une erreur, un **état**.

**Les cinq états** (`participation.traitement.etat`, `resources/participations.py:73`) :

| État | Sens | Ce que l'application en fait |
|---|---|---|
| `PLANIFIE` | en file d'attente (`date_planification`) | patienter |
| `EN_COURS` | un worker calcule (`date_debut`) | patienter (dizaines de minutes) |
| `FINI` | terminé (`date_fin`) | **le seul état où les observations existent** |
| `ERREUR` | échec après épuisement des essais (`message` = trace) | lire le motif |
| `RETRY` | échec **rattrapé** : le serveur a relancé (`retry`) | patienter |

⚠️ **Le serveur REMPLACE le bloc `traitement` à chaque étape**, il ne le complète pas : dès que le
calcul démarre, `date_planification` **disparaît**. N'attendez jamais les trois dates ensemble
(constaté en réel sur la participation canonique : `FINI` sans `date_planification`).

**Un état SERVEUR, distinct du workflow local.** `EtatTraitement` (`commun.api`) n'est **pas** une
extension de `StatutWorkflow` : il ne nous appartient pas, il n'est **pas monotone** (une relance
ramène `FINI` à `PLANIFIE`) et nous ne faisons que l'observer. `DEPOSE` reste donc l'état terminal du
workflow local — même partition que `StatutPlateforme` côté sites.

**Un refus n'est pas un échec.** `POST /compute` répond `400 «Already»` quand un traitement est déjà
`PLANIFIE`/`EN_COURS` **depuis moins de 24 h** (`participations.py:231-237`). Plutôt que de décrypter
son message, `TraitementVigieChiro.lancer` **relit l'état** : c'est lui qui fait foi. D'où
`ResultatLancement` (accepté / déjà lancé / relance bloquée / refusé / injoignable) au lieu d'un
booléen aveugle.

!!! danger "Relancer un traitement DÉTRUIT les observations"
    Le serveur **supprime toutes les `donnees` avant de recalculer** (`task_participation.py:726-731`),
    puis relit les WAV via `get_file_from_s3` — qui **renvoie `None` sans lever** quand le fichier n'a
    pas de `s3_id` (`fichiers.py:118-121`). Or sur un dépôt en **archives ZIP** (notre mode par défaut
    depuis #984), les WAV extraits n'ont **jamais** de `s3_id` (#1244) et les ZIP ont été supprimés de
    S3. Le recalcul rend donc une participation **vide, définitivement**.

    Vérifié en réel : un `compute` sur une participation `FINI` est **accepté (HTTP 200)**. **Seule
    notre garde locale protège les données** — `DepotVigieChiro.lancerTraitement(id, forcer)` refuse de
    son propre chef, le bouton de M-Lot se verrouille, et le forçage n'existe qu'en ligne de commande
    (`lancer-traitement-vigiechiro --forcer`), là où il mérite d'être réfléchi.

**Aucun sondage.** L'application ne surveille jamais la plateforme : elle relit l'état à l'ouverture de
M-Lot (depuis le **cache** `participation_traitement`, sans réseau), sur demande (« Actualiser ») ou
après un lancement. Un calcul dure des dizaines de minutes et le site officiel n'en fait pas davantage.
Le suivi scriptable, lui, passe par la CLI (`etat-traitement-vigiechiro`, codes `0` fini / `3` patiente
/ `2` échec / `4` jamais lancé), faite pour une boucle `until … ; [ $? -ne 3 ]`.

**Le point de relevé unique** est `commun.model.SuiviTraitement` : il interroge le serveur **et**
alimente le cache. M-Lot, la modale de M-Passage et la CLI le partagent — il vit dans `commun` parce
qu'un `passage` qui dépendrait de `lot` fermerait un cycle qu'ArchUnit refuse.

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

### Pièces jointes et CSV d'observations (#1565, probe #1568)

Une participation traitée expose ses fichiers rattachés via `GET /participations/{id}/pieces_jointes`,
filtrable par type : `?ta=true`, `?tc=true`, `?wav=true`, `?photos=true`, `?processing_extra=true`
(backend `Scille/vigiechiro-api`, `participations.py:342-350`). Chaque entrée porte `{_id, titre,
disponible, s3_id}`. C'est **la** voie pour obtenir le `_id` d'un fichier : la collection `/fichiers`
n'est **pas listable** (`403`), seul le document qui la référence donne l'`_id`.

**Le CSV d'observations** (`?processing_extra=true`, un unique `participation-<id>-observations.csv`)
est **`disponible: true` sur S3** : il est généré avec `force_upload=True`
(`task_observations_csv.py:52`), donc toujours monté (comme les logs, contrairement aux WAV extraits
d'un ZIP qui, eux, restent `disponible: false`, #1244). On le télécharge en **trois requêtes**,
exactement comme le journal :

1. `GET /participations/{id}/pieces_jointes?processing_extra=true` → `_items[0]._id` ;
2. `GET /fichiers/{_id}/acces` → `{"s3_signed_url": …}` ;
3. `GET` de l'URL signée, **sans** `Authorization` (la signature fait foi).

Le contenu est du **Tadarida BRUT** (`;` séparateur, champs quotés :
`"nom du fichier";"temps_debut";"temps_fin";"frequence_mediane";"tadarida_taxon";…`), **sans `_id`
d'observation**. Un seul téléchargement (≈1,4 Mo, ≈12 700 lignes pour une grosse nuit) remplace les ~48
pages de `GET …/donnees` (plafonnées à 100/page) : c'est le socle de la **reconstruction instantanée**
(#1565). Contrepartie de l'absence d'`_id` : l'**ancrage plateforme** (`idDonneeVigieChiro`) ne peut pas
venir du CSV ; il est acquis séparément, à la **réactivation**, par une passe `donnees` complète (le
filtre `donnees?where={titre}` étant **silencieusement ignoré**, même faux-négatif que `max_results>100`
#1277 ; la passe reste parallélisable, `_meta.total` étant connu dès la page 1).

Route serveur voisine, non utilisée : `POST /participations/{id}/csv` (`participations.py:182`)
régénère le CSV côté serveur ; inutile ici, le pipeline le produit déjà après traitement.

### Verdicts des probes d'écriture (exécutées le 2026-07-11)

- **ZIP (pilier B, #984)** : **verdict confirmé en réel.** La plateforme accepte un `.zip`
  (déclaration, `PUT` S3 `application/zip`, finalisation) **et l'ingère** — d'abord vérifié sur la
  participation canonique `6a4961f5…` (déposée en zip via le site web, 4806 `donnees`), puis reproduit
  par **notre chemin d'upload** (API directe) sur une vraie nuit (`Car130711-2026-Pass2-Z41`, 04/07) :
  les 19 archives ZIP téléversées, `compute` lancé, WAV extraits et listés côté serveur. Le dépôt
  **en ZIP est désormais le mode par défaut** (repli WAV seulement si l'espace disque est insuffisant),
  déposé **en parallèle** (5 uploads simultanés, cf. `DepotVigieChiro`). La seule pièce manquante était
  `lien_participation` (§ « Téléversement d'un fichier », sans quoi les uploads étaient orphelins).
  Depuis #1287, `probe_zip_vs_wav` **garde** ce verdict au lieu de le contredire : elle **affirme** que la
  plateforme accepte un ZIP, et elle a été **tirée** (2026-07-14, participation de rebut) - déclaration,
  `PUT` S3 `application/zip`, finalisation : **verte**. Un rouge sur cette probe veut donc dire que **le
  mode de dépôt par défaut est cassé**, et non, comme son libellé le laissait croire, qu'il faudrait
  revenir au WAV.
- **PATCH `/sites/{id}`** : **HTTP 403** pour un observateur → le **push point→site est abandonné** ;
  le pull (`RapprochementSites`) reste la seule direction de synchronisation des sites — exécuté à la
  connexion, et rejouable **à la demande** depuis M-Sites (« Récupérer depuis VigieChiro », #1045,
  passerelle `SynchronisationSites` activée par `OptionalBinder`).
- **Aller-retour d'écriture (#1862)** : **quatre verdicts confirmés en réel** (exécutée le 2026-07-18 sur
  la participation de rebut `6a50f790…`, quatre probes vertes).

    | Fait de plateforme | Observé | Ce qui en dépend |
    |---|---|---|
    | Le `PATCH` **remplace** la `configuration` entière | une clé témoin posée puis non renvoyée **disparaît** | oblige `CorrespondanceParticipation` à **partir de la configuration distante** (#1844) |
    | La clé canonique ressort **verbatim**, l'ancienne disparaît | `detecteur_enregistreur_numero_serie` conservé tel quel | une participation déposée avant #1844 se **répare au premier envoi** |
    | Une sentinelle ne franchit pas la frontière | **aucune** clé de série après un envoi « INCONNU » | #1828, ne rien inventer |
    | Les heures ne dérivent pas d'un cycle à l'autre | `21:00 → 06:00` rendus à l'identique sur deux cycles | #1860, le cliquet est bien refermé |

    **Corroboration au passage** : la configuration de la participation de rebut portait
    `detecteur_enregistreur_numserie` (clé **historique**), preuve de terrain que des participations
    déposées avant #1844 existent bel et bien avec l'ancienne clé.

### Méthodes autorisées et récupération (exploration du 2026-07-11, lecture seule)

Sondé via `OPTIONS` (en-tête `Allow`) avec un token d'observateur — **aucune suppression testée**.

| Ressource | `Allow` observé | Réalité |
|---|---|---|
| `/participations/{id}` | `GET, PATCH, DELETE…` | PATCH **fonctionne** (sync modale) ; **DELETE annoncé** — une participation est supprimable par son propriétaire (non testé, destructif ; `If-Match` requis, convention Eve) |
| `/sites/{id}` | `GET, PATCH, DELETE…` | PATCH réel → **403** : `Allow` reflète le **schéma Eve**, pas l'autorisation par rôle. Écriture/suppression réservées (MNHN/propriétaire) |
| `/moi/participations` | `GET` seul | lecture seule, **paginée** (`_meta.total` fiable) |
| `/fichiers` (collection) | `POST, GET…` | `GET` réel → **403** : on peut créer des fichiers, pas les relire |
| `/participations/{id}/donnees` | `GET, POST` | GET = résultats Tadarida (import) ; POST **ouvert au propriétaire** (vérifié #1203 : création d'une donnée avec observations, `201`) |
| `/donnees/{id}` | `GET, PATCH` | GET direct OK (vérifié #1203) ; PATCH du tableau `observations` = **403 pour l'observateur** (réservé admin) |
| `/donnees/{id}/observations/{index}` | `PATCH` (vérifié `200` en réel) | **la** route des corrections (#1203) : l'indice du tableau est l'identifiant ; cf. § Écriture des corrections |

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
