# ADR 4134 - Un banc n'emprunte pas l'état partagé du harnais, il ouvre le sien

- **Statut** : Accepté - 2026-08-22
- **Chantier** : #4134, clôture de l'EPIC #4133
- **Prolonge** : [ADR 3960](3960-un-garde-dit-la-couverture-qu-il-a-et-rend-l-etat-qu-il-emprunte.md)
- **Vérification** : certaine - `AppTest#le_stage_partage_reste_ajustable`

## Contexte

TestFX réutilise **la même fenêtre primaire** pour toutes les classes d'un même fork. Une classe qui
la dimensionne, la déplace ou la ferme laisse cet état à toutes celles qui passent après elle - et
seulement à celles-là, dans l'ordre où la répartition des forks les a mises.

Le défaut est revenu **cinq fois** :

| Venue | Ce qui l'a signalé |
|---|---|
| #1940 | une modale qui cessait de grandir |
| #1967 | prédisait le retour : « rien ne l'empêche […] aucun test ne rougit si on la réécrit » |
| #3452 | `LotDepotConnecteViewTest`, noeud « invisible » |
| #4130 | `LotDepotConnecteViewTest`, le même noeud |
| #4145 | `AppTest`, une fenêtre restée au plancher |

Les trois dernières se sont signalées sur une classe qui n'y était pour rien, ce qui est la marque de
ce défaut : la cause et le symptôme n'ont aucun rapport, et le symptôme se déplace.

## Ce que l'ADR 3960 avait tranché, et pourquoi cela n'a pas suffi

Sa troisième règle disait : **un banc rend l'état partagé tel qu'il l'a trouvé**. Les deux bancs
concernés l'appliquaient, et le documentaient :

> ⚠️ On REND la fenêtre telle qu'on l'a trouvée. TestFX réutilise la **fenêtre primaire** dans un fork
> unique […] C'est ce que le job `ordre-alternatif` a attrapé - et il avait raison (#3960).

Ils reposaient la **largeur** d'entrée. Mesuré : cela ne rend pas la fenêtre. `setWidth` fait passer un
Stage en dimensionnement **explicite**, et reposer une valeur ne l'en fait pas sortir.

| geste | largeur | hauteur |
| --- | --- | --- |
| `setWidth(900)` / `setHeight(300)` | 900 | 300 |
| une scène plus petite est posée | 900 (ignorée) | 300 (ignorée) |
| `sizeToScene()` | 33 | 270 |
| une scène plus grande est posée | 369 (suivie) | 504 (suivie) |

**La valeur n'est pas la propriété.** Les classes suivantes n'ont pas rougi jusqu'ici parce que la
largeur rendue se trouvait être assez grande pour que leurs noeuds restent visibles. C'est de la
chance, et la chance n'est pas une garantie.

## Décision

**1. Un banc qui doit dimensionner, déplacer ou fermer une fenêtre ouvre la sienne.**

`new Stage()`, `initOwner(celle du harnais)`, et on la referme avec le banc. La fenêtre reçue se
**lit** ; elle ne s'écrit pas. Cela vaut aussi pour `close` : « Échap ferme la modale » ne doit pas
fermer la fenêtre de tout le monde.

**2. Deux gardes, parce qu'un seul ne peut pas couvrir la propriété.**

- **sur les sources** (`ConventionsDEcritureTest#aucun_stage_recu_n_est_fige`) : aucune classe de test
  ne fige un Stage qu'elle a **reçu** - paramètre de
  `start`, paramètre d'un utilitaire, ou champ qui en dérive. Il attrape la forme connue,
  `alias.setWidth(` ;
- **sur la propriété** (`AppTest#le_stage_partage_reste_ajustable`) : on pose une scène nettement plus
  haute sur le Stage du harnais, et la fenêtre
  doit suivre. Il attrape les autres chemins.

**3. Un scénario filmé demande sa taille à la mise en page.** Taille préférée sur la racine, puis
`sizeToScene()` après l'affichage (`recette.FenetreDuBanc`). C'est la voie que `Modales` emprunte
depuis #1940.

## Ce que cette décision a coûté à apprendre

⚠️ **`sizeToScene` défige.** C'est ce qui en fait le bon geste - et ce qui rend aveugle tout garde qui
l'appelle avant de mesurer. Le premier jet du garde de propriété le faisait : son mutant a **survécu**,
le garde défaisant le défaut avant de le chercher. `setScene` suffit, et c'est justement lui qui reste
sans effet sur une fenêtre figée.

⚠️ **Le plancher masque la mesure.** `App.start` pose 600 px de haut minimum (#3452). Deux scènes
choisies **sous** ce plancher laissent la fenêtre à 600 dans les deux cas, et le garde rougit sur du
code sain. La seconde scène doit franchir le plancher.

⚠️ **La classe qui garde était la cinquième fautive.** `ModalesTest` héberge le garde de #1940 depuis
le début, et prenait le Stage du harnais **comme modale** - qu'un de ses tests ferme. Après elle, une
scène de quarante lignes laissait la fenêtre au plancher, là où elle la portait à 720 quand la classe
tournait seule.

## Ce qui a été écarté

**Restaurer mieux, plutôt que ne pas emprunter.** `sizeToScene()` en sortie de banc rendrait bien la
propriété - c'est mesuré. Mais rien ne garantit qu'un `@AfterEach` s'exécute après un échec brutal, et
surtout : la liste des états à rendre n'est pas close. Position, plancher, plafond, plein écran,
redimensionnable - chacun s'ajoute au fur et à mesure qu'on s'en sert, et la restauration se met à
jour **après** le défaut. Une fenêtre à soi ne demande rien à personne.

**Ne garder que le garde de sources.** Il ne voit que la forme qu'on lui a apprise. Le cinquième cas
est passé par `close`, que ce garde ne cherchait pas.
