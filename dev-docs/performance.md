# Performance et benchmarks

Deux objectifs qualité chiffrés encadrent les performances :

- **O5, capacité** : tenir une saison réaliste, de l'ordre de **~1000 passages** et **~4031
  observations**, avec des écrans réactifs (sélection < 100 ms, tri/filtre < 200 ms) ;
- **O3, tenue dans la durée** : importer des **nuits volumineuses** sans dérive de temps ni de
  mémoire.

Le dépôt fournit un **banc de mesure** (`fr.univ_amu.iut.perf.outils`) qui sert à la fois de **premier
benchmark** (calibrer les cibles sur la machine cible) et d'outil de **non-régression**.

!!! info "Source canonique"
    Le protocole détaillé, les commandes exactes et les **relevés chiffrés** vivent dans
    [`docs/benchmarks/README.md`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/docs/benchmarks/README.md)
    (dans le dépôt). Cette page en donne la **carte** et les **ordres de grandeur**.

## Les outils du banc

| Outil | Rôle |
|---|---|
| [`GenerateurJeuDeDonnees`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/perf/outils/GenerateurJeuDeDonnees.java) | Peuple une base SQLite déterministe (déf. 1000 passages + 4031 observations). |
| [`BancMesure`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/perf/outils/BancMesure.java) | Chronomètre les opérations **O5** à froid / à chaud et imprime l'`EXPLAIN QUERY PLAN`. |
| [`BancImport`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/perf/outils/BancImport.java) | Génère une **nuit synthétique** de vrais WAV + `LogPR`, lance le **vrai** import et mesure temps / débit / **mémoire crête** (**O3**). |

On les lance via `exec-maven-plugin`, avec le **JDK 25 standard** (comme la CI) ; les commandes
complètes (avec les propriétés `-Dperf.*` de dimensionnement) sont dans le README du banc.

## Ce que mesurent les relevés

### O5 : couche données

Les deux opérations critiques sont **largement sous les cibles** (facteur ~4 à ~10), grâce à l'index
`idx_obs_results` posé en réponse au point #28 :

| Opération | Cible | Froid (réf.) |
|---|---|---|
| Sélection ~4031 observations (`findByResults`) | < 100 ms | ~25 ms ✅ |
| Tri/filtre ~1000 passages (multisite) | < 200 ms | ~18 ms ✅ |

!!! warning "Ces deux chiffres ont été relevés sur **un seul carré**"
    Le jeu du banc semait **un** site de dix points, quelle que soit la valeur de `perf.passages`. Les
    écrans lançaient pourtant une requête **par site** puis une **par point** : sur cette topologie, onze
    requêtes, noyées dans le bruit. Le relevé « ~18 ms ✅ » était donc exact **et** aveugle - un
    coordinateur départemental, cent cinquante carrés, en payait plus de quatre cents.

    Le jeu sème désormais `perf.sites` carrés (défaut **40**), et un test
    (`JeuDuBancTest`) refuse qu'il retombe à un. **Les deux chiffres ci-dessus sont donc à refaire** sur
    la nouvelle topologie : ils ne sont pas faux, ils mesuraient autre chose que ce qu'on croyait.

Sans l'index, la sélection faisait un `SCAN` (~75 ms froid) ; l'index la ramène à ~25 ms. Le gain est
**verrouillé par un test CI** (non-régression du plan d'exécution).

### O3 : import d'une nuit

Le temps d'import **croît linéairement** avec la taille (débit ~stable 180-250 fichiers/s), la
**mémoire plafonne** (~600-700 Mo pour ~1000-1500 fichiers) :

| Fichiers | Taille src | Temps total | Débit | Mémoire crête |
|---:|---:|---:|---:|---:|
| 100 | 146 Mo | 0,55 s | ~184 f/s | 172 Mo |
| 500 | 732 Mo | 2,38 s | ~210 f/s | 569 Mo |
| 1000 | 1,43 Gio | 3,97 s | ~252 f/s | 626 Mo |

**Lectures clés** : la **copie SD→workspace (I/O) domine** (~65-70 %), la transformation parallélisée
(#12) ~25-30 %, la persistance est négligeable (~3 %). Ordre de grandeur de référence : une **vraie
nuit** (1572 fichiers) s'importe en **~6-8 s**, **2109 séquences** (découpage à 5 s réelles,
`ceil(D/5)` par enregistrement), empreinte ~600-700 Mo.

!!! warning "L'empreinte annoncée n'est pas le besoin en RAM"
    Les ~600-700 Mo sont une **crête sous heap généreux** : G1 laisse s'accumuler le déchet
    transitoire jusqu'à son seuil. Le **live set** est bien plus petit - la même nuit passe sous
    `-Xmx128m` avec 82-83 Mo vifs, au même débit (#104). Pour dimensionner une machine, c'est ce
    second chiffre qui compte.

!!! warning "Des ordres de grandeur, pas des garanties"
    Ces chiffres viennent d'une **machine de référence**. Sur un poste plus modeste, refaire la mesure.
    La règle du banc : **première utilisation du jour** (JIT + cache disque froids) pour la valeur
    « à froid » qui fait foi, puis relancer 2-3 fois et garder l'ordre de grandeur.

### Choisir un mécanisme de parallélisme

Deux questions distinctes, qu'il est tentant de confondre :

- **Quel mécanisme ?** La nature de l'attente décide. La tâche **attend** (réseau, disque) → fil
  virtuel + `Semaphore`. Elle **calcule** (DEFLATE, transformation audio) → `newFixedThreadPool`
  dimensionné sur `availableProcessors()`, parce que des fils virtuels ne multiplient pas les cœurs.
- **Quelle borne ?** Elle se chiffre sur **ce qu'elle protège**, pas sur le mécanisme : la politesse
  envers la plateforme (5 ou 8), le pic disque (fenêtre 2), les cœurs, ou le débit du support source.

Le socle est [`ExecutionParallele`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/model/ExecutionParallele.java) :
fils virtuels bornés, ordre préservé, progression monotone, annulation coopérative. Le détail et la
seule exception tolérée sont dans
l'[ADR 0044](decisions/0044-le-mecanisme-de-parallelisme-suit-la-nature-de-l-attente.md).

## Réactivité de l'IHM (pas de freeze)

Au-delà des chiffres, la perception d'efficience tient à **l'absence de gel** de l'interface. Dans
l'application, l'import s'exécute **hors du fil JavaFX** via le socle `ExecuteurTache` (#793/#1256)
et la navigation est **verrouillée** pendant l'état `EN_COURS` (#54) : le fil JavaFX n'est jamais
bloqué par le travail lourd, seuls les relais du socle (`relaisProgression`, `surFilJavaFx()`) y
ramènent la progression. Le README du banc donne une procédure semi-manuelle pour **détecter tout
écart de frame > 200 ms** (un `AnimationTimer` d'instrumentation) et une procédure pour vérifier la
**stabilité mémoire de nuit en nuit** (heap après GC dans un seul processus).

!!! note "Lien avec l'architecture"
    Cette discipline du fil JavaFX (travail lourd hors fil, retour et progression relayés par le
    socle `ExecuteurTache`, cf. [Patterns](patterns.md)) est la même que celle décrite côté
    [Navigation et chrome](navigation.md) et appliquée à toutes les tâches longues : c'est un
    **invariant** de l'application, pas une optimisation ponctuelle.

## Les écritures de masse passent par une unité de travail

Chaque appel DAO **auto-commit** par défaut. Sur SQLite, un commit est un `fsync` : une boucle qui écrit
ligne par ligne paie donc un aller-retour disque **par ligne**, et le coût ne se voit pas en test, où les
jeux d'essai comptent quelques unités.

Mesuré sur une réactivation réelle (#1959) : l'adoption des originaux d'une nuit reconstruite faisait un
`INSERT` par brut (~2042) et un rattachement par séquence (~4626), soit près de **6700 commits**, pour
**plus de deux minutes** d'attente muette. La même écriture, groupée dans une `UniteDeTravail`, tient dans
une seule transaction.

**La règle.** Toute boucle d'écriture dont le nombre d'itérations suit les données (séquences, observations,
originaux) passe par `UniteDeTravail.executer(connexion -> …)` et les variantes transactionnelles des DAO
(`insert(Connection, …)`, `executerMaj(Connection, …)`). C'est ce que fait `MoteurImport` depuis l'origine
pour la même masse.

Deux nuances utiles :

- **Ce qui doit lire ce que la transaction vient de valider reste dehors.** Le nettoyage des placeholders
  de l'adoption compte une poignée d'ordres et lit les rattachements tout juste commités : l'inclure
  demanderait des lectures sur la connexion transactionnelle, pour un gain nul.
- **L'atomicité est un effet de bord bienvenu, pas le motif.** Le motif est le coût ; mais une écriture de
  masse groupée ne laisse plus, en cas d'interruption, un état à moitié écrit.

## Les lectures de masse passent par un lot

La règle ci-dessus vaut pour les **écritures**. La même figure existe côté **lectures**, et elle a coûté
davantage parce que rien ne la nommait.

Un écran qui compose N lignes en interrogeant la base **pour chacune** paie N requêtes là où une seule
suffirait. Ce n'est pas un `fsync` par ligne, donc c'est moins spectaculaire qu'une écriture de masse -
mais le coût **croît avec l'inventaire de l'utilisateur**, c'est-à-dire qu'il est absent des jeux d'essai
et maximal chez celui qui a le plus de données.

Mesuré sur six chemins (#4251, #4271, #4278, #4280, #4283, #4286, #4293), à cent cinquante carrés :

| Chemin | Avant | Après |
|---|---|---|
| « Mes sites » | 165-241 ms | 6-8 ms |
| « Carte & passages » | 339-377 ms | 15-18 ms |
| « Ma saison » | 386-409 ms | 6-12 ms |
| Audit de cohérence | 11 requêtes par nuit | 2 |
| Export des sons | 3 requêtes par son | lecture unique |

**La règle.** Toute boucle qui compose des lignes à partir de la base lit **par lot** avant de boucler :
`PointDao#findParSites`, `PassageDao#findParPoints`, `SequenceDao#findParIds`, ou un index construit
depuis un `findAll()` quand la table porte une ligne par entité. Les lectures par identifiants passent
par `LotsDeParametres.decouper`, qui borne la **taille de la requête**.

⚠️ Cette page a d'abord justifié ce découpage par un refus de SQLite au-delà de « quelques centaines »
de paramètres liés. C'est faux pour le pilote embarqué ici : **cinquante mille passent**, mesuré. Le
découpage reste - une requête de cent kilo-octets de marqueurs ne se justifie pas davantage - mais il
n'évite aucun échec, et le dire autrement ferait croire à une protection qui n'existe pas.

⚠️ **Ce qui ne se lit pas par lot.** Les tables de **volume** - originaux, séquences d'écoute - restent
lues par session. Une nuit en porte des milliers : les charger toutes d'un bloc échangerait un défaut de
lenteur contre un défaut de mémoire.

### Comment on chiffre une lecture répétée

**Pas au chronomètre.** Trois façons de se tromper ont été rencontrées le même jour :

- **la première mesure d'un processus n'est pas une mesure** : le démarrage de la JVM coûte ~300 ms quelle
  que soit la taille des données, et comparer deux chiffres pris à des rangs différents compare deux
  choses différentes ;
- **lire le code sous-compte** : l'audit semblait faire six requêtes par nuit, le compteur en a trouvé
  **onze** - cinq vivaient un appel plus bas ;
- **une autre session charge la machine** : un banc filmé (`ffmpeg`) faisait varier les relevés du simple
  au double.

Ce qui marche est de **compter les connexions ouvertes**, en sous-classant `SourceDeDonnees`, puis de
comparer ce compte à **deux tailles de jeu**. C'est déterministe, insensible à la charge, et cela attrape
les requêtes **où qu'elles vivent** dans la pile d'appels. Deux exemples dans le dépôt :
`RequetesDeLAuditTest` et `ExportObservationsEtSonsTest#l_export_lit_par_lot`.
