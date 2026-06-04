# Banc de mesure des performances (#29)

> Outillage **enseignant** (hors version étudiante). Sert à vérifier les objectifs d'efficience
> **O3** (tenue dans la durée, nuits volumineuses) et **O5** (capacité : ~4031 observations,
> ~1000 passages). Les cibles chiffrées du brief sont des **ordres de grandeur**, à affiner par un
> premier benchmark **sur les machines IUT en début de Sprint 1** : ce banc est ce benchmark,
> réutilisable ensuite en non-régression.

## Outils

| Outil (`// --solution-only--`) | Rôle |
|---|---|
| [`perf.outils.GenerateurJeuDeDonnees`](../../src/main/java/fr/univ_amu/iut/perf/outils/GenerateurJeuDeDonnees.java) | Peuple une base SQLite : `perf.passages` passages (déf. 1000) + `perf.observations` observations (déf. 4031). Déterministe, base réécrite à neuf. |
| [`perf.outils.BancMesure`](../../src/main/java/fr/univ_amu/iut/perf/outils/BancMesure.java) | Génère le jeu puis chronomètre les opérations O5 **à froid** (1ᵉʳ appel) et **à chaud** (médiane), et imprime l'`EXPLAIN QUERY PLAN` de chaque requête. |
| [`perf.outils.BancImport`](../../src/main/java/fr/univ_amu/iut/perf/outils/BancImport.java) | Génère une **nuit synthétique** de vrais WAV + journal `LogPR`, lance le **vrai** import (`ServiceImport`) et mesure temps (copie / transformation #12 / persistance), débit (fichiers/s, Mo/s) et **mémoire crête** (O3). |

## Lancer le banc (couche données, O5)

JDK 25 standard (comme la CI), depuis la racine du dépôt :

```bash
export JAVA_HOME=~/.sdkman/candidates/java/25.0.2-open
./mvnw -q -DskipTests compile
./mvnw -q org.codehaus.mojo:exec-maven-plugin:exec \
  -Dexec.executable="$JAVA_HOME/bin/java" -Dexec.classpathScope=runtime \
  -Dexec.args="-Dvigiechiro.workspace=/tmp/vigiechiro-bench -cp %classpath fr.univ_amu.iut.perf.outils.BancMesure"
```

Volumes paramétrables : `-Dperf.passages=...`, `-Dperf.observations=...` (à ajouter dans `exec.args`).

## Lancer le banc d'import (O3 — tenue sur nuit volumineuse)

`BancImport` génère une nuit de **vrais WAV** (en-tête RIFF, 384 kHz par défaut) puis lance le vrai
import et mesure temps / débit / mémoire :

```bash
./mvnw -q org.codehaus.mojo:exec-maven-plugin:exec \
  -Dexec.executable="$JAVA_HOME/bin/java" -Dexec.classpathScope=runtime \
  -Dexec.args="--enable-native-access=ALL-UNNAMED -Dvigiechiro.workspace=/tmp/vigiechiro-bench-import \
               -Dperf.import.go=20 -cp %classpath fr.univ_amu.iut.perf.outils.BancImport"
```

Dimensionnement : `-Dperf.import.fichiers=N` **ou** `-Dperf.import.go=<Gio>` (prime sur `fichiers`),
plus `-Dperf.import.secondes` (déf. 5.0) et `-Dperf.import.frequenceHz` (déf. 384000, **multiple de 10**).
Sortie : taille, temps total décomposé en **copie (R9) / transformation (#12) / persistance (O7)**,
débit, nb séquences, **mémoire crête**.

> ⚠️ L'import recopie puis transforme : prévoir **2 à 3× la taille source** en espace disque. Pour viser
> 20 Gio à 384 kHz / 5 s, c'est ~5500 fichiers ; commencer petit (`-Dperf.import.fichiers=100`) pour
> calibrer le temps, puis monter. Le `vigiechiro.workspace` est un **dossier jetable, réinitialisé à
> chaque lancement** (n'y pointez pas un vrai workspace).

## Protocole de mesure (à respecter pour des chiffres comparables)

- **Machine IUT standard** (pas une machine de dev), **première utilisation du jour** : c'est la
  mesure **« à froid »** (JIT non chauffé, cache disque vide) qui fait foi pour les cibles du brief.
- La mesure **« à chaud »** (médiane après itérations) montre le régime établi, utile pour comparer
  **avant / après index** (#28).
- Relancer 2-3 fois et garder l'ordre de grandeur ; ne pas sur-interpréter quelques ms.

## Relevés (machine de référence)

> **Machine de référence** : poste comparable à ceux de l'IUT (_préciser CPU / RAM / SSD si besoin_).
> JDK 25 standard. Chiffres **mesurés** (pas des placeholders) ; relancer 2-3 fois et garder l'ordre de
> grandeur. La 1ʳᵉ utilisation du jour (JIT + cache disque froids) donne les valeurs « froid ».

### O5 — couche données (`BancMesure`)

| Opération | Cible | Plan d'exécution (après #28) | Froid | Chaud (méd.) |
|---|---|---|---|---|
| Sélection ~4031 observations (`findByResults`) | < 100 ms | `SEARCH observation USING INDEX idx_obs_results` (#28, était `SCAN`) | **~25 ms** ✅ | ~8-13 ms |
| Tri/filtre ~1000 passages (multisite, verdict) | < 200 ms | `SEARCH passage USING INDEX sqlite_autoindex_passage_1 (point_id=?)` | **~18 ms** ✅ | ~7-9 ms |

Les deux opérations sont **largement sous les cibles** (facteur ~4 à ~10). Rappel #28 : sans
`idx_obs_results` la sélection faisait un `SCAN` (~75 ms froid sur cette machine) ; avec l'index,
~25 ms.

### O3 — import d'une nuit (`BancImport`, WAV de 2 s @ 384 kHz)

| Fichiers | Taille src | Temps total | copie (R9) | transfo (#12) | persist. (O7) | Débit | Mémoire crête | Séquences |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 100 | 146 Mo | 0,55 s | 0,33 s | 0,18 s | 0,04 s | ~184 f/s · 269 Mo/s | 172 Mo | 400 |
| 500 | 732 Mo | 2,38 s | 1,56 s | 0,75 s | 0,07 s | ~210 f/s · 308 Mo/s | 569 Mo | 2000 |
| 1000 | 1,43 Gio | 3,97 s | 2,77 s | 1,09 s | 0,11 s | ~252 f/s · 369 Mo/s | 626 Mo | 4000 |

**Lectures clés (O3)** :
- Le temps **croît ~linéairement** avec la taille (débit ~stable 180-250 fichiers/s) → tenue dans la
  durée confirmée. La **copie SD→workspace (I/O) domine** (~65-70 %), la transformation parallélisée
  (#12) ~25-30 %, la persistance est négligeable (~3 %).
- **Mémoire** : la crête plafonne (≈ 600-700 Mo pour ~1000-1500 fichiers) — le découpage est borné aux
  cœurs (#12) ; elle croît surtout avec le **nombre de séquences** d'**une** nuit (résultats agrégés
  avant la transaction unique O7), puis est récupérée par le GC entre deux nuits → stable d'un import à
  l'autre.

**Ordre de grandeur à annoncer aux étudiants** : une **vraie nuit** (~1572 fichiers) s'importe en
**~6-8 s** (~200 fichiers/s), produit ~3600 séquences, avec une empreinte ~600-700 Mo. _(Chiffres
machine de référence ; un poste plus modeste sera plus lent — refaire la mesure le cas échéant.)_

## Réactivité IHM (freeze > 200 ms) — procédure semi-manuelle

Dans l'application réelle, l'import s'exécute sur un **thread virtuel** (cf. `ImportationController`),
et la navigation est **verrouillée** pendant `EN_COURS` (#54) : le fil JavaFX n'est jamais bloqué par
le travail lourd, seul `Platform.runLater` y relaie la progression.

**Vérifier l'absence de freeze** :
1. lancer l'application (`./mvnw -q javafx:run`), ouvrir **« Importer une nuit »** ;
2. pointer un dossier de nuit volumineuse (généré par `BancImport`, dossier `source-sd`) et lancer
   l'import ;
3. observer que la **barre de progression avance régulièrement** (pas de gel) et que le reste de la
   fenêtre reste réactif.

**Instrumentation optionnelle** (horodatage du pulse JavaFX) : ajouter temporairement un
`AnimationTimer` qui journalise les écarts entre frames et signale ceux **> 200 ms** :

```java
new AnimationTimer() {
    private long precedent = 0;
    @Override public void handle(long maintenant) { // maintenant en nanosecondes
        if (precedent != 0) {
            double ecartMs = (maintenant - precedent) / 1e6;
            if (ecartMs > 200) System.out.println("FREEZE IHM : " + Math.round(ecartMs) + " ms");
        }
        precedent = maintenant;
    }
}.start();
```

Aucun `FREEZE IHM` ne doit apparaître pendant un import (le découpage étant hors fil JavaFX).

## Mémoire stabilisée — procédure

`BancImport` imprime déjà la **crête** d'une nuit. Pour vérifier la **stabilité dans la durée** (O3,
plusieurs nuits) : enchaîner plusieurs imports (relancer `BancImport`, ou importer plusieurs nuits dans
l'application) et confirmer que la crête **ne croît pas** de nuit en nuit (le GC récupère entre les
imports). Pour un suivi fin, lancer avec `-Xlog:gc` ou échantillonner avec `jcmd <pid> GC.heap_info`.

## Bilan du cluster perf

- **O5 (#26)** : cibles tenues largement (sélection ~25 ms < 100 ms ; tri/filtre ~18 ms < 200 ms),
  index `#28` en place et verrouillé par un test CI.
- **O3 (#27)** : import linéaire et borné en mémoire ; nuit réelle ~6-8 s, crête ~600-700 Mo.
- Outillage réutilisable en **non-régression** : `GenerateurJeuDeDonnees`, `BancMesure`, `BancImport`
  (`// --solution-only--`).
