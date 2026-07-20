# Performance et benchmarks

Deux objectifs qualité chiffrés encadrent les performances :

- **O5 — capacité** : tenir une saison réaliste, de l'ordre de **~1000 passages** et **~4031
  observations**, avec des écrans réactifs (sélection < 100 ms, tri/filtre < 200 ms) ;
- **O3 — tenue dans la durée** : importer des **nuits volumineuses** sans dérive de temps ni de
  mémoire.

Le dépôt fournit un **banc de mesure** (`fr.univ_amu.iut.perf.outils`) qui sert à la fois de **premier
benchmark** (calibrer les cibles sur la machine cible) et d'outil de **non-régression**.

!!! info "Source canonique"
    Le protocole détaillé, les commandes exactes et les **relevés chiffrés** vivent dans
    [`docs/benchmarks/README.md`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/docs/benchmarks/README.md)
    (dans le dépôt). Cette page en donne la **carte** et les **ordres de grandeur**.

## Les outils du banc

| Outil | Rôle |
|---|---|
| [`GenerateurJeuDeDonnees`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/perf/outils/GenerateurJeuDeDonnees.java) | Peuple une base SQLite déterministe (déf. 1000 passages + 4031 observations). |
| [`BancMesure`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/perf/outils/BancMesure.java) | Chronomètre les opérations **O5** à froid / à chaud et imprime l'`EXPLAIN QUERY PLAN`. |
| [`BancImport`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/perf/outils/BancImport.java) | Génère une **nuit synthétique** de vrais WAV + `LogPR`, lance le **vrai** import et mesure temps / débit / **mémoire crête** (**O3**). |

On les lance via `exec-maven-plugin`, avec le **JDK 25 standard** (comme la CI) ; les commandes
complètes (avec les propriétés `-Dperf.*` de dimensionnement) sont dans le README du banc.

## Ce que mesurent les relevés

### O5 — couche données

Les deux opérations critiques sont **largement sous les cibles** (facteur ~4 à ~10), grâce à l'index
`idx_obs_results` posé en réponse au point #28 :

| Opération | Cible | Froid (réf.) |
|---|---|---|
| Sélection ~4031 observations (`findByResults`) | < 100 ms | ~25 ms ✅ |
| Tri/filtre ~1000 passages (multisite) | < 200 ms | ~18 ms ✅ |

Sans l'index, la sélection faisait un `SCAN` (~75 ms froid) ; l'index la ramène à ~25 ms. Le gain est
**verrouillé par un test CI** (non-régression du plan d'exécution).

### O3 — import d'une nuit

Le temps d'import **croît linéairement** avec la taille (débit ~stable 180-250 fichiers/s), la
**mémoire plafonne** (~600-700 Mo pour ~1000-1500 fichiers) :

| Fichiers | Taille src | Temps total | Débit | Mémoire crête |
|---:|---:|---:|---:|---:|
| 100 | 146 Mo | 0,55 s | ~184 f/s | 172 Mo |
| 500 | 732 Mo | 2,38 s | ~210 f/s | 569 Mo |
| 1000 | 1,43 Gio | 3,97 s | ~252 f/s | 626 Mo |

**Lectures clés** : la **copie SD→workspace (I/O) domine** (~65-70 %), la transformation parallélisée
(#12) ~25-30 %, la persistance est négligeable (~3 %). Ordre de grandeur de référence : une **vraie
nuit** (~1572 fichiers) s'importe en **~6-8 s**, ~2100 séquences (découpage à 5 s réelles, `ceil(D/5)`
par enregistrement), empreinte ~600-700 Mo.

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

Le socle est [`ExecutionParallele`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/model/ExecutionParallele.java) :
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
