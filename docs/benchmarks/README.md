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
  -Dexec.args="-Dvigiechiro.workspace=/tmp/vigiechiro-bench -cp %classpath fr.univ_amu.iut.perf.outils.BancMesure"
```

Volumes paramétrables : `-Dperf.sites=...`, `-Dperf.passages=...`, `-Dperf.observations=...` (à ajouter
dans `exec.args`).

> ⚠️ **`perf.sites` est la dimension qu'on oublie.** Le jeu a longtemps semé **un** carré, quelle que soit
> la valeur de `perf.passages` : les relevés O5 étaient donc aveugles aux requêtes lancées **par site** ou
> **par point**, qui sont exactement celles qui coûtent à un observateur ayant beaucoup de carrés. Un
> test (`JeuDuBancTest`) refuse désormais que le jeu retombe à un seul carré.

## Lancer le banc d'import (O3 : tenue sur nuit volumineuse)

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

> **Machine de référence** : poste comparable à ceux de l'IUT, JDK 25 standard. Chiffres **mesurés**
> (pas des placeholders) ; relancer 2-3 fois et garder l'ordre de grandeur. La 1ʳᵉ utilisation du jour
> (JIT + cache disque froids) donne les valeurs « froid ».

!!! warning "Les relevés ci-dessous n'ont pas de machine nommée"
    Cette section a porté des mois durant un « préciser CPU / RAM / SSD si besoin » que personne n'a
    rempli, et le poste exact qui a produit ces chiffres n'a jamais été consigné : il est aujourd'hui
    irrécupérable. Les relevés gardent donc leur valeur d'**ordre de grandeur** et perdent celle de
    point de comparaison - on ne peut pas dire si une machine est plus lente que « la référence ».

    Un champ à remplir à la main reste vide : c'est **le banc** qui nomme désormais sa machine, et la
    ligne voyage avec la mesure. `BancImport` ouvre sur :

    ```text
    machine         : 11th Gen Intel(R) Core(TM) i5-1145G7 @ 2.60GHz · 8 cœurs · RAM 30.4 Gio
    exécution       : 25.0.3+9-LTS · Linux 6.17.0-41-generic · heap max 7788 Mo
    ```

    Le processeur et la mémoire se lisent sous `/proc` ; ailleurs la ligne écrit « non lu sur cette
    plateforme » plutôt que de laisser croire à une mesure. **Le prochain relevé complet remplace ce
    tableau et lui rend sa machine** : il suffira de coller cette en-tête au-dessus.

### O5 : couche données (`BancMesure`)

| Opération | Cible | Plan d'exécution (après #28) | Froid | Chaud (méd.) |
|---|---|---|---|---|
| Sélection ~4031 observations (`findByResults`) | < 100 ms | `SEARCH observation USING INDEX idx_obs_results` (#28, était `SCAN`) | **~25 ms** ✅ | ~8-13 ms |
| Tri/filtre ~1000 passages (multisite, verdict) | < 200 ms | `SEARCH passage USING INDEX sqlite_autoindex_passage_1 (point_id=?)` | **~18 ms** ✅ | ~7-9 ms |

Les deux opérations sont **largement sous les cibles** (facteur ~4 à ~10). Rappel #28 : sans
`idx_obs_results` la sélection faisait un `SCAN` (~75 ms froid sur cette machine) ; avec l'index,
~25 ms.

### O3 : import d'une nuit (`BancImport`, WAV de 2 s @ 384 kHz)

| Fichiers | Taille src | Temps total | copie (R9) | transfo (#12) | persist. (O7) | Débit | Mémoire crête | Séquences |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 100 | 146 Mo | 0,55 s | 0,33 s | 0,18 s | 0,04 s | ~184 f/s · 269 Mo/s | 172 Mo | 100 |
| 500 | 732 Mo | 2,38 s | 1,56 s | 0,75 s | 0,07 s | ~210 f/s · 308 Mo/s | 569 Mo | 500 |
| 1000 | 1,43 Gio | 3,97 s | 2,77 s | 1,09 s | 0,11 s | ~252 f/s · 369 Mo/s | 626 Mo | 1000 |

_Reproduire une ligne : `-Dperf.import.fichiers=<100|500|1000> -Dperf.import.secondes=2.0`
(fréquence par défaut 384000). La commande d'exemple plus haut utilise la durée par défaut de 5 s :
ajouter `-Dperf.import.secondes=2.0` pour retrouver exactement ces chiffres._

!!! note "La colonne « Séquences » ne vient pas de la même mesure que les autres"
    Les temps, débits et crêtes sont **datés** : ils valent pour la machine de référence, et les
    rejouer ailleurs donnera autre chose. Le nombre de séquences, lui, ne dépend pas de la machine :
    c'est `ceil(D / 5)` appliqué à des fichiers de 2 s, donc **une séquence par fichier**, partout.

    Cette colonne annonçait 400 / 2000 / 4000 - quatre séquences pour un fichier de 2 s, ce qui est
    `ceil(2 / 0,5)` : le découpage d'**avant #504**, qui tranchait à 5 s *au rythme de sortie* et
    produisait donc des tranches de 0,5 s réelles. Le correctif a changé la règle sans que ce tableau
    en soit averti. Vérifié en rejouant la première ligne : 100 fichiers → **100 séquences**.

**Lectures clés (O3)** :
- Le temps **croît ~linéairement** avec la taille (débit ~stable 180-250 fichiers/s) → tenue dans la
  durée confirmée. La **copie SD→workspace (I/O) domine** (~65-70 %), la transformation parallélisée
  (#12) ~25-30 %, la persistance est négligeable (~3 %).
- **Mémoire** : la crête plafonne (≈ 600-700 Mo pour ~1000-1500 fichiers), le découpage est borné aux
  cœurs (#12) ; elle croît surtout avec le **nombre de séquences** d'**une** nuit (résultats agrégés
  avant la transaction unique O7), puis est récupérée par le GC entre deux nuits → stable d'un import à
  l'autre.

!!! warning "600-700 Mo et 83 Mo décrivent la même chose"
    Les deux chiffres circulent, et ils ne se contredisent pas : ils ne mesurent pas la même
    grandeur. La **crête** de ce tableau est ce que la JVM a *occupé* sous un heap généreux ; le
    **live set** de #104 est ce qu'elle avait *vivant* au même instant.

    Sous `-Xmx2g`, G1 laisse s'accumuler le déchet transitoire - les `byte[]` PCM déjà morts - et ne
    déclenche qu'au seuil : la crête échantillonnée dit surtout où est ce seuil. Contraindre le heap
    déplace le seuil sans gêner le travail : la même nuit s'importe sous **`-Xmx128m`** avec un live
    set de **82-83 Mo**, au même débit (#104).

    Conséquence pratique : **la crête de ce tableau ne dimensionne pas la RAM nécessaire**. Elle dit
    ce que l'import prend quand on le laisse faire, pas ce dont il a besoin. C'est le second chiffre
    qu'il faut regarder pour savoir si une machine tient.

**Ordre de grandeur de référence** : une **vraie nuit** (1572 fichiers) s'importe en
**~6-8 s** (~200 fichiers/s), produit **2109 séquences**, avec une empreinte ~600-700 Mo. _(Le temps et
l'empreinte sont ceux de la machine de référence ; un poste plus modeste sera plus lent. Le nombre de
séquences, lui, est un **fait de la nuit** : `Car640380-2026-Pass2-Z1` porte 1572 bruts et 2109
transformés, et notre import les reproduit au bit près - c'est ce que #504 a vérifié.)_

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

- **O5 (#26)** : cibles tenues largement (sélection ~25 ms < 100 ms ; tri/filtre ~18 ms < 200 ms),
  index `#28` en place et verrouillé par un test CI.
- **O3 (#27)** : import linéaire et borné en mémoire ; nuit réelle ~6-8 s, crête ~600-700 Mo.
- Outillage réutilisable en **non-régression** : `GenerateurJeuDeDonnees`, `BancMesure`, `BancImport`.
