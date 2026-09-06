---
type: adr
title: "Une liste qu'un garde confronte est un inventaire"
status: stable
article: A3
chantier: "#5294 (le coût des ateliers), lot #5373"
decided_at: 2026-09-06
verification: certaine
enforced_by:
  - "scripts/methode/gardes-java-declares.py"
ratchet: 0
verified:
  - by: machine:ci
    at: 2026-09-06
generated:
  by: "process:assistance-par-agents"
---

# Une liste qu'un garde confronte est un inventaire

## Le contexte

`scripts/batterie.py` répond à « quels contrôles ce diff engage-t-il ? », et sa population s'arrêtait
au **Python**. Or ce dépôt teste sa documentation comme du code : des classes Java lisent des `.md` et
refusent quand ils dérivent.

La demande #5356 a rougi sur `build` pour cette raison, sur un `enforced_by` qui portait un argument
là où l'invariant cherche un fichier. **La porte ne pouvait pas le voir**, et le coût de ce contrôle
est pourtant de **2,4 s** pour vingt et un invariants, contre huit minutes de `build` entier. C'est
exactement le profil qu'une batterie locale cherche : bon marché, et il rattrape souvent.

## La tension

L'[ADR 3450] refuse « une liste de classes sensibles » : « une liste ne voit que ce qu'on y a mis et
se périme ». Déclarer cinq classes Java dans un dictionnaire est précisément cette liste.

Et l'inférence ne peut pas la remplacer. La `loupe-5175` a mesuré que l'évaluation symbolique ne
résout le parcours que de treize gardes sur quarante et un, et écrit qu'« aucune loupe ne les
couvrira ».

## La décision

**La liste est écrite, et un garde la confronte à une population dérivée.**

`gardes-java-declares.py` relève toute classe `*Test.java` qui **construit** un chemin vers de la
prose, et refuse celle qui n'est pas déclarée. La population vient de l'arbre ; la déclaration lui
est opposée.

**Une liste qu'un garde confronte n'est plus une liste, c'est un inventaire.** La différence n'est
pas rhétorique : ce que l'ADR 3450 refuse, c'est une liste que *rien ne tient*, et dont la dérive est
donc silencieuse. Une sixième classe qui lirait un `.md` sans être déclarée fait rougir.

## Construire un chemin, ce n'est pas le citer

Le garde cherche `Path.of`, `Paths.get` ou `new File` dont l'argument désigne `docs/`, `dev-docs/`,
`brief/`, un `mkdocs*.yml`, un `.md` racine ou `.github/workflows`.

Une mention en commentaire ne compte pas. C'est la règle que
`verifie_inventaires_ci.porte_l_option` porte déjà : « le commentaire cite la chose, il ne la fait
pas ». Sans elle, `DecisionsRespecteesTest` serait compté à tort - il lit `pom.xml`, `jpackage/` et
`flatpak/`, pas de la prose.

## Ce qu'il ne voit pas, et qui est assumé

Une classe qui passerait par un **helper** pour lire la prose. `EnregistreurDeFilm` est dans ce cas :
il n'est pas une classe de test, surefire ne le lance pas, et les classes qui l'emploient sont déjà
déclarées.

La faille est réelle et connue. Elle se refermera si on la constate, pas par précaution.

## Pas de repli « on lance tout », et c'est délibéré

Les gardes Python non déclarés sont **lancés** par défaut ([ADR 5340]) : le défaut penche du côté
coûteux. Ici non, et pour une raison : une classe Java non déclarée n'est pas invisible, elle fait
**rougir**. Le silence est fermé par le garde plutôt que par le repli, et lancer toute la suite Java
par précaution coûterait huit minutes là où le garde coûte une seconde.

[ADR 3450]: 3450-une-propriete-de-fuseau-se-tient-en-rejouant-pas-en-relisant.md
[ADR 5340]: 5340-un-garde-qui-ne-declare-pas-ses-chemins-est-lance.md
