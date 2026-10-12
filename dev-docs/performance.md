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

Relevé du 2026-10-12, sur la topologie par défaut (**40 carrés de 10 points**, 1000 passages, 4031
observations), sur la machine que nomme `docs/benchmarks/README.md` (i5-1145G7, 8 cœurs, 30,4 Gio,
JDK 25.0.3). Trois lancements :

| Opération | Cible | Froid (1ᵉʳ appel) | Chaud (médiane de 20) |
|---|---|---|---|
| Sélection ~4031 observations (`findByResults`) | < 100 ms | 30,7 · 38,1 · 33,7 ms | 11,0 · 12,5 · 18,7 ms |
| Tri/filtre ~1000 passages (multisite) | < 200 ms | 47,8 · 41,6 · 32,7 ms | 10,3 · 11,2 · 12,3 ms |

Le relevé du 24/08/2026, sans machine nommée, donnait 34,4 et 26,2 ms à froid, 9,5 et 8,2 ms à chaud.
Ces chiffres sont ceux de la couche données : `findByResults` n'est pas la requête que charge l'écran
des sons, et l'IHM n'est pas mesurée (#105).

!!! note "Les deux colonnes, et laquelle regarder"
    Le **froid** dit ce que coûte le premier geste du jour, démarrage de la JVM compris. Le **chaud** dit
    ce que coûte l'opération elle-même. Pour juger une **cible de capacité**, c'est la seconde qui
    compte : une première mesure de processus coûte ~300 ms quelle que soit la taille des données, et
    noierait n'importe quel écart réel.

!!! warning "Ces chiffres remplacent un relevé fait sur **un seul carré**"
    Le jeu du banc semait **un** site de dix points, quelle que soit la valeur de `perf.passages`, et
    annonçait « ~25 ms » et « ~18 ms ». Les écrans lançaient pourtant une requête **par site** puis une
    **par point** : sur cette topologie, onze requêtes, noyées dans le bruit. Le relevé était donc exact
    **et** aveugle - un coordinateur départemental, cent cinquante carrés, en payait plus de quatre
    cents.

    Le jeu sème désormais `perf.sites` carrés (défaut **40**), et `JeuDuBancTest` refuse qu'il retombe à
    un. Les chiffres ci-dessus sont ceux de cette topologie-là.

    Ils sont plus **hauts** que les anciens (34 contre 25, 26 contre 18) et c'est attendu : le jeu est
    quarante fois plus large. Ils restent très en deçà des cibles, et surtout ils mesurent enfin la
    dimension où le coût vivait.

Sans l'index, la sélection faisait un `SCAN` (~75 ms froid) ; l'index la ramène à ~25 ms. Le gain est
**verrouillé par un test CI** (non-régression du plan d'exécution).

### O3 : import d'une nuit

Relevé du 2026-10-12, même machine, WAV de 5 s à 384 kHz, heap par défaut :

| Fichiers | Taille src | Temps total (lancements) | Débit | Après GC |
|---:|---:|---:|---:|---:|
| 100 | 0,36 Gio | 0,77 s | ~478 Mo/s | 56 Mo |
| 1572 | 5,62 Gio | 48,9 · 40,3 · 42,7 s | ~118-147 Mo/s | 81-82 Mo |
| 2517 | 9,00 Gio | 83,5 · 86,6 s | ~106-110 Mo/s | 84-85 Mo |

**Lectures clés** : la nuit standard tient en moins d'une minute ; l'occupation après GC ne croît pas
avec la nuit ; le **débit baisse** quand la nuit grandit, sur la même machine, et la cause n'est pas
établie (le cache disque est l'hypothèse la plus simple, non vérifiée). La copie prend 36 à 47 % du
temps, la transformation 51 à 62 %, la persistance moins de 3 %. Une **vraie nuit** de 1572 bruts
produit **2109 séquences** (découpage à 5 s réelles, `ceil(D/5)` par enregistrement) ; la nuit du banc,
à WAV de 5 s, en produit une par fichier. Le test de charge à 20 Go n'a pas été tiré, faute de disque
(#105).

!!! warning "L'empreinte annoncée n'est pas le besoin en RAM"
    La **crête sous heap généreux** échantillonnée par le banc va de 332 à 1114 Mo d'un lancement à
    l'autre de la même nuit de 1572 WAV : G1 laisse s'accumuler le déchet transitoire jusqu'à son seuil.
    L'occupation **après GC** reste à 81-82 Mo, et la même nuit passe sous `-Xmx128m` au même débit.
    Pour dimensionner une machine, c'est ce second chiffre qui compte.

**La mémoire ne suit plus la durée d'un WAV (#104).** La transformation lit l'original par morceaux
(`FichierWav.ouvrirPcm`), une tranche de 5 s en mémoire par original en vol, au lieu de le charger
entier. Mesuré le 2026-10-12 sur 16 WAV de 120 s : l'occupation après GC passe de 904 à 68 Mo sous
`-Xmx2g`, et la nuit, qui tombait sur une `OutOfMemoryError` sous `-Xmx128m`, s'importe. Le tableau
complet est dans `docs/benchmarks/README.md`.

!!! warning "Des ordres de grandeur, pas des garanties"
    Ces chiffres valent pour la machine qu'ils nomment. Sur un autre poste, refaire la mesure. La règle
    du banc suit `O5.md` : un appel jeté, puis trois relevés dont on publie les trois valeurs ; la valeur
    à froid se publie à part, parce qu'elle mesure le démarrage de la JVM et non la capacité.

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
    **invariant** de l'application, pas une optimisation ponctuelle. Sa moitié « la tâche n'écrit
    pas la scène » est tenue par un espion de test
    ([ADR 6142](decisions/6142-une-tache-de-fond-rend-une-valeur-et-n-ecrit-pas-la-scene.md)).

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

Cette page a d'abord justifié ce découpage par un refus de SQLite au-delà de « quelques centaines »
de paramètres liés. C'est faux pour le pilote embarqué ici : **cinquante mille passent**, mesuré. Le
découpage reste - une requête de cent kilo-octets de marqueurs ne se justifie pas davantage - mais il
n'évite aucun échec, et le dire autrement ferait croire à une protection qui n'existe pas.

**Ce qui ne se lit pas par lot.** Les tables de **volume** - originaux, séquences d'écoute - restent
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
