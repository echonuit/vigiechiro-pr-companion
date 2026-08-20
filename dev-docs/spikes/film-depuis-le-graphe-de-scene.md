# Filmer la recette depuis le graphe de scène

Prototype de l'extension JUnit 5 évoquée en discussion, à comparer à `lance-test-filme.sh` sur une
classe réelle. Sept classes, 589 lignes hors commentaires contre 944 de bash, mais ce n'est pas le
volume qui compte : c'est que quatre familles de défauts n'ont plus où se produire.

## Le renversement

Le script filme le bureau X. De dehors, « une fenêtre est-elle à l'écran » se devine par la
luminance moyenne de l'image, et « quand ce test a-t-il joué » se calcule en recalant l'horloge du
journal sur celle du film. Les deux heuristiques marchent, elles sont bien gardées, et elles ont
coûté #3696, #3707, #3774, #3788, #3835 et #4053.

Ici, `CameraDeScene` photographie le graphe de scène par `Scene.snapshot`, et l'extension borne
l'enregistrement sur l'exécution du test. Les deux questions cessent d'être des mesures : ce sont
des faits que la JVM connaît.

## Ce que cela retire du dispositif

| Dans le script | Devient |
|---|---|
| Seuil de luminance à 20, recalibré à chaque changement de gestionnaire de fenêtres | `Window.getWindows()` : une fenêtre est visible ou non |
| `t0 = arrêt - durée`, l'instant relevé avant `wait`, le tube nommé et le `printf q >&3` | Un fichier par test : plus qu'une horloge, donc plus de recalage |
| `couper_par_luminance`, `couverture_des_plages`, `plages_du_journal`, `reperes.tsv` | Sans objet |
| `carton_de_titre` : `drawtext`, `fold -s -w 54`, résolution DejaVu, `fc-match`, `concat -c copy`, contrôle des dimensions du montage | `CartonDeTitre` : `Graphics2D`, police logique, repli mesuré en pixels, poussé dans le même encodeur |
| Le cas d'auto-test à tesseract | `CartonDeTitreTest` : l'invariant porte sur des chaînes |
| `libelle_du_cas` en awk + sed | `LibelleDesCas`, avec huit cas unitaires |
| Les cinq préconditions, `verifier_tout`, `--verifier`, la relance sans `WAYLAND_DISPLAY` | Rien : on filme dans le mode Monocle headless où les tests tournent déjà |
| Xvfb, openbox, xdotool, `glass.platform=gtk`, `testfx.robot=awt`, `java.awt.headless=false` | Rien de tout cela n'est requis |

**Ce qui reste requis, et que le tableau ne dit pas** : `ffmpeg`. `Encodeur.VersFfmpeg` le lance en
sous-processus et le nourrit en rawvideo, on garde donc libx264 et les réglages du script. Ce spike
supprime la dépendance à un **bureau X**, pas la dépendance à un encodeur. L'interface `Encodeur`
est le point de substitution si l'on veut un jour encoder en Java.

En échange, ce qui disparaît n'est pas seulement de la complexité : le banc devient exécutable
**sous Windows et macOS**, où il ne l'est pas aujourd'hui. La session `s10` est précisément celle du
poste Windows.

Ce qui reste du script : `--planche` garde son sens, mais devient `-Dtest=A,B,C` sans autre
apparat, puisqu'un fichier par test se produit de lui-même. `CorrespondanceRecetteTest` reste le
bon endroit pour dériver la liste.

## Contenu

| Fichier | Rôle |
|---|---|
| `EnregistreurDeFilm.java` | L'extension JUnit 5. Ne fait rien sans `-Drecette.film` |
| `CameraDeScene.java` | `AnimationTimer` qui compose les fenêtres visibles, CENTRÉES sur la toile |
| `Enregistrement.java` | Une séance par test : file bornée, fil d'écriture, bilan |
| `Encodeur.java` | Interface, et l'implémentation qui nourrit le ffmpeg du banc en rawvideo |
| `CartonDeTitre.java` | Le carton, dessiné au format du film |
| `LibelleDesCas.java` | Le libellé d'un cas, lu dans les sessions |
| `CameraDeSceneTest`, `EncodeurTest` | Les gardes du placement et de la résolution de l'encodeur |
| `IndexDesCas.java` | L'index par cas, écrit en fin de session JUnit |
| `CasDeRecette.java` | Rappel de la forme supposée. À supprimer à l'intégration |

## Branchement

```java
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class})
class ConnexionModaleViewTest { ... }
```

L'ordre importe : `BeforeTestExecutionCallback` s'exécute après tous les `@BeforeEach`, donc après
le démarrage de l'application par TestFX. La caméra trouve une scène montée.

Le profil `recette-filmee` se réduit alors à une propriété :

```xml
<profile>
  <id>recette-filmee</id>
  <properties>
    <recette.film>true</recette.film>
  </properties>
</profile>
```

et le lancement à `./mvnw test -Precette-filmee -Dtest=ConnexionModaleViewTest`, sans script,
sans écran, sans gestionnaire de fenêtres.

Propriétés reconnues : `recette.film` (présence suffit), `recette.film.taille` (défaut
`1280x900`), `recette.film.dossier` (défaut `target/recette/clips`), `recette.sessions` (défaut
`dev-docs/recette/sessions`).

## Ce qui est éprouvé, et ce qui ne l'est pas

`CartonDeTitre` et `LibelleDesCas` ont été compilés et exécutés en headless : **13 tests,
20 assertions**, aucun échec, accents compris et sans fontconfig. Les deux moitiés ont été
**éprouvées par mutation** : neutraliser `LibelleDesCas` fait rougir 7 tests sur 13, désactiver le
repli de `CartonDeTitre` en fait rougir 1, et retirer la case à cocher du motif de puce en fait
rougir 1. `apercu-carton.png` est le rendu réel, à relire à l'œil.

### 1. Le débit : mesuré, et le compteur ne bouge pas

Seize clips produits, dix sur `ConnexionModaleViewTest` et six sur `ParcoursNavigationE2ETest` :
**zéro image perdue**. Le fil FX suit la cadence de 10 images par seconde.

Le surcoût en temps, lui, **n'est pas mesurable sur un poste chargé**. Trois exécutions de chaque
sur la même classe E2E :

| | avec film | sans film |
|---|---:|---:|
| 1 | 37,49 s | 26,46 s |
| 2 | 28,51 s | 25,13 s |
| 3 | 26,57 s | **38,04 s** |
| moyenne | 30,86 s | 29,88 s |

Le troisième run **sans** film est le plus lent de tous. L'écart des moyennes vaut +0,98 s pour une
étendue de 12,91 s : la marge est du même ordre que la variance de la machine. Un seul échantillon
aurait donné « +42 % », et c'est ce qu'il a donné avant qu'on répète.

La leçon vaut au-delà de ce spike : **comparer la marge à la variance, pas à zéro**.

### 2. La position des fenêtres : c'était un défaut, il est corrigé

Le clip d'un parcours montrait « ieChiro Companion » au lieu de « VigieChiro Companion », et
« gende » au lieu de « Légende ».

Mesuré au pixel : la scène fait **1100×720** et elle était dessinée à **x = -51**, perdant ses
51 premiers pixels tandis que 231 pixels de toile restaient vides à droite. `Window.getX()` situe
la fenêtre sur un écran virtuel Monocle étranger à la toile.

`CameraDeScene.decalage` **centre** désormais, sans lire aucune coordonnée. Sur le clip refait, le
contenu occupe x de 90 à 1189 et y de 90 à 810, marges de 90 et 89 pixels, titre entier.
`CameraDeSceneTest` garde le placement en cinq cas.

Ce qui rendait ce défaut dangereux est sa modestie : **un bord amputé de cinquante pixels se lit
comme une mise en page**, pas comme un défaut. Il n'aurait fait rougir aucun test.

### Ce qui reste à vérifier

Ce conteneur n'a pas de banc de référence pour les deux points suivants.

3. **Le pipeline logiciel.** Le rendu passe par Prism SW en headless. Quelques effets et mélanges
   diffèrent à la marge du rendu matériel. Sans conséquence pour un film de recette, à vérifier
   une fois si un test perceptif juge au pixel près.

4. **L'allocation.** `composer()` alloue une toile neuve et `snapshot` un `WritableImage` neuf à
   chaque image, soit de l'ordre de 45 Mo par seconde à 10 images/s en 1280x900. À surveiller en
   même temps que le débit.

   ⚠️ **Réutiliser un ou deux tampons ne marcherait pas**, et c'est un piège qui rend un film
   silencieusement faux plutôt qu'absent : la file a une profondeur de 60 et le fil scribe consomme
   en différé, donc jusqu'à 60 images attendent leur écriture. Écrire par-dessus l'une d'elles
   remplacerait une image passée par une image présente. La forme juste est un **petit bassin de
   tampons** que le scribe **rend** après écriture, le producteur comptant comme perdue une image
   pour laquelle aucun tampon n'est libre, exactement comme il compte déjà celles que la file
   refuse.

Deux limites assumées : le curseur n'est pas rendu (il se dessine à partir d'un `EventFilter` sur
`MOUSE_MOVED`, ce qui permet en échange de surligner le nœud touché), et les boîtes natives
`FileChooser` restent invisibles. Cette seconde limite ne coûte rien ici, puisque TestFX ne sait pas
les piloter non plus : si tes tests passent aujourd'hui, c'est qu'elles sont déjà derrière un port.
