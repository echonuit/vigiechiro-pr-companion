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

## Protocole de mesure (à respecter pour des chiffres comparables)

- **Machine IUT standard** (pas une machine de dev), **première utilisation du jour** : c'est la
  mesure **« à froid »** (JIT non chauffé, cache disque vide) qui fait foi pour les cibles du brief.
- La mesure **« à chaud »** (médiane après itérations) montre le régime établi, utile pour comparer
  **avant / après index** (#28).
- Relancer 2-3 fois et garder l'ordre de grandeur ; ne pas sur-interpréter quelques ms.

## Cibles (brief) et relevés

> Les colonnes « froid / chaud » sont à **remplir sur machine IUT**. Les valeurs ci-dessous sont des
> repères obtenus sur une machine de dev (JIT déjà chaud) : indicatives, **non** représentatives de l'IUT.

| Opération | Cible | Plan d'exécution (SQLite) | Froid (IUT) | Chaud (IUT) |
|---|---|---|---|---|
| Sélection ~4031 observations (`findByResults`) | < 100 ms | `SCAN observation` **(sans index sur `results_id` → #28)** | _à remplir_ | _à remplir_ |
| Tri/filtre ~1000 passages (multisite, verdict) | < 200 ms | `SEARCH passage USING INDEX sqlite_autoindex_passage_1 (point_id=?)` (déjà indexé) | _à remplir_ | _à remplir_ |

**Lecture clé** : la sélection des observations fait un **balayage complet** (`SCAN`) faute d'index sur
`observation(results_id)` — c'est la cible prioritaire de **#28**. Les passages profitent déjà de
l'auto-index de la contrainte `UNIQUE(point_id, year, passage_number)`.

## Suite

- **#28** : ajouter l'index manquant (`observation(results_id)`, et autres colonnes filtrées), puis
  **relancer ce banc** pour comparer le plan (`SCAN` → `SEARCH … USING INDEX`) et les temps.
- **O3** (nuits volumineuses ≤ 40 Go, 0 freeze IHM > 200 ms, mémoire stable) : générateur de nuit
  volumineuse + sonde import + procédure semi-manuelle (à venir, lot 29c/29d).
