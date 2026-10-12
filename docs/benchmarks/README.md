# Banc de mesure des performances (#29)

> Outillage de mesure des performances. Sert à vérifier les objectifs d'efficience
> **O3** (tenue dans la durée, nuits volumineuses) et **O5** (capacité : ~4031 observations,
> ~1000 passages). Les cibles chiffrées sont des **ordres de grandeur**, à affiner par un premier
> benchmark sur la machine cible : ce banc est ce benchmark, réutilisable ensuite en non-régression.

## Outils

| Outil (fourni) | Rôle |
|---|---|
| [`perf.outils.GenerateurJeuDeDonnees`](../../src/main/java/fr/univ_amu/iut/perf/outils/GenerateurJeuDeDonnees.java) | Peuple une base SQLite : `perf.sites` carrés (déf. 40) de 10 points, `perf.passages` passages (déf. 1000) + `perf.observations` observations (déf. 4031). Déterministe, base réécrite à neuf. |
| [`perf.outils.BancMesure`](../../src/main/java/fr/univ_amu/iut/perf/outils/BancMesure.java) | Génère le jeu puis chronomètre les opérations O5 **à froid** (1ᵉʳ appel) et **à chaud** (médiane), et imprime l'`EXPLAIN QUERY PLAN` de chaque requête. |
| [`perf.outils.BancImport`](../../src/main/java/fr/univ_amu/iut/perf/outils/BancImport.java) | Génère une **nuit synthétique** de vrais WAV + journal `LogPR`, lance le **vrai** import (`ServiceImport`) et mesure temps (copie / transformation #12 / persistance), débit (fichiers/s, Mo/s) et **mémoire crête** (O3). |

## Lancer le banc (couche données, O5)

JDK 25 standard (comme la CI), depuis la racine du dépôt :

```bash
export JAVA_HOME=~/.sdkman/candidates/java/25.0.2-open
./mvnw -q -DskipTests compile
./mvnw -q org.codehaus.mojo:exec-maven-plugin:exec \
  -Dexec.executable="$JAVA_HOME/bin/java" -Dexec.classpathScope=runtime \
  -Dexec.args="-Dvigiechiro.workspace=$HOME/vigiechiro-bench -cp %classpath fr.univ_amu.iut.perf.outils.BancMesure"
```

Volumes paramétrables : `-Dperf.sites=...`, `-Dperf.passages=...`, `-Dperf.observations=...` (à ajouter
dans `exec.args`).

> **`perf.sites` est la dimension qu'on oublie.** Le jeu a longtemps semé **un** carré, quelle que soit
> la valeur de `perf.passages` : les relevés O5 étaient donc aveugles aux requêtes lancées **par site** ou
> **par point**, qui sont exactement celles qui coûtent à un observateur ayant beaucoup de carrés. Un
> test (`JeuDuBancTest`) refuse désormais que le jeu retombe à un seul carré.

## Lancer le banc d'import (O3 : tenue sur nuit volumineuse)

`BancImport` génère une nuit de **vrais WAV** (en-tête RIFF, 384 kHz par défaut) puis lance le vrai
import et mesure temps / débit / mémoire :

```bash
./mvnw -q org.codehaus.mojo:exec-maven-plugin:exec \
  -Dexec.executable="$JAVA_HOME/bin/java" -Dexec.classpathScope=runtime \
  -Dexec.args="--enable-native-access=ALL-UNNAMED -Dvigiechiro.workspace=$HOME/vigiechiro-bench-import \
               -Dperf.import.go=20 -cp %classpath fr.univ_amu.iut.perf.outils.BancImport"
```

Dimensionnement : `-Dperf.import.fichiers=N` **ou** `-Dperf.import.go=<Gio>` (prime sur `fichiers`),
plus `-Dperf.import.secondes` (déf. 5.0) et `-Dperf.import.frequenceHz` (déf. 384000, **multiple de 10**).
Sortie : taille, temps total décomposé en **copie (R9) / transformation (#12) / persistance (O7)**,
débit, nb séquences, **mémoire crête**.

> L'import recopie puis transforme : prévoir **3× la taille source** en espace disque (mesuré le
> 2026-10-12 : 27,0 Gio de workspace pour 9 Gio de source). Pour viser
> 20 Gio à 384 kHz / 5 s, c'est ~5500 fichiers ; commencer petit (`-Dperf.import.fichiers=100`) pour
> calibrer le temps, puis monter. Le `vigiechiro.workspace` est un **dossier jetable, réinitialisé à
> chaque lancement** (n'y pointez pas un vrai workspace).

## Protocole de mesure (à respecter pour des chiffres comparables)

- **Une machine standard, nommée** : le brief le dit (`O5.md`), et c'est la ligne `machine` de
  `BancImport` qui la nomme. Le poste est au repos pendant la mesure, aucun autre build ni banc ne
  tourne, et le workspace est sur un disque qui a la place (trois fois la taille de la source).
- **Un appel jeté, puis les relevés** : la première mesure d'un processus Java coûte de l'ordre de
  300 ms quelle que soit la taille des données (`O5.md`, « Ce que ce protocole disait de faux »).
  Une mesure à froid mesure donc le démarrage de la JVM, pas la capacité. C'est la valeur à chaud qui
  se compare aux seuils ; la valeur à froid se publie à part, sous son nom, comme l'attente du premier
  écran du jour (ADR 0027).
- **Trois lancements**, dont on publie les trois valeurs : un chiffre seul ne dit pas sa dispersion.

## Relevés du 2026-10-12

Tous les chiffres de cette section viennent du même poste, le même jour, sur `main` à `ea1415febb`
(#104 livré : l'import lit chaque WAV par morceaux). En-tête imprimé par `BancImport` :

```text
machine         : 11th Gen Intel(R) Core(TM) i5-1145G7 @ 2.60GHz · 8 cœurs · RAM 30.4 Gio
exécution       : 25.0.3+9-LTS · Linux 7.0.0-38-generic · heap max 7788 Mo
```

Disque : SSD NVMe interne, workspace et source sur la même partition `ext4`. Les bancs ont tourné l'un
après l'autre, sans autre build sur le poste. Ce n'était pas la première utilisation du jour.

Les relevés de juin que cette section remplaçait n'avaient pas de machine nommée ; ils restent dans
l'historique de #26 et #27, et ne se comparent pas à ceux-ci.

### O5 : couche données (`BancMesure`)

Jeu par défaut : 40 carrés de 10 points, 1000 passages, 4031 observations. Trois lancements, chacun
dans une JVM neuve : le froid est le premier appel du processus, le chaud la médiane des 20 appels qui
suivent.

| Opération | Seuil | Froid (3 lancements) | Chaud, médiane de 20 (3 lancements) |
|---|---|---|---|
| Sélection des 4031 observations (`findByResults`) | < 100 ms | 30,7 · 38,1 · 33,7 ms | 11,0 · 12,5 · 18,7 ms |
| Tri et filtre des 1000 passages (`ServiceMultisite`, verdict OK, 250 lignes rendues) | < 200 ms | 47,8 · 41,6 · 32,7 ms | 10,3 · 11,2 · 12,3 ms |

Plans d'exécution : `SEARCH observation USING INDEX idx_obs_results` et `SEARCH passage USING INDEX
sqlite_autoindex_passage_1`, sans `SCAN`.

**Ce que ces chiffres ne mesurent pas.** Ils chronomètrent la couche données, pas l'écran.
`findByResults` n'est pas la requête que charge l'écran des sons : il passe par
`ProjectionsAudioDao` (`lignesAudioDuPassage`, `lignesAudioNonIdentifiees`), qu'aucun banc ne
chronomètre. Le tri et le filtrage des observations, le clic sur une ligne, l'action de revue et la
topologie de 150 carrés de 2 points ne sont pas mesurés ici : ils se mesurent dans l'IHM (#105).

### O3 : import d'une nuit (`BancImport`, WAV de 5 s à 384 kHz)

Heap par défaut de la JVM (7788 Mo sur ce poste), sauf la ligne `-Xmx128m`. « Après GC » est
l'occupation la plus haute relevée juste après un GC dans le journal G1 (`-Xlog:gc`) : sans GC complet
forcé, c'est une borne haute de ce que l'import tient vivant. La crête est échantillonnée par le banc.

| Nuit | Taille source | Lancement | Temps total | copie | transfo (#12) | persist. (O7) | Débit | Crête | Après GC |
|---|---:|---|---:|---:|---:|---:|---:|---:|---:|
| 100 WAV (calibrage) | 0,36 Gio | 1 | 0,77 s | 0,37 s | 0,32 s | 0,07 s | 130 f/s · 478 Mo/s | 196 Mo | 56 Mo |
| 1572 WAV (nuit standard) | 5,62 Gio | 1 | 48,9 s | 17,8 s | 30,4 s | 0,7 s | 32 f/s · 118 Mo/s | 332 Mo | 82 Mo |
| 1572 WAV | 5,62 Gio | 2 | 40,3 s | 18,6 s | 21,0 s | 0,6 s | 39 f/s · 143 Mo/s | 1114 Mo | 81 Mo |
| 1572 WAV | 5,62 Gio | 3 | 42,7 s | 19,9 s | 21,9 s | 0,9 s | 37 f/s · 135 Mo/s | 350 Mo | 81 Mo |
| 1572 WAV, `-Xmx128m` | 5,62 Gio | 1 | 39,3 s | 17,8 s | 20,2 s | 1,2 s | 40 f/s · 147 Mo/s | 125 Mo (plafond) | passe |
| 2517 WAV (`-Dperf.import.go=9`) | 9,00 Gio | 1 | 83,5 s | 34,3 s | 48,9 s | 0,4 s | 30 f/s · 110 Mo/s | 616 Mo | 84 Mo |
| 2517 WAV | 9,00 Gio | 2 | 86,6 s | 37,8 s | 47,5 s | 1,4 s | 29 f/s · 106 Mo/s | 361 Mo | 85 Mo |

Place occupée par le workspace en fin d'import : 1,07 Gio pour la nuit de 100 WAV, 16,9 Gio pour
1572, 27,0 Gio pour 2517. C'est trois fois la source : la source, sa copie protégée et les séquences.

**Lectures** :

- **Nuit standard, critère 1 d'O3 (« quelques minutes »)** : tenu. 40 à 49 s pour 5,62 Gio.
- **Mémoire** : l'occupation après GC reste entre 81 et 85 Mo de 5,62 à 9 Gio, et la nuit passe sous
  `-Xmx128m`. La crête, elle, va de 332 à 1114 Mo d'un lancement à l'autre de la même nuit : elle dit
  où G1 a placé son seuil, pas ce dont l'import a besoin.
- **Le débit baisse quand la nuit grandit**, sur la même machine (0,36 Gio : 478 Mo/s ; 5,62 Gio :
  118 à 147 Mo/s ; 9 Gio : 106 à 110 Mo/s). Le temps n'est donc pas linéaire en dessous de quelques Gio.
  La cause n'est pas établie ; le cache disque du système, qui contient une petite nuit en entier et
  pas une grande, est l'hypothèse la plus simple, et elle n'a pas été vérifiée.
- **Les séquences** : une par WAV, ces WAV durant 5 s. Une vraie nuit de 1572 bruts en donne 2109,
  parce que ses durées varient : ce relevé ne le reproduit pas.

**Ce que ces chiffres ne mesurent pas** :

- La lecture d'une vraie carte SD. La source vient d'être écrite par le banc sur le SSD, et elle est
  probablement encore dans le cache disque : la copie lit donc plus vite qu'elle ne lirait sur une
  carte.
- L'IHM : `BancImport` n'a pas de fil JavaFX. Le freeze pendant et après un import, et le heap sur
  plusieurs nuits dans un même processus, se mesurent dans l'application (#105).
- **Les 20 Go du test de charge** : non mesurés sur ce poste, faute de disque. Il en faut environ
  60 Gio, et le poste en avait 40 de libres. La nuit de 9 Gio est la plus grosse qu'il pouvait tenir.

### La lecture par morceaux (#104) : la mémoire ne suit plus la durée d'un WAV

Jusqu'à #104, l'import chargeait chaque WAV entier avant de le découper. Il le lit désormais tranche
par tranche, une tranche de 5 s en mémoire par original en vol. Le gain ne se voit pas sur la nuit de
ce tableau, dont chaque WAV de 5 s tient déjà en une tranche : il se voit quand le **fichier** grandit,
le cas d'un enregistreur qui ne découpe pas.

Mesuré le 2026-10-12 (8 cœurs, JDK 25.0.3, Linux, workspace sur disque réel), 16 WAV à 384 kHz, mêmes
réglages et même générateur des deux côtés. « Après GC » est l'occupation maximale relevée juste après
un GC dans le journal G1 (`-Xlog:gc`) :

| Durée d'un WAV | Crête `-Xmx2g`, avant | après | Après GC `-Xmx2g`, avant | après | `-Xmx128m`, avant | après |
|---|---|---|---|---|---|---|
| 5 s | 240 Mo | 176 Mo | 13 Mo | 13 Mo | passe | passe |
| 60 s | 1259 Mo | 132 Mo | 416 Mo | 68 Mo | `OutOfMemoryError` | passe |
| 120 s | 1900 Mo | 210 Mo | 904 Mo | 68 Mo | `OutOfMemoryError` | passe |

Les deux régimes ne se lisent pas l'un avec l'autre. Sous `-Xmx2g`, la crête compte le déchet
transitoire et saute d'un lancement à l'autre ; l'occupation après GC approche ce que l'import tient
vivant, et c'est elle qui cesse de croître avec la durée. Sous `-Xmx128m`, les deux chiffres restent
collés au plafond : ce régime dit si l'import tient, pas combien il garde. Avant #104, une nuit de WAV de
60 s n'y tenait pas, l'erreur partant de `DecoupageParallele`. Un WAV de plus de 2 Gio, qui faisait
tomber l'import, se transforme comme les autres.

**Le nombre de séquences d'une vraie nuit** est un fait de la nuit, pas de la machine :
`Car640380-2026-Pass2-Z1` porte 1572 bruts et 2109 transformés, et notre import les reproduit au bit
près, ce que #504 a vérifié. Le temps et la mémoire d'une nuit de cette taille sont ceux du tableau
O3 plus haut, pour la machine qu'il nomme.

## Réactivité IHM (freeze > 200 ms) : procédure semi-manuelle

Dans l'application réelle, l'import s'exécute **hors du fil JavaFX** (socle `ExecuteurTache`, cf.
`ImportationController`), et la navigation est **verrouillée** pendant `EN_COURS` (#54) : le fil
JavaFX n'est jamais bloqué par le travail lourd, seuls les relais du socle y ramènent la progression.

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

## Mémoire stabilisée : procédure

`BancImport` imprime la **crête d'une nuit**, mais chaque lancement démarre un **JVM neuf** (heap
remis à zéro) : **relancer l'outil ne teste donc pas** la stabilité « de nuit en nuit » ni une fuite
qui ne se révélerait qu'au fil de plusieurs imports **dans le même processus**.

Pour la **stabilité dans la durée** (O3), mesurer **dans un seul processus** :
1. lancer l'**application** (`./mvnw -q javafx:run`) avec `-Xlog:gc` (ou attacher `jcmd <pid>
   GC.heap_info`) ;
2. **importer plusieurs nuits** successivement (générées par `BancImport`, dossier `source-sd`), en
   forçant un GC entre chacune (`jcmd <pid> GC.run`) ;
3. relever le heap **après GC** après chaque nuit : il doit **revenir à un palier stable** (pas de
   dérive croissante) → pas de rétention d'état d'import entre les nuits.

`BancImport` reste l'outil pour la **crête d'une seule nuit** (pic mémoire pendant l'import).

## Bilan du cluster perf

Relevés du 2026-10-12, machine nommée plus haut. La campagne est portée par #105, qui regroupe #26
(O5) et #27 (O3), et elle n'est pas finie.

- **O5, couche données** : sélection des 4031 observations de 11 à 19 ms à chaud (seuil 100 ms) ; tri
  et filtre des 1000 passages de 10 à 12 ms à chaud (seuil 200 ms). Index `#28` en place et verrouillé
  par un test CI. **Dans l'IHM : non mesuré.**
- **O3** : nuit standard de 5,62 Gio en 40 à 49 s ; 9 Gio en 84 à 87 s ; occupation après GC de 81
  à 85 Mo, la nuit standard passant sous `-Xmx128m`. **20 Go, freeze IHM et heap sur plusieurs nuits
  dans l'application : non mesurés.**
- Outillage réutilisable en **non-régression** : `GenerateurJeuDeDonnees`, `BancMesure`, `BancImport`.
