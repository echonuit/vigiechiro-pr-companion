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
| [`perf.outils.BancReactivation`](../../src/main/java/fr/univ_amu/iut/perf/outils/BancReactivation.java) | Banc de la **réactivation** (#6174) : fabrique et importe une nuit, l'archive, puis la réactive depuis ses bruts par le **vrai** `ServiceReactivationPassage`, voie `bruts` ou `hydratation`. Mesure le temps, les temporaires de régénération sous `java.io.tmpdir`, la mémoire du poste et le heap après GC. |
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
- **Le banc de l'IHM se tire à l'échelle 1, et il le vérifie lui-même** (#6177). Il dessine en
  rendu logiciel : sur un écran que le système double, chaque image coûte quatre fois plus de pixels,
  et l'écart entre deux images va du simple au triple pendant un import, davantage en navigation
  (mesuré le 2026-10-12, « Le test de charge à 20 Gio »). Le banc lit l'échelle de rendu de sa
  fenêtre une fois affichée, l'écrit en tête de chaque relevé (sortie et `releve.tsv`) avec celle que
  l'écran demande et les réglages `glass.platform`, `prism.order` et `glass.gtk.uiScale`, et s'arrête
  avant toute mesure si elle n'est pas 1. La même ligne écrit `dessin=`, le pipeline que Prism a
  réellement pris : le banc dessine en logiciel par défaut, et en rendu GPU par
  `-Drecette.prism.order=es2` sur la ligne de Maven (ADR 6171). Sur un écran dense, l'échelle se force à 1 par
  `-Dglass.gtk.uiScale=1`, posé par `JAVA_TOOL_OPTIONS` (voir « Le relancer »). Mesurer à l'échelle de
  l'écran reste possible par `-Dperf.ihm.echelle=ecran`, et le relevé écrit alors cette dérogation à
  côté de ses chiffres.

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
| Mes sites | 41,9 · 41,4 · 54,1 ms | 346,8 · 360,7 · 339,0 ms | < 50 ms, sans croître | **non tenu** ; ne croît plus depuis #6172, voir « Mes sites ne croît plus avec les carrés » |
| vue multisite | 352,2 · 75,8 · 303,5 ms | 174,1 · 170,8 · 151,3 ms | < 50 ms | **non tenu** (#6207) |
| écran des sons | 270,0 · 251,7 · 229,5 ms | 106,3 · 113,5 · 119,5 ms | < 50 ms | **non tenu** (#6207) |

| O3, quatre nuits distinctes de 300 WAV de 5 s, un seul processus | Nuit 1 | Nuit 2 | Nuit 3 | Nuit 4 |
|---|---|---|---|---|
| durée de l'import, du clic au compte rendu | 1,4 s | 1,0 s | 1,0 s | 1,1 s |
| écart max entre deux images pendant l'import | 151 ms | 98 ms | 76 ms | 87 ms |
| écart max en navigation après l'import | 758 ms | 224 ms | 255 ms | 244 ms |
| écarts de plus de 200 ms en navigation (neuf ouvertures) | 5 | 4 | 4 | 5 |
| heap après GC, harnais compris (50 Mo avant la nuit 1, voir #6170) | 148 Mo | 208 Mo | 265 Mo | 322 Mo |

**Lectures** :

- **Pendant l'import, aucun écart ne dépasse 200 ms** : tenu, sur des nuits de 300 WAV. L'import tourne hors
  du fil JavaFX. À 20 Gio, sur un second poste, le seuil ne tenait pas le 2026-10-12 ; il tient depuis
  que l'import laisse des cœurs à l'interface : « L'import ne gèle plus le fil JavaFX » (#6175).
- **En navigation après l'import, le seuil n'est pas tenu** (#6171) : les écarts tombent à l'ouverture
  des écrans, à chaque nuit, et pas seulement à la première ouverture. Ils ne grandissent pas avec le
  nombre de nuits en base. Ce relevé est en rendu logiciel : à chaud et en rendu GPU, le seuil tient
  (« L'ouverture d'un écran après un import », plus bas, et ADR 6171).
- **Le heap après GC grandissait de 57 à 98 Mo par nuit** dans ce tir. Ce chiffre mesurait surtout le
  harnais et des caches que la JVM rend sous pression, pas l'application : la cause est établie, et le
  palier tenu, dans « Le heap de nuit en nuit » plus bas (#6170).
- La durée d'import n'est pas le chiffre O3 de ce banc : c'est celui de `BancImport`, plus haut.

**Ce que ces chiffres ne mesurent pas** : la latence d'entrée (souris et clavier contournés), le dessin
de l'image et son affichage par le compositeur, un rendu GPU, une vraie carte SD et une autre machine.
L'échelle de ce tir n'a pas été lue dans le processus : le banc ne la relevait pas encore (#6177). Le
poste était très probablement à l'échelle 1, à 96 points par pouce, ce que l'on déduit de ses réglages
d'affichage relus le jour même (un écran de 1920 × 1080 annoncé à 96 points par pouce, `Xft.dpi` à 96),
et le second poste, tiré à l'échelle 1, rend presque les mêmes chiffres. Les 20 Go ont leur
sous-section.

**Le relancer** (jamais en CI : l'étiquette `banc-ihm` est exclue par défaut) :

```bash
./mvnw test -Precette-filmee -Dsurefire.groups=banc-ihm -Dsurefire.excludedGroups=api-live \
  '-Dtest=BancIhmO5Test,BancIhmO3Test' -Dperf.ihm.racine=$HOME/banc-ihm
```

Volumes : `-Dperf.ihm.observations` (4031), `-Dperf.ihm.passages` (1000), `-Dperf.ihm.nuits` (4),
`-Dperf.ihm.wav` (300 WAV par nuit, environ 1,1 Gio gardé par nuit importée : les séquences seules,
l'assistant ne recopiant pas les bruts par défaut). Le relevé s'ajoute à `releve.tsv` sous
`perf.ihm.racine`, chaque banc ouvrant sa part sur une ligne `échelle`. Sur un écran dont l'échelle
n'est pas 1, le banc s'arrête dès la fenêtre affichée et dit quoi poser :
`JAVA_TOOL_OPTIONS="-Dglass.gtk.uiScale=1"` devant la commande, ou `-Dperf.ihm.echelle=ecran` pour
mesurer à l'échelle de l'écran. Le heap de `BancIhmO3Test` se relève sous
`-XX:SoftRefLRUPolicyMSPerMB=0`, posé de la même façon : la section suivante dit pourquoi.

### Le heap de nuit en nuit (#6170)

Mesuré le 2026-10-13 sur le premier poste, en-tête imprimé par le banc :

```text
machine         : 11th Gen Intel(R) Core(TM) i5-1145G7 @ 2.60GHz · 8 cœurs · RAM 30.4 Gio
exécution       : 25.0.3+9-LTS · Linux 7.0.0-38-generic · heap max 7788 Mo
échelle         : rendu 1.00 × 1.00 · écran 1.00 × 1.00 · glass.platform=Headless · prism.order=sw
```

`BancIhmO3Test`, quatre nuits de 300 WAV de 5 s, en Headless, sur `main` à `f65822f95b` plus la
branche de #6170. Les deux tirs ne diffèrent que par la correction de la fiche du carré.

| Heap après GC complet, sous `-XX:SoftRefLRUPolicyMSPerMB=0` | Avant | Nuit 1 | Nuit 2 | Nuit 3 | Nuit 4 |
|---|---|---|---|---|---|
| fiche du carré non corrigée | 54 Mo | 90 Mo | 93 Mo | 96 Mo | 99 Mo |
| fiche du carré corrigée | 54 Mo | 89 Mo | 89 Mo | 93 Mo | 90 Mo |

Le seuil retenu, moins de 10 Mo gagnés par nuit de la nuit 2 à la nuit 4, est tenu : les écarts sont
de 0, 4 et -3 Mo. La dérive de 57 Mo par nuit du premier relevé avait trois causes. Chacune a été nommée par le
chemin qui la retenait jusqu'aux racines du ramasse-miettes, lu dans un vidage du heap, puis vue
disparaître par une mesure :

- **Le harnais.** TestFX garde dans un registre statique (`FxToolkitContext`, `FiredEvents`) tout
  événement tiré sur la fenêtre, pour toute la durée du test. Chaque `fire()` du banc y laissait son
  bouton, donc l'assistant d'import entier et la fiche d'où il s'ouvrait : une copie par nuit. Le banc
  vide désormais ce registre avant chaque relevé, et le vérifie.
- **Des caches que la JVM rend sous pression.** JavaFX garde en références douces les tampons de ses
  effets, de la taille de la fenêtre. Un GC complet ne les rend pas tant que le heap est loin d'être
  plein, et le banc les comptait. Ce sont eux qui faisaient suivre la dérive à l'échelle de l'écran :
  à l'échelle 2, chaque tampon est quatre fois plus gros. Sous `-XX:SoftRefLRUPolicyMSPerMB=0`, la JVM
  les rend à chaque GC. Ce n'est pas une fuite : sans ce réglage, la même mémoire revient dès que le
  heap se remplit.
- **Le produit.** La fiche d'un carré écoutait le jeton (#4219) par un écouteur fort posé sur un
  singleton : chaque fiche ouverte restait vivante jusqu'à la fermeture de l'application, environ 3 Mo
  chacune. L'écouteur est maintenant tenu par la fiche et inscrit faible, et
  `SiteDetailEcouteDuJetonViewTest` garde les deux moitiés : la fiche quittée est rendue, la fiche
  ouverte suit encore la connexion après un GC.

**Ce que ce relevé ne mesure pas** : l'affichage réel. Le porteur a choisi de ne pas solliciter le poste
pour un tir à l'écran, puisque le Headless à l'échelle 1 écrite par le banc tient le critère. Il ne
mesure pas non plus l'application lancée hors des tests : le chemin jusqu'aux racines, pris dans le
banc, a suffi à départager le produit du harnais.

**Le relancer** :

```bash
JAVA_TOOL_OPTIONS="-XX:SoftRefLRUPolicyMSPerMB=0" env -u DISPLAY ./mvnw test \
  -Dsurefire.groups=banc-ihm -Dsurefire.excludedGroups=api-live -Dtest=BancIhmO3Test \
  -Dperf.ihm.racine=$HOME/banc-ihm
```

### Mes sites ne croît plus avec les carrés (#6172)

Mesuré le 2026-10-13 sur le premier poste, en Headless, en-tête imprimé par le banc :

```text
machine         : 11th Gen Intel(R) Core(TM) i5-1145G7 @ 2.60GHz · 8 cœurs · RAM 30.4 Gio
exécution       : 25.0.3+9-LTS · Linux 7.0.0-38-generic · heap max 7788 Mo
échelle         : rendu 1.00 × 1.00 · écran 1.00 × 1.00 · glass.platform=Headless · prism.order=sw · glass.gtk.uiScale=non posée
```

`BancIhmO5Test#o5_topologie`, un geste jeté puis trois relevés, aux mêmes réglages avant et après. Avant :
`main` à `4a66871177`, charge du poste sur 1 minute de 1,43 au départ. Après : la même base plus la liste
virtualisée de #6172, charge de 1,50 au départ et de 4,20 à la fin, la compilation du tir comprise.

| Ouverture de Mes sites | 15 carrés de 2 points | 150 carrés de 2 points |
|---|---|---|
| avant | 99,5 · 89,1 · 78,5 ms (premier : 153,5) | 487,2 · 448,1 · 427,5 ms (premier : 618,3) |
| après | 143,4 · 85,5 · 81,9 ms (premier : 260,2) | 81,8 · 69,0 · 59,4 ms (premier : 86,1) |

Témoin à vide : de 15,1 à 16,0 ms dans les deux tirs.

**Lectures** :

- **La croissance a disparu.** Avant, l'ouverture était environ cinq fois plus lente à 150 carrés qu'à
  15. Après, le plus haut relevé à 150 carrés vaut 57 % du plus haut à 15 : le critère de #6172, au plus
  25 % au-dessus, est tenu.
- **Ce qui croissait.** Une sonde locale, non versée, a décomposé l'ouverture avant le remède. À 150
  carrés, la lecture en base restait plate (de 10 à 20 ms) et le FXML coûtait 3 à 4 ms. Ce qui suivait
  les carrés était la construction des cartes, de 40 à 64 ms, puis surtout leur CSS et leur mise en
  page, de 257 à 335 ms. L'écran bâtissait une carte par site, quand six ou sept tiennent dans la
  fenêtre. La liste virtualisée ne construit que celles-là, et `MesSitesTopologieViewTest` le garde en
  comptant des cartes, jamais des millisecondes (ADR 4251).
- **Le seuil de 50 ms n'est pas tenu** en Headless, ni à 15 ni à 150 carrés. Il est publié tel quel.
  En Headless, Mes sites à 15 carrés s'ouvre plus lentement que sur l'affichage réel du 2026-10-12
  (de 41 à 54 ms) : les deux modes ne se comparent pas entre eux.

**Le relancer** :

```bash
env -u DISPLAY ./mvnw test -Dsurefire.groups=banc-ihm -Dsurefire.excludedGroups=api-live \
  '-Dtest=BancIhmO5Test#o5_topologie' -Dperf.ihm.racine=$HOME/banc-ihm
```

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
| écart max en navigation après l'import | 1310 ms | 377 ms | 200 ms | **non tenu** en rendu logiciel (#6171) |
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
  (de 99 à 114 ms), comme le premier (de 76 à 151 ms). La lecture du jour, « c'est la taille de la
  nuit qui fait passer le seuil », a été démentie le lendemain par la datation des écarts : voir
  « L'import ne gèle plus le fil JavaFX ».
- **L'échelle de l'écran change tout le reste.** À l'échelle par défaut, la sonde voit de 14 à
  24 images par seconde pendant l'import, contre 23 à 41 à l'échelle 1 ; les nuits de 300 WAV
  dépassent le seuil (de 224 à 338 ms) et la navigation gèle de 1,2 à 1,3 s. Ces chiffres-là
  mesurent le dessin en rendu logiciel, pas l'application : ils ont motivé la règle du protocole.
- **En navigation, le seuil n'est pas tenu à l'échelle 1 non plus** (#6171) : de 197 à 384 ms, l'ordre
  de grandeur du premier poste, et 377 ms après la nuit de 20 Gio. L'écart ne suit pas la taille de la
  nuit. C'est le dessin logiciel du banc : voir « L'ouverture d'un écran après un import ».
- **Le heap après GC, à l'échelle 1, est celui du premier poste à quelques Mo près** (#6170) : de 57 à
  59 Mo de plus par nuit. Deux faits nouveaux : la nuit de 20 Gio laisse 160 Mo, 13 de plus qu'une
  nuit de 300 WAV pour dix-neuf fois plus de séquences ; et l'échelle par défaut porte le gain à 98 Mo
  par nuit. Ce qui grandit suit donc la surface dessinée plus que les données : c'étaient des écrans
  retenus par TestFX et des tampons de la taille de la fenêtre gardés par JavaFX, voir « Le heap de
  nuit en nuit » plus haut.
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
- La réactivation d'une nuit : elle a son banc et son relevé plus bas (« La réactivation d'une nuit », #6174).

**Le relancer.** La JVM de test reçoit sa ligne d'arguments du pom : `-Djava.io.tmpdir`, l'échelle et
`-Xlog:gc` y entrent par la variable `JAVA_TOOL_OPTIONS`, et le banc écrit sous `perf.ihm.racine`.

```bash
JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$HOME/banc-20go/tmp -Dglass.gtk.uiScale=1 \
  -Xlog:gc:file=$HOME/banc-20go/gc-%p.log" \
  ./mvnw test -Precette-filmee -Dsurefire.groups=banc-ihm -Dsurefire.excludedGroups=api-live \
  -Dtest=BancIhmO3Test -Dperf.ihm.racine=$HOME/banc-20go/ihm -Dperf.ihm.nuits=1 -Dperf.ihm.wav=5593
```

### L'import ne gèle plus le fil JavaFX (#6175)

Mesuré le 2026-10-13 sur le second poste, celui de la section précédente : Core Ultra 7 255H, 16 cœurs
de trois familles (six à 5,1 GHz, huit à 4,4, deux à 2,5), 30,3 Gio, JDK 25.0.3, Linux 7.0.0.
`BancIhmO3Test` sur l'affichage réel (XWayland, profil `recette-filmee`, rendu logiciel), à l'échelle 1
écrite par le banc (`rendu 1.00 × 1.00`, `glass.gtk.uiScale=1`), fenêtre de 1280 × 860, une nuit par
tir sauf mention.

**Le banc date ses écarts.** La sonde garde l'instant de chaque écart de plus de 100 ms, et le banc
marque six jalons lus sur les nœuds de l'assistant : le clic, le plan établi (la table de suivi reçoit
ses lignes), le premier et le dernier fichier terminé à l'écran, le passage à `TERMINE` et le compte
rendu. Chaque écart se situe parmi eux. Le tir s'ouvre sur son témoin, un blocage de 300 ms posé à un
instant connu : sur les trente et un tirs de la journée il est daté de 3,2 à 15,2 ms avant son début
et de 0,2 à 0,6 ms après sa fin, soit à moins d'une image à 60 par seconde. Les écouteurs du banc
coûtent de 0,2 à 8,4 ms au plan établi, écrits dans chaque relevé.

**La cause : le processeur pris par la transformation.** `main` à `09b3b2447` plus la datation, où
l'import mène de front autant de transformations que le poste a de cœurs. Le nombre de traitements
de front est émulé par celui des fils porteurs (`-Djdk.virtualThreadScheduler.parallelism` et
`maxPoolSize`), en tirs alternés, de 10 h 58 à 11 h 17 :

| Traitements de front | 1572 WAV, écart max | 5593 WAV (20 Gio), écart max | Écarts de plus de 100 ms dans la transformation, par tir | Durée à 20 Gio |
|---|---|---|---|---|
| 16, le défaut | 178 · 273 · 296 · 442 ms | 103 · 189 · 225 · 258 ms | de 1 à 14 | 12,9 à 13,2 s |
| 14 | 276 · 387 ms | 93 · 318 ms | de 0 à 11 | 12,8 et 13,1 s |
| 12 | 155 · 199,5 ms | 90 · 145 ms | de 0 à 11 | 13,2 et 13,5 s |
| 10 | 125 · 134 ms | 104 · 144 ms | de 0 à 3 | 13,7 et 13,9 s |
| 8 | 65 · 150 · 185 · 255 ms | 74 · 103 ms | 0 aux six tirs | 14,1 et 14,4 s |

« Dans la transformation » : entre le premier et le dernier fichier terminé. Au défaut, la sonde voit
de 16 à 42 images par seconde ; à 8, de 44 à 59.

**Le remède : l'import mène de front la moitié des cœurs** (`ParallelismeDeFond`, ADR 6175). Sur le
code qui le porte, sans option d'émulation, de 13 h 29 à 13 h 32 :

| Nuit | Écart max pendant l'import | Images | Durée | Écarts de plus de 100 ms dans la transformation | Seuil de 200 ms |
|---|---|---|---|---|---|
| 5593 WAV (20,00 Gio), tir 1 | 76 ms | 906 | 15,0 s | 0 | tenu |
| 5593 WAV (20,00 Gio), tir 2 | 134 ms | 891 | 15,1 s | 0 | tenu |
| 1572 WAV, tir 1 | 244 ms | 185 | 4,0 s | 0 | non tenu, par l'image du plan établi (#6197) |
| 1572 WAV, tir 2 | 105 ms | 212 | 3,8 s | 0 | tenu |
| 300 WAV, quatre nuits d'un même processus | 170 · 106 · 52 · 70 ms | 47 · 47 · 44 · 42 | 1,0 · 0,9 · 0,8 · 0,8 s | 0 | tenu |

Charge du poste sur 1 minute au départ des tirs : de 1,5 à 7,4, chaque tir héritant du précédent ;
60 °C au plus haut sur la journée. Aucune fenêtre ni question pendant les trente et un tirs.

`BancImport` à 20 Gio sur le même code, deux lancements, sans interface : 19,93 et 20,00 s, 1028 et
1024 Mo/s, crête de 753 et 724 Mo, 77 et 87 Mo après GC. La veille, à seize traitements de front :
20,75 et 19,73 s, crête de 1404 et 2396 Mo, 108 et 115 Mo après GC.

**Lectures** :

- **Le gel venait de la transformation, pas de la taille de la nuit.** Les écarts de plus de 200 ms
  tombent entre le premier et le dernier fichier terminé, et disparaissent quand la transformation
  laisse des cœurs. La nuit de 1572 WAV gelait plus que celle de 20 Gio.
- **Le seuil tient à 20 Gio** : 76 et 134 ms, 59 à 60 images par seconde.
- **Le coût se paie dans l'IHM seulement** : 15,0 s au lieu de 13,1 pour 20 Gio, soit 15 % de plus,
  là où l'émulation en annonçait 9. `BancImport`, qui ne dessine rien, ne ralentit pas, et sa crête
  mémoire baisse de moitié au moins.
- **La dispersion est du même ordre que l'effet.** Au défaut, la même nuit de 1572 WAV rend 178 ms à
  un tir et 442 au suivant ; les 465 et 472 ms relevés la veille à 20 Gio ne se sont pas reproduits
  (de 103 à 258 ms sur quatre tirs), sans que le code de l'import ait changé. D'où le choix d'une
  borne à marge : à 12, un tir rend 199,5 ms.
- **Une autre image coûte, à tout parallélisme** (#6197) : celle qui suit le plan établi, quand la
  table de suivi s'affiche, dure de 100 à 255 ms. Ce sont les 255 ms de la ligne à 8 et les 244 ms du
  remède à 1572 WAV. L'image de fin, qui porte `TERMINE` et le compte rendu, reste entre 100 et
  185 ms.
- **Trois lectures écartées** : les relais vers le fil JavaFX (bornée à 8, la transformation émet
  encore 91 % des événements par seconde, sans plus d'écart) ; un point de sûreté de la JVM (aucun de
  plus de 30 ms pendant un import, dans un journal `-Xlog:gc*,safepoint` horodaté) ; l'échauffement.

**Ce que ces chiffres ne mesurent pas** :

- Un autre poste. La règle n'est mesurée dans l'IHM que sur seize cœurs de trois familles. Sur le
  premier poste (i5-1145G7, 8 cœurs dont 4 physiques), `BancImport` a chiffré le même jour, sans
  interface, la nuit standard à 8 puis 4 fils porteurs : 37,3 s contre 41,3 et 43,1 s, un tir à froid
  écarté.
- Le tableau de la cause émule la borne par les fils porteurs, pas par le sémaphore du produit : les
  durées diffèrent (14,3 s émulés à 8, 15,0 s mesurés).
- Un import avec copie des bruts dans l'IHM : l'assistant ne les recopie pas par défaut.
- L'application lancée hors des tests, où le dessin passe par la carte graphique et charge moins le
  processeur.

**Le relancer** : la commande de la section précédente, avec `-Dperf.ihm.wav` à la taille voulue. Pour
recouper les écarts datés avec la JVM, ajouter `-Xlog:gc*,safepoint:file=<fichier>:time,uptime` à
`JAVA_TOOL_OPTIONS` : le relevé écrit l'heure du clic.

### L'ouverture d'un écran après un import (#6171)

Mesuré le 2026-10-13 sur le second poste (Core Ultra 7 255H, 16 cœurs, 30,3 Gio, carte Intel,
JDK 25.0.3, Linux 7.0.0), `main` à `c6056ba35` plus la branche de #6171, donc après #6175 et avant
#6191. `BancIhmO3Test` sur l'affichage réel (XWayland, profil `recette-filmee`), à l'échelle 1 écrite
par le banc, fenêtre de 1280 × 860, quatre nuits de 300 WAV par tir. Après chaque nuit, trois tours de
Mes sites, de la vue multisite et de l'écran des sons : neuf ouvertures.

**Le banc date chaque ouverture** : le début du geste, puis le premier pulse après mise en page où
l'écran montre ses données, et chaque écart de plus de 100 ms se situe parmi ces jalons. La durée
d'ouverture est celle du fil JavaFX, du geste à l'écran prêt. Le relevé écrit le dessin que Prism a
réellement pris : `dessin=SWPipeline` en logiciel, `dessin=ES2Pipeline` en rendu GPU.

Huit tirs, quatre par dessin : une première série où le rendu GPU était posé par une modification
locale du pom, puis une seconde, alternée, avec le réglage livré (`-Drecette.prism.order=es2`), de
14 h 16 à 14 h 18. Charge du poste sur 1 minute au départ des tirs : de 1,6 à 6,2.

| À chaud, après les nuits 2 à 4 (vingt-sept ouvertures par tir) | Rendu logiciel | Rendu GPU | Seuil |
|---|---|---|---|
| écart max entre deux images | 182 à 240 ms | 52 à 122 ms | 200 ms |
| écarts de plus de 200 ms, par tir | 2 · 0 · 2 · 2 | aucun | aucun |
| écarts de plus de 100 ms, par tir | 20 · 20 · 21 · 19 | 0 · 0 · 1 · 0 | |
| ouverture de Mes sites | 26 à 98 ms | 26 à 77 ms | |
| ouverture de la vue multisite | 68 à 233 ms | 61 à 212 ms | |
| ouverture de l'écran des sons | 59 à 225 ms | 56 à 158 ms | |

| À froid, première ouverture de chaque écran dans le processus | Rendu logiciel | Rendu GPU |
|---|---|---|
| Mes sites | 90 · 92 · 122 · 142 ms | 95 · 120 · 123 · 129 ms |
| vue multisite | 259 · 291 · 412 · 465 ms | 269 · 287 · 438 · 465 ms |
| écran des sons | 375 · 409 · 460 · 464 ms | 352 · 402 · 417 · 527 ms |
| écart max entre deux images, après la nuit 1 | 277 · 282 · 344 · 346 ms | 260 · 271 · 285 · 398 ms |

**Lectures** :

- **À chaud, le gel est le dessin logiciel du banc.** Le fil JavaFX fait le même travail dans les deux
  dessins. En logiciel, presque chaque ouverture de la vue multisite et de l'écran des sons est suivie
  d'un écart de 176 à 193 ms, daté sur le jalon « prêt » ou juste après lui. En rendu GPU il n'existe
  pas.
- **Le seuil se juge à chaud et en rendu GPU, et il est tenu** (ADR 6171) : 122 ms au plus sur cent
  huit ouvertures. Le rendu logiciel reste publié : c'est ce que voit un poste sans accélération
  graphique.
- **La première ouverture d'un écran se publie à part.** Elle coûte dans les deux dessins : c'est le
  chargement d'un écran jamais ouvert, sur le fil JavaFX.
- **Le seuil de 50 ms d'O5 n'est tenu par aucun des deux écrans** (#6207), à chaud et sur un seul
  carré : c'est la durée d'ouverture elle-même, pas le nombre de carrés ni le dessin.
- En rendu GPU, le heap après GC des mêmes tirs est de 28 à 29 Mo avant la première nuit et de 63 à
  65 Mo après chacune ; en logiciel, de 51 à 52 Mo et de 107 à 110 Mo.

**Ce que ces chiffres ne mesurent pas** :

- La part de chaque travail dans une ouverture. Le journal des pulses de JavaFX
  (`-Djavafx.pulseLogger=true`) fait tomber le processus, sur une `NullPointerException` de
  `PrintLogger` : le tir qui le portait n'a rien rendu.
- Ce qui coûte au dessin logiciel. Les journaux de Prism montrent des shaders d'ombre portée : une
  piste, non mesurée.
- Un autre poste, et une autre carte. Le premier poste n'a de relevés qu'en rendu logiciel.

**Le relancer** : la commande du test de charge, avec `-Dperf.ihm.nuits=4 -Dperf.ihm.wav=300`, et
`-Drecette.prism.order=es2` pour le rendu GPU. Le relevé doit alors porter `dessin=ES2Pipeline` : s'il
porte `SWPipeline`, le poste a refusé la carte et les chiffres sont ceux du logiciel.

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

### La réactivation d'une nuit (`BancReactivation`, #6174)

La réactivation rend écoutable une nuit archivée en régénérant ses séquences depuis ses bruts, par la
même transformation que l'import. Elle régénère chaque brut dans un dossier temporaire, rebranche les
tranches vérifiées, puis efface ce dossier. Le code a deux voies, et elles ne régénèrent pas de la
même façon :

- **bruts** : la nuit a gardé l'inventaire de ses originaux, et la réactivation en régénère **un à la
  fois** (`ReactivationDepuisBruts`) ;
- **hydratation** : la nuit est reconstruite, ou rapatriée de Vigie-Chiro, et ses originaux ne sont
  que des placeholders. Les bruts sont alors régénérés **en parallèle**, autant que de cœurs
  (`HydratationDepuisBruts`).

**Le constat** : le 2026-09-15, la réactivation d'une nuit a figé ce poste jusqu'à ce que le processus
s'arrête faute de mémoire. Cette nuit n'est pas rejouée ici : les nuits sont fabriquées par le banc.

**Le lancer.** Comme `BancImport`, dans une JVM à soi. Le banc écrit sous `vigiechiro.workspace`, qu'il
réinitialise, et le dossier temporaire est celui de la JVM :

```bash
./mvnw -q org.codehaus.mojo:exec-maven-plugin:exec \
  -Dexec.executable="$JAVA_HOME/bin/java" -Dexec.classpathScope=runtime \
  -Dexec.args="--enable-native-access=ALL-UNNAMED -Xmx4g -Dvigiechiro.workspace=$HOME/vigiechiro-bench-reactivation \
               -Dperf.reactivation.fichiers=8 -Dperf.reactivation.secondes=1200 \
               -Dperf.reactivation.voie=hydratation -cp %classpath fr.univ_amu.iut.perf.outils.BancReactivation"
```

Dimensionnement : `-Dperf.reactivation.fichiers=N` **ou** `-Dperf.reactivation.go=<Gio>`, plus
`-Dperf.reactivation.secondes` (déf. 5.0) et `-Dperf.reactivation.frequenceHz` (déf. 384000).
`-Dperf.reactivation.voie=bruts|hydratation` choisit la voie ; le banc obtient l'hydratation en
réduisant les originaux de la nuit à des placeholders, comme les pose une reconstruction. Prévoir trois
fois la taille source sur le disque de l'espace de travail, comme pour l'import.

> **Avant le remède de #6174, sur un poste dont `/tmp` est un tmpfs, la voie `hydratation` écrivait en
> mémoire vive.** Pour rejouer cet état, tirer le banc dans une portée à mémoire bornée, pour que la
> saturation arrête le tir et non le poste, comme ce relevé l'a fait :
> `systemd-run --user --scope -p MemoryMax=12G -p MemorySwapMax=0 -- java …`. La borne y fixe aussi le
> heap par défaut, d'où le `-Xmx4g` écrit. Le poste était tenu éveillé par
> `systemd-inhibit --what=sleep:idle`.

#### Relevé du 2026-10-12

Poste : celui de la section « Relevés du 2026-10-12 » (i5-1145G7, 8 cœurs, 30,4 Gio, SSD NVMe, JDK
25.0.3, Linux 7.0.0). Code : `main` à `80d09a0cee`, plus le banc. Heap : `-Xmx4g`. Workspace sur la
partition `ext4` de `/home`. `/tmp` est un tmpfs de 15,2 Gio. Un lancement par ligne, tirés l'un après
l'autre sans build ni autre banc. La charge du poste sur une minute allait de 0,8 à 8,7 au départ des
tirs, d'autres sessions travaillant sur le poste : les temps se lisent avec cette réserve, les chiffres
de mémoire beaucoup moins.

« Temporaires » est l'occupation la plus haute des dossiers de régénération (`vc-regen-*`,
`vc-hydrate-*`) sous `java.io.tmpdir`. « Poste » est `MemTotal - MemAvailable`, et « Shmem » la
mémoire partagée, où se compte un tmpfs, au départ puis au plus haut. Tout est échantillonné toutes les
200 ms. « Après GC » suit un `System.gc()` demandé à la fin.

| Tir | Nuit | Voie | `java.io.tmpdir` | Réactivation | Temporaires | Poste | Shmem | Heap au plus haut | Après GC |
|---|---|---|---|---:|---:|---:|---:|---:|---:|
| A | 1572 WAV de 5 s, 5,62 Gio | bruts | tmpfs | 52,0 s | 3 Mo | 12843 → 14895 Mo | 1175 → 1797 Mo | 721 Mo | 15 Mo |
| B | 1572 WAV de 5 s, 5,62 Gio | hydratation | tmpfs | 42,4 s | 25 Mo | 12999 → 13997 Mo | 1198 → 1229 Mo | 1143 Mo | 15 Mo |
| C | 8 WAV de 1200 s, 6,87 Gio | hydratation | tmpfs | 29,9 s | **7031 Mo** | 12719 → **20703 Mo** | 1155 → **8190 Mo** | 1303 Mo | 15 Mo |
| D | 8 WAV de 1200 s, 6,87 Gio | hydratation | `/home` | 66,0 s | 7031 Mo | 12657 → 13662 Mo | 1088 → 1103 Mo | 1210 Mo | 15 Mo |
| E | 8 WAV de 1200 s, 6,87 Gio | bruts | tmpfs | 38,4 s | 878 Mo | 13772 → 15368 Mo | 1090 → 2044 Mo | 583 Mo | 14 Mo |

Toutes les séquences sont revenues (1572 ou 1920 réactivées, aucune manquante), et aucun dossier de
régénération n'est resté après un tir. La voie que rend le rapport est `BRUTS` pour les cinq :
une hydratation réussie se rapporte ainsi.

Un premier lancement de D a été **écarté** : le poste s'est mis en veille de 20:18 à 20:37 pendant le
tir, et la JVM a été gelée. Le D du tableau est le second lancement, poste tenu éveillé.

**Lectures** :

- **La cause est le dossier temporaire du système.** Les tirs C et D ne diffèrent que par `java.io.tmpdir` :
  avec un tmpfs, la mémoire du poste monte de 7,8 Gio et la mémoire partagée de 6,9 Gio ; sur
  `/home`, de 1,0 Gio et de 15 Mo. Le heap, lui, ne garde rien : 15 Mo après GC dans les deux tirs.
  La JVM ne voit pas cette mémoire, c'est le poste qui la paie.
- **L'hydratation tient la nuit entière à la fois.** 7031 Mo, c'est les huit bruts de C régénérés en
  même temps (huit fils, huit bruts de 879 Mo). La voie bruts n'en tient qu'un : 878 Mo dans E.
- **Avec des bruts de 5 s, rien ne se voit** (A et B : 3 et 25 Mo). Il faut des bruts longs, que
  produit un enregistreur qui ne découpe pas, pour que la régénération pèse.
- **Ce que cela dit du 2026-09-15**, sans le prouver : sur une nuit reconstruite ou rapatriée, huit
  bruts d'un ou deux Gio régénérés à la fois dans un tmpfs, ajoutés au heap par défaut (7788 Mo) et à ce
  que le poste porte déjà, dépassent les 30 Gio du poste. Sur le second poste, à seize cœurs, il en
  faut deux fois moins.

**Ce que ces chiffres ne mesurent pas** :

- La nuit du 2026-09-15 elle-même, ni l'état du code de ce jour-là, où la transformation lisait encore
  chaque brut entier dans le heap (#104).
- Une vraie nuit reconstruite : le banc en imite l'inventaire, il ne passe pas par la reconstruction
  depuis la plateforme.
- La réactivation dans l'application : le banc appelle le service, sans la modale ni ses barres.
- Une saturation menée à son terme : la borne de 12 Go et `-Xmx4g` la préviennent par construction.

#### Après le remède : la régénération sous l'espace de travail

Depuis #6174, les temporaires de régénération naissent sous la racine de l'espace de travail, comme
l'extraction d'une archive, et plus dans `java.io.tmpdir`. Le banc relève les deux endroits. Mêmes
poste, réglages et nuit que C et E, `/tmp` toujours en tmpfs, poste tenu éveillé, le 2026-10-12 sur la
branche du remède.

| Tir | Voie | Sous `java.io.tmpdir` | Sous l'espace de travail | Réactivation | Poste | Shmem | Après GC |
|---|---|---:|---:|---:|---:|---:|---:|
| C, avant | hydratation | 7031 Mo | (non relevé) | 29,9 s | 12719 → 20703 Mo | 1155 → 8190 Mo | 15 Mo |
| C, après | hydratation | 0 Mo | 7031 Mo | 59,4 s | 12576 → 13635 Mo | 1131 → 1141 Mo | 15 Mo |
| E, avant | bruts | 878 Mo | (non relevé) | 38,4 s | 13772 → 15368 Mo | 1090 → 2044 Mo | 14 Mo |
| E, après | bruts | 0 Mo | 878 Mo | 44,2 s | 12792 → 12839 Mo | 1134 → 1135 Mo | 15 Mo |

La régénération occupe toujours autant, mais sur le disque : la mémoire du poste ne monte plus que d'un
Gio en hydratation, ce que le tir D faisait déjà avec le temporaire sur `/home`. Écrire sur disque
coûte du temps, d'où une réactivation plus lente, comme D (66,0 s) l'était déjà ; la charge du poste au
départ des tirs (3,0 et 7,6) n'en permet pas une lecture plus fine.

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

Lancer l'application sous `-XX:SoftRefLRUPolicyMSPerMB=0` : sans ce réglage, un GC complet garde ce
que seules des références douces tiennent, dont les tampons des effets JavaFX, et le palier paraît
dériver alors que rien ne fuit (#6170). Le banc de l'IHM (`BancIhmO3Test`) fait ce relevé sans
manipulation : voir « Le heap de nuit en nuit ».

`BancImport` reste l'outil pour la **crête d'une seule nuit** (pic mémoire pendant l'import).

## Bilan du cluster perf

Relevés du 2026-10-12 et du 2026-10-13, chacun avec sa machine, nommée plus haut : un premier poste pour tout, un
second pour les 20 Gio. La campagne est portée par #105, qui regroupe #26 (O5) et #27 (O3). Chaque
critère a son relevé, et chaque seuil non tenu son issue.

- **O5, couche données** : sélection des 4031 observations de 11 à 19 ms à chaud (seuil 100 ms) ; tri
  et filtre des 1000 passages de 10 à 12 ms à chaud (seuil 200 ms). Index `#28` en place et verrouillé
  par un test CI. **Dans l'IHM** : tri, filtre et clic tenus (au plus 66 ms) ; à 150 carrés, ni la vue
  multisite ni l'écran des sons ne s'ouvrent sous 50 ms (#6207) ; Mes sites croissait avec les carrés, et ne croît plus depuis sa liste
  virtualisée (#6172).
- **O3** : nuit standard de 5,62 Gio en 40 à 49 s ; 9 Gio en 84 à 87 s ; occupation après GC de 81
  à 85 Mo, la nuit standard passant sous `-Xmx128m`. **Dans l'IHM** : pas d'écart de plus de 200 ms pendant l'import ; des écarts de 203 à 758 ms à
  l'ouverture des écrans après import, en rendu logiciel, et de 122 ms au plus à chaud en rendu GPU,
  où le seuil se juge (#6171, ADR 6171) ; un heap après GC qui tient son palier sur quatre nuits
  (de 89 à 93 Mo), une fois mis à part le harnais TestFX et les caches doux de JavaFX ; une fiche de
  carré que le produit retenait est corrigée (#6170). **20 Gio, sur le second poste** : import en 20 à 21 s, 108 à 115 Mo après GC ; dans
  l'IHM, l'import gelait le fil jusqu'à 472 ms parce qu'il menait de front autant de transformations
  que de cœurs ; à la moitié des cœurs, 76 et 134 ms à 20 Gio, pour 15 % de durée en plus (#6175).
  La première image de la table de suivi coûte encore de 100 à 255 ms (#6197).
- **Réactivation** (#6174) : sur une nuit hydratée de bruts de 20 min, les temporaires de
  régénération occupaient 7031 Mo à la fois dans `java.io.tmpdir`, de la mémoire vive quand c'est un
  tmpfs. Ils naissent désormais sous l'espace de travail, et la mémoire du poste ne monte plus que d'un
  Gio ; le heap après GC reste à 15 Mo.
- Outillage réutilisable en **non-régression** : `GenerateurJeuDeDonnees`, `BancMesure`, `BancImport`,
  `BancReactivation`, et le banc de l'IHM (`BancIhmO5Test`, `BancIhmO3Test`), lancé à la main.
