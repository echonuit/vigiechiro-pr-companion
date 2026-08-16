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
  administrateurs n'ont pas la clé. Protection réelle, mais **de nature différente**.

    ⚠️ **Cette phrase a été affirmée pendant des mois sans que personne la vérifie**, et l'audit de
    dette du 2026-07-28 le relevait : le code « se repose sur les ACL du profil **sans les contrôler
    explicitement** ». Mesuré depuis (#3778), par sonde sous Windows Server 2025 : le fichier porte
    exactement **trois** entrées `ALLOW` - son propriétaire, `NT AUTHORITY\SYSTEM` et
    `BUILTIN\Administrators`. C'est l'équivalent exact de `600` sous POSIX, où **root** lit aussi ce
    qu'il veut. **La doc disait vrai** - et c'est précisément ce qu'on ignorait.

    La propriété est désormais **lue**, et non plus affirmée :
    `commun.model.ProtectionFichier.restreinteAuProprietaire` l'exprime sur les deux systèmes -
    permissions POSIX ici, ACL là-bas -, et `EcritureAtomiqueTest` l'éprouve. ⚠️ Ni POSIX ni ACL fait
    **lever** plutôt que rendre `true` : annoncer une protection depuis une ignorance serait le faux
    vert que tout ce dispositif existe pour éviter.

    ⚠️ La protection reste **héritée** du dossier de profil, pas posée par le produit. Elle est réelle
    et elle peut changer sans que rien ne le dise - d'où la lecture, et le test qui s'en sert chaque
    mardi (#3526).

    L'écriture passe par `commun.model.EcritureAtomique` (#2735), et non plus par un `writeString`
    suivi d'un `chmod`. La différence n'est pas cosmétique : restreindre **après** avoir écrit laisse
    une fenêtre pendant laquelle le fichier existe avec les permissions de l'umask, souvent `644`. Elle
    se rouvrait à chaque **création** du fichier, donc à chaque reconnexion, puisque se déconnecter le
    supprime. Le secret n'atterrit désormais que dans un fichier **créé déjà restreint**, déplacé sur sa
    cible par un `ATOMIC_MOVE` - ce qui corrige au passage un `connexion.json` tronqué par une
    interruption, que le lecteur traduisait en « non connecté » sans rien dire.

    ⚠️ **Sous Windows, ce déplacement échoue dès qu'un lecteur tient la cible** - mesuré par sonde sur
    Windows Server 2025 (#3777) : `Files.newInputStream`, `FileChannel`, `RandomAccessFile` et
    `FileChannel.lock()` provoquent **tous** une `AccessDeniedException`. Un simple lecteur suffit, il
    n'y a pas besoin d'un verrou. Or c'est le chemin du fichier d'amorçage, et les tenues concurrentes
    y sont **ordinaires** : un antivirus qui analyse au moment du remplacement, un outil de sauvegarde,
    une seconde instance qui lit.

    L'écriture **insiste** donc : cinq tentatives espacées de 150 ms, ~600 ms au total - assez pour
    traverser une analyse antivirus, trop court pour qu'un utilisateur croie l'application figée.
    Au-delà, le refus **nomme** la cause (« tenu ouvert par un autre programme ») plutôt que de parler
    de droits, ce qui enverrait chercher un problème qui n'existe pas sur un fichier dont on est
    propriétaire.

    Ce garde est **structurel** (`SecretsEcritsProtegesTest`), parce que la fenêtre est un état
    intermédiaire : après coup, les deux façons d'écrire laissent le même fichier à `600`, et aucun test
    d'état final ne les distingue.

    **Il n'y aura pas de coffre système** : l'[ADR 2736](decisions/2736-le-clair-est-assume-et-annonce.md)
    l'a tranché. Ranger le jeton au coffre pendant que les **localisations d'espèces protégées** restent
    en clair dans la base du même dossier déplacerait la serrure sans déplacer la porte.
- **Un journal doit pouvoir être joint à un signalement** (#1845). L'utilisateur est invité à envoyer
  ses journaux avec un rapport d'anomalie : ils ne doivent donc porter **ni le jeton, ni les en-têtes,
  ni le corps envoyé**. Ils ne portent pas non plus l'**URL complète** d'un dépôt S3 : une URL
  pré-signée **porte sa signature dans sa requête**, et journaliser le **chemin seul** règle la fuite
  par construction plutôt que par vigilance (cf. [Observabilité](observabilite.md)).
- Les workflows GitHub Actions tournent au **moindre privilège** (`maven.yml` : `permissions: contents:
  read`). N'élargir que là où c'est nécessaire (`contents: write` pour `capture-vues.yml`, qui pousse
  les aperçus).

## Ce qui n'est pas protégé, et pourquoi

Une page de sécurité qui n'énumère que ses protections se lit comme une promesse. Voici la liste
complémentaire, celle des **non-protections assumées** ([ADR 2736](decisions/2736-le-clair-est-assume-et-annonce.md)).

| Ce qui reste en clair | Où | Pourquoi c'est assumé |
|---|---|---|
| Les **localisations** des points d'écoute et des gîtes | `vigiechiro.db`, dossier de travail | Chiffrer le workspace demanderait une clé, dont aucun emplacement n'est bon (cf. ci-dessous). Le dossier appartient à l'utilisateur (ADR 0048) |
| Le **jeton** de session | `connexion.json`, restreint à `600` | Session de ~14 jours, révocable ; un attaquant local qui le lit lit tout aussi bien la base d'à côté |
| Les **sauvegardes** (base + audio) | là où l'utilisateur les range | Une sauvegarde chiffrée dont on perd la clé est perdue **au moment précis où elle sert** |
| Les **exports** (CSV, sons) | fichier choisi par l'utilisateur | Ils sont faits pour être ouverts ailleurs : les chiffrer les rendrait inutilisables |

**Pourquoi la clé est le vrai obstacle.** Dérivée d'un mot de passe, elle transforme la sauvegarde en
piège. Stockée à côté de l'archive, elle ne protège de rien. Confiée à un coffre système, elle rend la
sauvegarde illisible depuis une autre machine - ce qui est justement son usage.

**Ce qui est fait à la place** : l'application **annonce** ce que ses archives emportent, plutôt que de
décider à la place de l'utilisateur où elles peuvent être rangées (#3212). La phrase vit dans
`ServiceSauvegarde.CE_QU_ELLE_EMPORTE` et sa variante complète : **une seule source pour les deux
surfaces**, l'IHM la disant dans sa confirmation ou son compte rendu, la CLI sur sa sortie standard.
Deux copies d'un même avertissement divergent (ADR 0014).

⚠️ **Ce que cela suppose du contexte** : le produit vise le **poste personnel** d'un naturaliste, pas
un terminal partagé en environnement hostile. Un poste compromis expose les données locales et le
jeton. Si le produit venait à stocker un secret **ré-utilisable ailleurs** (mot de passe, clé d'API
personnelle), ou si la plateforme délivrait un jeton de longue durée, l'arbitrage serait à rouvrir.

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
    pas les deux populations dans ce domaine. Une carte SD du générateur de recette se décompresse
    **environ 140 fois**, la nuit synthétique des parcours E2E **137 fois**, et un enregistrement
    réellement silencieux fait bien davantage : de l'audio silencieux et une bombe sont **les mêmes
    octets**.

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

### Le même raisonnement, en mémoire (#3222)

Ce que #2732 a posé sur le disque, #3222 le pose sur la **mémoire** : six lectures chargeaient une
entrée externe **entière** avant de savoir si elle était exploitable - trois clients HTTP (plateforme
VigieChiro, GBIF, API Géo) et trois lecteurs de fichiers de la carte (journal du capteur par ses deux
portes, relevé climatique).

Même paire de gardes, pour la même raison : **ce qui annonce sa taille est refusé avant d'être lu**
(`Files.size` pour un fichier, `Content-Length` pour une réponse), **ce qui ne l'annonce pas est compté
pendant la lecture** (une réponse en encodage par blocs n'a pas de `Content-Length`, et un plafond
vérifié après avoir tout chargé ne protège de rien : la mémoire est déjà prise).

Les plafonds viennent d'une **mesure sur la carte réelle** `Car640380-2026-Pass2-Z1`, pas d'une
intuition - c'est la leçon du plafond de taux de compression retiré ci-dessus :

| Ce qui est lu | Mesuré sur une nuit réelle | Plafond | Marge |
|---|---|---|---|
| Fichier de la carte (journal, relevé) | 1 862 o (22 lignes) | **32 Mio** (`vigiechiro.import.journal.max-octets`) | 17 000 fois une nuit, 70 fois une saison de dix ans |
| Corps d'une réponse HTTP | 446 Kio (CSV d'observations, 4 032 lignes) | **64 Mio** (`vigiechiro.reseau.corps.max-octets`) | 147 fois le plus gros corps mesuré |

!!! note "Le journal de saison n'existe pas"
    La crainte qui motivait l'issue était une carte laissée en place toute une saison. La mesure l'a
    corrigée : le journal consigne des **événements de session** (démarrage, mode, batterie, veille),
    pas un enregistrement par fichier - 22 lignes pour une nuit qui a produit 4 031 observations. Une
    saison de 250 nuits pèse ~465 Ko, dix ans ~4,7 Mo.

Côté réseau, un dépassement devient un `ReponseApi.Refuse`, **jamais** un `Injoignable` : un
`Injoignable` serait rejoué par la politique de reprise (#2354), et réémettre une requête dont la
réponse est trop grosse la redonne trop grosse. Détail par [ADR
3222](decisions/3222-une-entree-externe-se-lit-sous-plafond.md).

### Hôtes sortants

Tous les appels réseau de l'application, exhaustivement - chacun **best-effort** (une panne dégrade
un confort, jamais un geste métier), sauf l'API VigieChiro qui porte le cœur des échanges :

| Hôte | Usage | Authentifié |
|---|---|---|
| API VigieChiro (Eve) | sites, participations, résultats, dépôt d'une nuit | token de session |
| `*.s3.amazonaws.com` (URLs pré-signées) | téléchargement / téléversement des fichiers son | signature dans l'URL |
| `archive-api.open-meteo.com` | pré-remplissage météo d'un passage | non |
| `api.gbif.org` | clé d'usage des fiches d'espèces | non |
| `geo.api.gouv.fr` | commune d'un point d'écoute depuis son GPS (#2791, ADR 2791) | non |
| `api.github.com` | détection d'une nouvelle version | non |
| tuiles OpenStreetMap (Gluon Maps) | fond de carte des sites | non |

Hors ligne, l'application reste pleinement utilisable : ces enrichissements se taisent, et la
commune des points se comble plus tard (`rattraper-communes`, ou les déclencheurs de l'IHM).

### Les URL pré-signées sont vérifiées avant d'être suivies

Une URL pré-signée est le seul endroit où **le serveur décide où partent les données** : c'est elle
qu'on télécharge, et c'est vers elle que montent les enregistrements d'une nuit. Elle partait telle
quelle dans `URI.create` (#2734) : une API compromise ou mal configurée pouvait donc les envoyer
ailleurs, éventuellement en clair.

`commun.api.UrlSigneeAdmise` la confronte à deux règles **avant** d'ouvrir la moindre connexion :

- **`https` obligatoire**, sans échappatoire. Le serveur code le schéma en dur
  (`vigiechiro-api/vigiechiro/resources/fichiers.py:188`) : une URL en clair ne vient pas du chemin
  nominal.
- **hôte admis** : `s3.amazonaws.com` ou l'un de ses sous-domaines. La comparaison exige le **point de
  séparation** : `bucket.s3.amazonaws.com` passe, `s3.amazonaws.com.pirate.net` non, alors qu'un
  `endsWith` naïf l'aurait admis.

Le refus n'est pas réessayé (`ReponseApi.Refuse` hors 429/5xx) : insister ne rendra pas une URL
inattendue acceptable.

**L'allowlist reste visible et ouvrable.** Le serveur sait renvoyer autre chose : quand
`DEV_FAKE_S3_URL` est configuré (`fichiers.py:125`), l'URL signée devient cette valeur suivie du nom
d'objet. Une liste figée rendrait l'application inutilisable contre une instance de développement.
D'où la propriété système `vigiechiro.s3.hotes`, qui **remplace** la liste, et un message de refus qui
nomme l'hôte observé **et** la propriété : un changement d'hébergement doit être une ligne à ajouter,
pas un mur.

Une **sonde live** (`ContratApiVigieChiroLiveTest`, hors CI) confronte l'allowlist à l'URL que la
plateforme sert vraiment : si l'hébergement change, elle rougit là plutôt que chez un utilisateur.

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
