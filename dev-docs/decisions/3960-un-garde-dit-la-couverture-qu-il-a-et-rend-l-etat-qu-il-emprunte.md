---
type: adr
title: "Un garde dit la couverture qu'il a, et rend l'état qu'il emprunte"
status: stable
article: A3
chantier: "#3960, clôture du lot #3900"
decided_at: 2026-08-18
verification: certaine
enforced_by:
  - "FilArianeElisionTest#aucun_segment_rendu_n_est_coupe"
verified:
  - by: machine:ci
    at: 2026-08-18
relations:
  prolonge: ["3645", "2748"]
---

# Un garde dit la couverture qu'il a, et rend l'état qu'il emprunte

## Contexte

La clôture du lot #3900 a joué les douze passes sur un delta de 68 commits. Elle a trouvé **quatre**
gardes qui annonçaient plus qu'ils ne tenaient. Quatre mécanismes différents, un seul motif.

| Garde | Ce qu'il annonçait | Ce qu'il faisait |
|---|---|---|
| `FilArianeElisionTest` | « la CI rejoue le même fichier à 900 » | tournait **toujours** à 1100 |
| `BudgetHorizontalChromeTest` | `@DisplayName("à 1100 comme à 900")` | idem |
| `cli-surface.bats` | un inventaire de 41 commandes | `[ "${n}" -ge 20 ]`, un plancher à moins de la moitié |
| `scripts/adr/2843` | « un garde qui ment selon la machine ne vaut rien » | plantait sur le premier sous-dossier venu |

Les deux premiers lisaient `System.getProperty("chrome.largeur", "1100")`, propriété que **rien** ne
posait : ni le `pom.xml`, ni un atelier, ni un script. **900 est exactement la largeur où l'élision
sert** - celle que le produit impose comme minimum.

Le quatrième est le plus instructif : le fichier écrit, trois lignes au-dessus de la zone fautive, la
règle qu'il enfreint. Une zone à glob total (`*`) rouvrait le trou que l'ancrage des zones fermait.

## Décision

**1. Un garde s'exécute dans tous les cas qu'il nomme, et son nom ne nomme que ceux-là.**

Concrètement, un banc qui vise deux largeurs **boucle** sur les deux, avec les constantes du produit
(`TailleOuverture.LARGEUR_VOULUE`, `LARGEUR_MINIMALE`) plutôt que des littéraux.

**Une boucle plutôt qu'une seconde exécution en intégration.** Un garde qui ne rougirait qu'en CI
ne protège pas celui qui écrit le code, et c'est précisément le régime dont ces deux-là sortaient.

**2. La boucle vérifie d'abord qu'elle a obtenu ce qu'elle demandait.**

`assertThat(scene.getWidth()).isEqualTo(largeur, within(1.0))` avant chaque vérification. Une fenêtre
rabattue par la plateforme rendrait la boucle muette **sur le cas même qu'elle vise**, et le garde
annoncerait deux largeurs en n'en éprouvant qu'une : le défaut d'origine, sous une forme neuve.

**3. Un banc rend l'état partagé tel qu'il l'a trouvé.**

TestFX réutilise la **fenêtre primaire** dans un fork unique. Un `setWidth(900)` laissé en place
rétrécit toutes les classes qui passent ensuite - et seulement dans l'ordre où elles passent après.

## Ce que cette dernière règle a coûté à apprendre

Le job `ordre-alternatif` (`-Dsurefire.forkCount=1 -Dsurefire.runOrder=reversealphabetical`) a rougi
sur `SonsValidationViewTest.puces_filtres_alignees_horizontalement` : une puce passait à la ligne,
ordonnée 38 au lieu de 0.

**Trois hypothèses sont tombées à la mesure** avant la bonne :

1. la police embarquée chargée par un test de plus - faux, ce test monte déjà une scène habillée ;
2. une incompatibilité entre les deux classes dans une même JVM - faux, elles passent ensemble en
   local dans l'ordre par défaut ;
3. une branche périmée de huit commits - le rebasage a réglé `build`, pas `ordre-alternatif`.

Ce qui a tranché n'est aucun raisonnement : c'est d'avoir **relancé la suite avec les propriétés
exactes du job**. Rouge en local, de façon déterministe, en trois classes.

Et j'ai affirmé entre-temps que le rouge venait de `main`, sur la foi de deux `build` rouges qui
portaient en réalité sur **des tests différents**. Deux échecs simultanés ne font pas une cause
commune.

## Conséquences

- **Le job `ordre-alternatif` est un instrument, pas une formalité.** Il a trouvé une fuite qu'aucune
  relecture n'aurait vue, et son commentaire dit déjà pourquoi ses deux propriétés sont exactes.
- **Un compteur d'inventaire se verrouille** (`-eq`), il ne se plancherise pas (`-ge`). Ajouter une
  commande, c'est ajuster le chiffre : ce geste **est** le prix de l'inventaire.
- **Un garde n'ouvre que des fichiers.** `prose()` filtre sur `is_file()` : un répertoire n'est pas de
  la prose, et le faire lire faisait planter au lieu de juger.

## Ce que la décision NE fait pas

Elle ne prétend pas qu'un garde bouclant sur ses cas soit **suffisant**. Pendant ce même chantier,
j'ai ajouté à `FilArianeElisionTest` un test affirmant que le fil « tient dans la place qu'on lui
laisse » : la mutation qui désactive l'élision ne l'a pas fait rougir, et je n'ai pas su lui en
trouver une.

**Il a été retiré.** Un garde dont on ne sait pas montrer le rouge est une affirmation de plus, pas
une garantie - et c'est exactement ce que les quatre gardes ci-dessus étaient devenus.
