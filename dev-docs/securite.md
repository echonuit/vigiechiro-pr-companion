# Sécurité & données sensibles

VigieChiro traite des **données naturalistes**. La règle la plus importante n'est pas technique mais
**écologique** : ne jamais exposer la localisation d'espèces protégées. Cette page est la version
développeur de
[**SECURITY.md**](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/SECURITY.md)
(source canonique).

!!! danger "Données sensibles : chiroptères"
    La localisation précise des gîtes et points d'écoute d'**espèces protégées** ne doit **jamais**
    être diffusée publiquement. Concrètement, dans ce dépôt :

    - **aucune donnée terrain réelle** (enregistrements, journaux de capteur, relevés) ;
    - **aucune coordonnée GPS** de site sensible ;
    - **aucune donnée personnelle** d'observateur.

    En cas de doute sur la diffusion d'une donnée ou d'une coordonnée : **on s'abstient et on demande**.

## Le jeu de données d'exemple

Pour tester sans matériel, utilisez le **dataset d'exemple**, **dé-PII** (les fichiers Kaleidoscope
porteurs de métadonnées identifiantes ont été retirés) et publié sur Zenodo
([DOI 10.5281/zenodo.20492247](https://doi.org/10.5281/zenodo.20492247)). La base locale
**`vigiechiro.db`** est un artefact de travail **ignoré par git** : elle ne doit contenir que des
données d'exemple ou de test.

## Secrets et identifiants

- **Aucun secret n'est versionné** : pas de clé d'API, de jeton ni de mot de passe dans le dépôt, y
  compris dans un test, une capture ou une base SQLite.
- **Token de session VigieChiro** : depuis l'intégration API (#716), l'app enregistre un **token de
  session** (fourni par la plateforme, péremption ~14 jours) dans **`connexion.json`** à la racine du
  workspace. Ce fichier est un **artefact local**, **ignoré par git** (`.gitignore`), jamais versionné.
  À l'écriture, le fichier est **restreint au propriétaire** (POSIX `600`) sur les systèmes qui le
  supportent, pour qu'un autre compte de la machine ne puisse pas lire le token (sans objet sous
  Windows, où le fichier reste protégé par les ACL du profil). Un durcissement supplémentaire
  (chiffrement au repos / keychain OS) reste suivi dans #1140.
- **Un journal doit pouvoir être joint à un signalement** (#1845). L'utilisateur est invité à envoyer
  ses journaux avec un rapport d'anomalie : ils ne doivent donc porter **ni le jeton, ni les en-têtes,
  ni le corps envoyé**. Ils ne portent pas non plus l'**URL complète** d'un dépôt S3 : une URL
  pré-signée **porte sa signature dans sa requête**, et journaliser le **chemin seul** règle la fuite
  par construction plutôt que par vigilance (cf. [Observabilité](observabilite.md)).
- Les workflows GitHub Actions tournent au **moindre privilège** (`maven.yml` : `permissions: contents:
  read`). N'élargir que là où c'est nécessaire (`contents: write` pour `capture-vues.yml`, qui pousse
  les aperçus).

## Surface applicative

L'application est **locale** : pas de serveur entrant, pas de port ouvert. Elle appelle en revanche
l'**API VigieChiro** (lecture des sites/participations/résultats et dépôt d'une nuit) authentifiée par
le **token de session** stocké localement (cf. « Secrets et identifiants »). Aucun autre identifiant
n'est conservé.

- **Pas d'injection SQL** : les DAO utilisent des `PreparedStatement` (requêtes paramétrées), jamais de
  concaténation de chaînes dans le SQL (cf. [Persistance](persistance.md)).
- **Accès natifs cadrés** : sous Java 25 (accès natif strict), seuls les modules concernés sont
  autorisés (`--enable-native-access=javafx.graphics`, `--enable-native-access=org.xerial.sqlitejdbc`).

### Hôtes sortants

Tous les appels réseau de l'application, exhaustivement - chacun **best-effort** (une panne dégrade
un confort, jamais un geste métier), sauf l'API VigieChiro qui porte le cœur des échanges :

| Hôte | Usage | Authentifié |
|---|---|---|
| API VigieChiro (Eve) | sites, participations, résultats, dépôt d'une nuit | token de session |
| URLs S3 pré-signées | téléchargement / téléversement des fichiers son | signature dans l'URL |
| `archive-api.open-meteo.com` | pré-remplissage météo d'un passage | non |
| `api.gbif.org` | clé d'usage des fiches d'espèces | non |
| `geo.api.gouv.fr` | commune d'un point d'écoute depuis son GPS (#2791, ADR 2791) | non |
| `api.github.com` | détection d'une nouvelle version | non |
| tuiles OpenStreetMap (Gluon Maps) | fond de carte des sites | non |

Hors ligne, l'application reste pleinement utilisable : ces enrichissements se taisent, et la
commune des points se comble plus tard (`rattraper-communes`, ou les déclencheurs de l'IHM).

## Chaîne d'approvisionnement

- **Dependabot** propose mensuellement les mises à jour `maven` et `github-actions` ; les bumps sont
  **revus avant merge** (cf. [CI/CD et release](ci-cd-release.md#dependances)).
- **JavaFX (`org.openjfx:*`) est exclu** de l'automatisation (impact fort : rendu, Headless Platform) :
  mises à jour décidées à la main.
- Le composant **`audio-view`** (Maven Central) fait partie de la chaîne au même titre que toute
  dépendance.

## Signaler une vulnérabilité

Ne **pas** ouvrir d'issue publique. Écrire en privé à
**[sebastien.nedjar@univ-amu.fr](mailto:sebastien.nedjar@univ-amu.fr)** (problème, impact, étapes de
reproduction). Le périmètre supporté est la **branche par défaut** (dernière version).

!!! warning "Ne désactivez pas les garde-fous"
    Ne neutralisez pas PMD, ArchUnit ou la garde d'intégrité pour « faire passer » un build. Et
    vérifiez votre identité git avant de committer (`git config user.email` institutionnel).
