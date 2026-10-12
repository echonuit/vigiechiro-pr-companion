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
| [`perf.outils.BancIhmO5Test`](../../src/test/java/fr/univ_amu/iut/perf/outils/BancIhmO5Test.java) et [`BancIhmO3Test`](../../src/test/java/fr/univ_amu/iut/perf/outils/BancIhmO3Test.java) | Banc de l'**IHM** (#105) : monte les vrais écrans sur une vraie base et chronomètre les gestes d'O5 (ouverture, tri, filtre, clic, revue ; topologie), puis importe plusieurs nuits dans le même processus, sonde des images posée, heap après GC (O3). Étiqueté `banc-ihm`, jamais en CI. |

## Lancer le banc (couche données, O5)

JDK 25 standard (comme la CI), depuis la racine du dépôt :

```bash
export JAVA_HOME=~/.sdkman/candidates/java/25.0.2-open
./mvnw -q -DskipTests compile
./mvnw -q org.codehaus.mojo:exec-maven-plugin:exec \
  -Dexec.executable="$JAVA_HOME/bin/java" -Dexec.classpathScope=runtime \
  -Dexec.args="-Dvigiechiro.workspace=$HOME/vigiechiro-bench -cp %classpath fr.univ_amu.iut.perf.outils.BancMesure"
```

Volumes paramétrables : `-Dperf.sites=...`, `-Dperf.points=...` (points par carré, défaut 10),
`-Dperf.passages=...`, `-Dperf.observations=...` (à ajouter dans `exec.args`). La topologie d'O5, cent
cinquante carrés de deux points, est `-Dperf.sites=150 -Dperf.points=2`.

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
> 2026-10-12 : 27,0 Gio de workspace pour 9 Gio de source, 60,0 Gio pour 20 Gio). Pour viser
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
- **Le banc de l'IHM se tire à une échelle d'écran écrite.** Il dessine en rendu logiciel : sur un
  écran que le système double, chaque image coûte quatre fois plus de pixels, et l'écart entre deux
  images va du simple au triple pendant un import, davantage en navigation (mesuré le 2026-10-12,
  « Le test de charge à 20 Gio »).
  L'échelle se force à 1 par `-Dglass.gtk.uiScale=1`, ou s'écrit à côté du relevé.

## Relevés du 2026-10-12

Sauf le test de charge à 20 Gio, qui nomme son propre poste dans sa sous-section, tous les chiffres
de cette section viennent du même poste, le même jour, sur `main` à `ea1415febb` (#104 livré :
l'import lit chaque WAV par morceaux). En-tête imprimé par `BancImport` :

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
  plusieurs nuits dans un même processus, se mesurent dans l'IHM : section suivante.
- **Les 20 Go du test de charge** : non mesurés sur ce poste, faute de disque. Il en faut environ
  60 Gio, et le poste en avait 40 de libres. La nuit de 9 Gio est la plus grosse qu'il pouvait tenir.
  Ils sont mesurés sur un second poste : « Le test de charge à 20 Gio », plus bas.

### O5 et O3 dans l'IHM (`BancIhmO5Test`, `BancIhmO3Test`)

Mesuré le 2026-10-12 sur la même machine, sur `main` à `2d4107261a` plus la branche de #105 (le banc et le
réglage `perf.points`). Un seul tir, sur l'affichage réel (XWayland, profil `recette-filmee`, rendu
logiciel `prism.order=sw` posé par le pom), fenêtre de 1280 × 860. Charge du poste sur 1 minute : 1,18 au
départ, de 1,73 à 3,26 pendant le tir, le banc compris.

Un premier tir, le même jour, a été jeté : ses quatre nuits portaient la même série et la même date, et
l'assistant a demandé trois fois « cette nuit a déjà été importée, importer quand même ? ». Un clic
humain est entré dans la mesure. Le banc date désormais chaque nuit, refuse toute confirmation et
s'arrête à la première fenêtre en trop (voir « Ses contrôles »).

**Comment le banc mesure.** Il monte les vrais écrans (`MainView.fxml`) sur la composition complète et une
vraie base. Le robot met l'écran en place mais n'entre jamais dans le chronomètre : chaque geste passe par
l'API du contrôle sur le fil JavaFX (ordre de tri de la table, texte du champ de recherche de l'écran,
modèle de sélection, `fire()` du bouton). Le temps court de ce déclenchement jusqu'au premier pulse, après
mise en page, où l'écran montre le résultat attendu (`Scene#addPostLayoutPulseListener`) : il compte le
calcul, le CSS et la mise en page, pas le dessin de cette image. Chaque geste est joué une fois pour rien,
puis trois fois ; les trois valeurs sont publiées, la première à part.

**Ses contrôles**, rejoués dans ce tir : un geste qui bloque le fil 300 ms se lit à 301,1 ms, et la sonde y
voit un écart de 304,5 ms ; une modale ouverte exprès et une question posée exprès arrêtent chacune le
chronomètre. Vus rouges en Headless : un chronomètre démarré après le geste, une sonde aveugle, un filet
des fenêtres muet, et des nuits de même date (le banc s'arrête alors en 8,7 s sur la question). Le
**témoin à vide**, une action qui ne fait rien, attend le pulse suivant : 15 à 16 ms, le plancher de chaque
chiffre ci-dessous.

| O5, écran des sons à 4031 observations | Relevés | Seuil | Verdict |
|---|---|---|---|
| tri par la colonne probabilité | 33,3 · 27,0 · 27,2 ms | dizaines à centaines de ms | tenu |
| filtre par taxon (recherche de l'écran) | 64,9 · 66,0 · 39,9 ms | dizaines à centaines de ms | tenu |
| retrait du filtre | 63,8 · 36,9 · 43,4 ms | idem | tenu |
| clic sur une ligne (sélection) | 11,0 · 4,8 · 3,7 ms | < 100 ms | tenu |
| action de revue (valider) | 105,7 · 70,7 · 76,5 ms | aucun dans le brief | |
| ouverture de l'écran | 236,5 · 241,8 · 230,0 ms (premier : 464,1) | aucun dans le brief | |

| O5, vue multisite à 1000 passages | Relevés | Seuil | Verdict |
|---|---|---|---|
| tri par la colonne date | 13,0 · 10,3 · 9,9 ms | < 200 ms | tenu |
| filtre (carré, recherche de l'écran) | 23,7 · 18,7 · 20,3 ms | < 200 ms | tenu |
| retrait du filtre | 18,2 · 18,6 · 22,8 ms | < 200 ms | tenu |
| ouverture de la vue | 133,8 · 375,5 · 125,8 ms (premier : 355,2) | aucun dans le brief | |

| O5, topologie : ouverture | 15 carrés de 2 points | 150 carrés de 2 points | Seuil | Verdict |
|---|---|---|---|---|
| Mes sites | 41,9 · 41,4 · 54,1 ms | 346,8 · 360,7 · 339,0 ms | < 50 ms, sans croître | **non tenu** (#6172) |
| vue multisite | 352,2 · 75,8 · 303,5 ms | 174,1 · 170,8 · 151,3 ms | < 50 ms | **non tenu** (#6172) |
| écran des sons | 270,0 · 251,7 · 229,5 ms | 106,3 · 113,5 · 119,5 ms | < 50 ms | **non tenu** (#6172) |

| O3, quatre nuits distinctes de 300 WAV de 5 s, un seul processus | Nuit 1 | Nuit 2 | Nuit 3 | Nuit 4 |
|---|---|---|---|---|
| durée de l'import, du clic au compte rendu | 1,4 s | 1,0 s | 1,0 s | 1,1 s |
| écart max entre deux images pendant l'import | 151 ms | 98 ms | 76 ms | 87 ms |
| écart max en navigation après l'import | 758 ms | 224 ms | 255 ms | 244 ms |
| écarts de plus de 200 ms en navigation (neuf ouvertures) | 5 | 4 | 4 | 5 |
| heap après GC (50 Mo avant la nuit 1) | 148 Mo | 208 Mo | 265 Mo | 322 Mo |

**Lectures** :

- **Pendant l'import, aucun écart ne dépasse 200 ms** : tenu, sur des nuits de 300 WAV. L'import tourne hors
  du fil JavaFX. À 20 Gio, sur un second poste, le seuil ne tient plus : voir plus bas (#6175).
- **En navigation après l'import, le seuil n'est pas tenu** (#6171) : les écarts tombent à l'ouverture
  des écrans, à chaque nuit, et pas seulement à la première ouverture. Ils ne grandissent pas avec le
  nombre de nuits en base.
- **Le heap après GC grandit de 57 à 98 Mo par nuit** (#6170). Ce n'est pas une fuite établie : le
  processus porte aussi TestFX et l'espion des écritures, et la base grandit. Un histogramme de classes
  entre deux nuits, puis la même mesure dans l'application lancée hors des tests, départagera.
- La durée d'import n'est pas le chiffre O3 de ce banc : c'est celui de `BancImport`, plus haut.

**Ce que ces chiffres ne mesurent pas** : la latence d'entrée (souris et clavier contournés), le dessin
de l'image et son affichage par le compositeur, un rendu GPU, une vraie carte SD et une autre machine.
L'échelle de l'écran de ce poste n'a pas été relevée ; le second poste, tiré à l'échelle 1, rend
presque les mêmes chiffres. Les 20 Go ont leur sous-section.

**Le relancer** (jamais en CI : l'étiquette `banc-ihm` est exclue par défaut) :

```bash
./mvnw test -Precette-filmee -Dsurefire.groups=banc-ihm -Dsurefire.excludedGroups=api-live \
  '-Dtest=BancIhmO5Test,BancIhmO3Test' -Dperf.ihm.racine=$HOME/banc-ihm
```

Volumes : `-Dperf.ihm.observations` (4031), `-Dperf.ihm.passages` (1000), `-Dperf.ihm.nuits` (4),
`-Dperf.ihm.wav` (300 WAV par nuit, environ 1,1 Gio gardé par nuit importée : les séquences seules,
l'assistant ne recopiant pas les bruts par défaut). Le relevé s'ajoute à `releve.tsv` sous
`perf.ihm.racine`.

### Le test de charge à 20 Gio, sur un second poste

Le premier poste n'avait pas le disque. Ce relevé vient d'un autre, le 2026-10-12, sur `main` à
`d1f6c7d5c9` (le banc de l'IHM fusionné, #6173). En-tête imprimé par `BancImport` :

```text
machine         : Intel(R) Core(TM) Ultra 7 255H · 16 cœurs · RAM 30.3 Gio
exécution       : 25.0.3+9-LTS · Linux 7.0.0-38-generic · heap max 7768 Mo
```

Disque : SSD NVMe interne (Samsung MZVLC1T0HFLU, 954 Go), une partition `ext4` qui avait 640 Go
libres. La source, le workspace et `java.io.tmpdir` étaient sur cette partition : `/tmp` est un `tmpfs`
sur ce poste. Un tir à la fois, sans autre build ; l'éditeur du poste restait ouvert, et ce n'était pas
la première utilisation du jour. Seize cœurs contre huit et un autre disque : ces chiffres ne se
comparent pas à ceux du premier poste.

**`BancImport`** (`-Dperf.import.go=20`, heap par défaut, `-Xlog:gc` dans un fichier). Deux lancements,
chacun dans une JVM neuve, la nuit supprimée entre les deux. La charge est celle du poste sur 1 minute,
au départ du tir :

| Nuit | Taille source | Lancement | Temps total | copie | transfo (#12) | persist. (O7) | Débit | Crête | Après GC | Charge |
|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 100 WAV (calibrage) | 0,36 Gio | 1 | 0,78 s | 0,27 s | 0,26 s | 0,25 s | 128 f/s · 469 Mo/s | 183 Mo | 89 Mo | 1,32 |
| 5593 WAV (`-Dperf.import.go=20`) | 20,00 Gio | 1 | 20,75 s | 7,65 s | 12,05 s | 1,04 s | 270 f/s · 987 Mo/s | 1404 Mo | 108 Mo | 1,61 |
| 5593 WAV | 20,00 Gio | 2 | 19,73 s | 7,39 s | 11,66 s | 0,68 s | 283 f/s · 1038 Mo/s | 2396 Mo | 115 Mo | 0,98 |

Place occupée par le workspace en fin d'import : 1,07 Gio pour 100 WAV, 60,0 Gio (64,4 Go) pour
20 Gio, aux deux lancements. C'est encore trois fois la source. Charge au plus haut pendant les tirs à
20 Gio : 7,45 et 5,28, le banc compris.

**Dans l'IHM** (`BancIhmO3Test`). Quatre tirs, sur l'affichage réel (XWayland, profil `recette-filmee`,
rendu logiciel `prism.order=sw` posé par le pom), fenêtre de 1280 × 860. L'écran fait 3072 × 1920 et
le serveur X l'annonce à 192 points par pouce, le double de 96. Deux tailles, la nuit de 20 Gio
(`-Dperf.ihm.nuits=1 -Dperf.ihm.wav=5593`) et les quatre nuits de 300 WAV du premier poste
(`-Dperf.ihm.nuits=4 -Dperf.ihm.wav=300`), chacune à l'échelle par défaut de cet écran puis à
l'échelle forcée à 1 (`-Dglass.gtk.uiScale=1`). Charge du poste sur 1 minute, au départ puis au plus
haut, le banc compris. À 20 Gio : 1,59 et 7,15 à l'échelle par défaut, 1,67 et 4,20 à l'échelle 1.
À 300 WAV : 0,63 et 3,60, puis 1,55 et 2,27.

| O3, une nuit de 5593 WAV de 5 s (20,00 Gio) | Échelle par défaut | Échelle 1 | Seuil | Verdict |
|---|---|---|---|---|
| durée de l'import, du clic au compte rendu | 14,4 s | 13,0 s | | |
| images vues par la sonde pendant l'import | 343 | 461 | | |
| écart max entre deux images pendant l'import | 472 ms | 465 ms | 200 ms | **non tenu** (#6175) |
| écarts de plus de 200 ms pendant l'import | 253, 472, 225 ms | 268, 441, 465 ms | aucun | **non tenu** (#6175) |
| écart max en navigation après l'import | 1310 ms | 377 ms | 200 ms | **non tenu** (#6171) |
| heap après GC complet, avant puis après la nuit | 68 puis 247 Mo | 51 puis 160 Mo | palier stable | voir #6170 |
| journal G1 : au plus haut après une pause jeune, et crête | 234 et 2323 Mo | 188 et 2176 Mo | | |

| O3, quatre nuits de 300 WAV de 5 s, un seul processus | Échelle par défaut | Échelle 1 | Premier poste |
|---|---|---|---|
| durée de l'import, nuits 1 à 4 | 1,2 · 0,8 · 0,7 · 0,8 s | 0,9 · 0,8 · 0,7 · 0,7 s | 1,4 · 1,0 · 1,0 · 1,1 s |
| images vues par la sonde pendant l'import | 17 · 18 · 10 · 12 | 21 · 24 · 29 · 25 | non relevé |
| écart max entre deux images pendant l'import | 338 · 235 · 224 · 301 ms | 99 · 100 · 100 · 114 ms | 151 · 98 · 76 · 87 ms |
| écart max en navigation après l'import | 1323 · 1272 · 1276 · 1277 ms | 384 · 197 · 269 · 198 ms | 758 · 224 · 255 · 244 ms |
| écarts de plus de 200 ms en navigation (neuf ouvertures) | 9 · 7 · 6 · 8 | 6 · 0 · 1 · 0 | 5 · 4 · 4 · 5 |
| heap après GC, avant la nuit 1 puis après chaque nuit | 68, puis 282 · 380 · 478 · 576 Mo | 51, puis 147 · 204 · 263 · 320 Mo | 50, puis 148 · 208 · 265 · 322 Mo |

Aucune fenêtre ni question ne s'est ouverte pendant ces quatre tirs. Avant eux, le banc a été vu rouge
sur ce poste, en Headless : deux nuits de même date l'arrêtent seul, en 13 s, sur la question « nuit
déjà importée ».

**Lectures** :

- **Temps, débit, crête** : 20 à 21 s pour 20 Gio, de 987 à 1038 Mo/s. La crête va de 1404 à 2396 Mo
  d'un lancement à l'autre de la même nuit, comme sur le premier poste.
- **Mémoire** : l'occupation après GC est de 108 à 115 Mo à 20 Gio, contre 89 Mo à 0,36 Gio sur ce
  poste. Cinquante-cinq fois plus de données, de 19 à 26 Mo de plus.
- **Le débit ne baisse pas avec la taille sur ce poste** (469 Mo/s à 0,36 Gio), à l'inverse du premier.
  Cela s'accorde avec l'hypothèse du cache disque sans la prouver : ce poste avait environ 19 Gio de
  mémoire disponible, presque toute la source.
- **À 20 Gio, le seuil de 200 ms n'est pas tenu pendant l'import** (#6175) : 472 puis 465 ms, trois
  écarts à chaque tir, quelle que soit l'échelle. À 300 WAV et à l'échelle 1, le même poste le tient
  (de 99 à 114 ms), comme le premier (de 76 à 151 ms). À affichage égal, c'est donc la taille de la
  nuit qui fait passer le seuil.
- **L'échelle de l'écran change tout le reste.** À l'échelle par défaut, la sonde voit de 14 à
  24 images par seconde pendant l'import, contre 23 à 41 à l'échelle 1 ; les nuits de 300 WAV
  dépassent le seuil (de 224 à 338 ms) et la navigation gèle de 1,2 à 1,3 s. Ces chiffres-là
  mesurent le dessin en rendu logiciel, pas l'application : ils ont motivé la règle du protocole.
- **En navigation, le seuil n'est pas tenu à l'échelle 1 non plus** (#6171) : de 197 à 384 ms, l'ordre
  de grandeur du premier poste, et 377 ms après la nuit de 20 Gio. L'écart ne suit pas la taille de la
  nuit.
- **Le heap après GC, à l'échelle 1, est celui du premier poste à quelques Mo près** (#6170) : de 57 à
  59 Mo de plus par nuit. Deux faits nouveaux : la nuit de 20 Gio laisse 160 Mo, 13 de plus qu'une
  nuit de 300 WAV pour dix-neuf fois plus de séquences ; et l'échelle par défaut porte le gain à 98 Mo
  par nuit. Ce qui grandit suit donc la surface dessinée plus que les données.
- **Ce ne sont pas des pauses du ramasse-miettes** : hors GC complet, la plus longue du journal dure
  72 ms sur les quatre tirs, et 20 ms dans le tir à 20 Gio à l'échelle 1. Les GC complets, que le
  banc demande lui-même, tombent hors des périodes sondées.
- **L'import de l'IHM ne fait pas le même travail que `BancImport`** : l'assistant ne recopie pas les
  bruts par défaut (ADR 0036). Il transforme et persiste, d'où 13 à 14 s contre 20 s, et il garde
  20,0 Gio, les séquences seules. Avec la carte, un tir occupe 39 Go au plus haut.

**Ce que ces chiffres ne mesurent pas** :

- Le disque seul. La source vient d'être écrite et tient presque entière dans le cache du système, et
  l'import n'attend pas que ses écritures aient atteint le disque : à la fin du second lancement,
  1176 Mo de pages restaient à écrire, sur 40 Gio produits.
- Une vraie carte SD, et un poste à huit cœurs ou à disque plus lent : le premier poste n'a pas tiré
  20 Gio.
- Dans l'IHM : le moment où tombent les trois écarts de l'import à 20 Gio, et l'application lancée
  hors des tests, où un rendu GPU peut donner autre chose. Le processus porte TestFX, l'agent
  ByteBuddy et celui de JaCoCo.
- La réactivation d'une nuit, qu'aucun banc ne mesure (#6174).

**Le relancer.** La JVM de test reçoit sa ligne d'arguments du pom : `-Djava.io.tmpdir`, l'échelle et
`-Xlog:gc` y entrent par la variable `JAVA_TOOL_OPTIONS`, et le banc écrit sous `perf.ihm.racine`.

```bash
JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$HOME/banc-20go/tmp -Dglass.gtk.uiScale=1 \
  -Xlog:gc:file=$HOME/banc-20go/gc-%p.log" \
  ./mvnw test -Precette-filmee -Dsurefire.groups=banc-ihm -Dsurefire.excludedGroups=api-live \
  -Dtest=BancIhmO3Test -Dperf.ihm.racine=$HOME/banc-20go/ihm -Dperf.ihm.nuits=1 -Dperf.ihm.wav=5593
```

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

Relevés du 2026-10-12, chacun avec sa machine, nommée plus haut : un premier poste pour tout, un
second pour les 20 Gio. La campagne est portée par #105, qui regroupe #26 (O5) et #27 (O3). Chaque
critère a son relevé, et chaque seuil non tenu son issue.

- **O5, couche données** : sélection des 4031 observations de 11 à 19 ms à chaud (seuil 100 ms) ; tri
  et filtre des 1000 passages de 10 à 12 ms à chaud (seuil 200 ms). Index `#28` en place et verrouillé
  par un test CI. **Dans l'IHM** : tri, filtre et clic tenus (au plus 66 ms) ; à 150 carrés, aucun écran ne
  s'ouvre sous 50 ms, et Mes sites croît avec les carrés (#6172).
- **O3** : nuit standard de 5,62 Gio en 40 à 49 s ; 9 Gio en 84 à 87 s ; occupation après GC de 81
  à 85 Mo, la nuit standard passant sous `-Xmx128m`. **Dans l'IHM** : pas d'écart de plus de 200 ms pendant l'import ; des écarts de 203 à 758 ms à
  l'ouverture des écrans après import (#6171) ; un heap après GC qui grandit de nuit en nuit dans le
  banc (#6170). **20 Gio, sur le second poste** : import en 20 à 21 s, 108 à 115 Mo après GC ; dans
  l'IHM, un écart de 465 à 472 ms entre deux images pendant l'import, là où les nuits de 300 WAV
  tiennent le seuil à échelle d'écran égale (#6175).
- Outillage réutilisable en **non-régression** : `GenerateurJeuDeDonnees`, `BancMesure`, `BancImport`,
  et le banc de l'IHM (`BancIhmO5Test`, `BancIhmO3Test`), lancé à la main.
