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
  Le fichier est **restreint au propriétaire** (POSIX `600`) sur les systèmes qui le supportent, pour
  qu'un autre compte de la machine ne puisse pas lire le token. Sous Windows, il reste protégé par les
  **ACL du profil utilisateur** : le dossier de travail vit sous le profil, dont les autres comptes non
  administrateurs n'ont pas la clé. Protection réelle, mais **de nature différente**, et c'est pourquoi
  elle est écrite plutôt que sous-entendue.

    L'écriture passe par `commun.model.EcritureProtegee` (#2735), et non plus par un `writeString`
    suivi d'un `chmod`. La différence n'est pas cosmétique : restreindre **après** avoir écrit laisse
    une fenêtre pendant laquelle le fichier existe avec les permissions de l'umask, souvent `644`. Elle
    se rouvrait à chaque **création** du fichier, donc à chaque reconnexion, puisque se déconnecter le
    supprime. Le secret n'atterrit désormais que dans un fichier **créé déjà restreint**, déplacé sur sa
    cible par un `ATOMIC_MOVE` - ce qui corrige au passage un `connexion.json` tronqué par une
    interruption, que le lecteur traduisait en « non connecté » sans rien dire.

    Ce garde est **structurel** (`SecretsEcritsProtegesTest`), parce que la fenêtre est un état
    intermédiaire : après coup, les deux façons d'écrire laissent le même fichier à `600`, et aucun test
    d'état final ne les distingue.

    Un durcissement supplémentaire (chiffrement au repos / coffre système) est arbitré dans #2736.
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

### Archives ZIP : le seul fichier arbitraire que l'application ouvre

Choisir un `.zip` comme source d'import fait ouvrir à l'application un fichier dont **rien ne garantit
la provenance**. Deux bornes l'encadrent, et elles répondent à deux menaces différentes.

**En chemins**, la garde « zip-slip » : une entrée dont le chemin s'évaderait du dossier d'extraction
(`../../.ssh/authorized_keys`) est refusée. Le chemin résolu est normalisé puis confronté à la racine
d'extraction.

**En ressources** (#2732, `BornesExtraction`), parce qu'une archive n'a pas besoin de s'évader pour
nuire : il lui suffit de remplir le disque, sur lequel vit aussi la base SQLite du workspace. Deux
gardes, et le second existe parce que le premier se ferait berner :

| Quand | Sur quoi | Ce qu'il attrape |
|---|---|---|
| Avant le premier octet écrit | l'inventaire **annoncé** (répertoire central, lu sans décompresser) | nombre d'entrées, taille d'une entrée, total, espace disque disponible avec marge |
| Pendant la copie | les octets **réellement** écrits | l'archive qui écrit plus qu'elle n'annonçait, c'est-à-dire la bombe ZIP, qui ment précisément sur ce que le premier garde lit |

Le second garde n'a besoin d'aucune constante : il confronte l'archive à **sa propre déclaration**,
celle sur laquelle l'espace disque vient d'être validé. Ensemble, les deux tiennent une garantie
simple : **on n'écrit jamais plus que ce qui a été déclaré, et on n'accepte jamais une déclaration qui
ne tient pas.**

!!! note "Pourquoi il n'y a pas de plafond de taux de compression"
    C'est le garde classique contre les bombes ZIP. Il a été écrit ici, puis **retiré** : il ne sépare
    pas les deux populations dans ce domaine. Les fixtures de recette produites par le générateur de
    cartes SD se décompressent **137 fois**, et un enregistrement réellement silencieux fait bien
    davantage : de l'audio silencieux et une bombe sont **les mêmes octets**.

    Il n'ajoutait rien à la garantie ci-dessus. Un taux énorme ne nuit que s'il aboutit à beaucoup
    d'octets écrits, ce que le total annoncé, le contrôle d'espace disque et le second garde bornent
    déjà. Le conserver, c'était payer des refus injustifiés pour une protection qu'on avait par
    ailleurs.

Les défauts sont larges (une nuit de terrain fait quelques milliers de fichiers et une dizaine de
gigaoctets) et chacun se surcharge par propriété système (`vigiechiro.import.zip.max-entrees`,
`…max-octets-par-entree`, `…max-octets-total`, `…marge-disque-octets`). Il n'y a **pas** de réglage
dans l'écran Réglages : un naturaliste n'a pas à choisir un plafond d'entrées. C'est le message de
refus qui nomme la limite atteinte et l'échappatoire.

⚠️ Ces bornes protègent l'**import par l'IHM**, seul chemin qui accepte aujourd'hui une archive : la
CLI ne prend qu'un dossier.

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
