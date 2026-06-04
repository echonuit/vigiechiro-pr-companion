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
| [`perf.outils.BancImport`](../../src/main/java/fr/univ_amu/iut/perf/outils/BancImport.java) | Génère une **nuit synthétique** de vrais WAV + journal `LogPR`, lance le **vrai** import (`ServiceImport`) et mesure temps (copie/transfo), débit (fichiers/s, Mo/s) et **mémoire crête** (O3). |

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
Sortie : taille, temps total (copie vs transformation #12), débit, nb séquences, **mémoire crête**.

> ⚠️ L'import recopie puis transforme : prévoir **2 à 3× la taille source** en espace disque. Pour viser
> 20 Gio à 384 kHz / 5 s, c'est ~5500 fichiers ; commencer petit (`-Dperf.import.fichiers=100`) pour
> calibrer le temps, puis monter.

## Protocole de mesure (à respecter pour des chiffres comparables)

- **Machine IUT standard** (pas une machine de dev), **première utilisation du jour** : c'est la
  mesure **« à froid »** (JIT non chauffé, cache disque vide) qui fait foi pour les cibles du brief.
- La mesure **« à chaud »** (médiane après itérations) montre le régime établi, utile pour comparer
  **avant / après index** (#28).
- Relancer 2-3 fois et garder l'ordre de grandeur ; ne pas sur-interpréter quelques ms.

## Cibles (brief) et relevés

> Les colonnes « froid / chaud » sont à **remplir sur machine IUT**. Les valeurs ci-dessous sont des
> repères obtenus sur une machine de dev (JIT déjà chaud) : indicatives, **non** représentatives de l'IUT.

| Opération | Cible | Plan d'exécution (SQLite, après #28) | Froid (IUT) | Chaud (IUT) |
|---|---|---|---|---|
| Sélection ~4031 observations (`findByResults`) | < 100 ms | `SEARCH observation USING INDEX idx_obs_results (results_id=?)` (#28 ✓, était `SCAN`) | _à remplir_ | _à remplir_ |
| Tri/filtre ~1000 passages (multisite, verdict) | < 200 ms | `SEARCH passage USING INDEX sqlite_autoindex_passage_1 (point_id=?)` (déjà indexé) | _à remplir_ | _à remplir_ |

**Lecture clé** : la sélection des observations faisait un **balayage complet** (`SCAN`) faute d'index
sur `observation(results_id)` ; **#28** a ajouté `idx_obs_results` → le plan passe à `SEARCH … USING
INDEX` (sur machine de dev : froid ~75 ms → ~37 ms). Les passages profitaient déjà de l'auto-index de
la contrainte `UNIQUE(point_id, year, passage_number)`.

## Suite

- **#28** ✓ : index prioritaires ajoutés (`V03__perf_indexes.sql`). `SCAN observation` → `SEARCH …
  USING INDEX`. Index « faibles » restants (microphone, monitoring_site, taxon) laissés de côté.
- **O3 / 29c** ✓ : `BancImport` mesure l'import d'une nuit volumineuse (temps copie/transfo, débit,
  mémoire crête). Chiffres à relever sur machine IUT. Repère dev : 30 WAV × 1 s (22 Mo) → ~0,14 s,
  ~210 fichiers/s, crête ~28 Mo.
- **29d** (restitution finale) : consigner les chiffres IUT (O5 + O3) dans les tableaux ci-dessus, et
  documenter les procédures **semi-manuelles** restantes : **freeze IHM > 200 ms** (sonde sur le pulse
  JavaFX pendant import/navigation) et **mémoire stabilisée** après plusieurs minutes.
