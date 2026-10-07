---
type: adr
title: "Un clip dont le verdict est au bas de sa page finit calé sur ce bas"
status: stable
article: A5
chantier: "#5870, lot 9 du sous-chantier #5644"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/recette/GesteVisibleBasDePageTest.java"
verification_note: "trois cas : le défaut d origine reproduit, sans lequel le suivant passerait sur un banc où rien ne bouge ; l aide qui cale la page au pixel ; le refus d une cible qui n est pas au bas. Mutation sans le calage au maximum : rouge sur le deuxième. Rien ne tient qu un scénario dont le verdict est au bas de sa page emploie l aide : c est la comparaison de deux tournages qui le montre"
relations:
  complete: ["4274-on-compare-la-derniere-image-pas-le-chemin"]
  amendee_par: ["6069-le-dernier-geste-d-un-clip-pose-une-position"]
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# Un clip dont le verdict est au bas de sa page finit calé sur ce bas

!!! warning "Ce qui fait foi aujourd'hui"
    **Amendée le 2026-10-06** par l'[ADR 6069](6069-le-dernier-geste-d-un-clip-pose-une-position.md) : `amenerDansLeCadre` n'est plus le geste qui **finit** un clip dont le verdict est au milieu de sa page. Sa condition d'arrêt est vraie à plusieurs positions, et un clip y a gagné une seconde fin. Le calage au bas, lui, tient toujours.

## Contexte

L'[ADR 4274](4274-on-compare-la-derniere-image-pas-le-chemin.md) compare la dernière image de deux
tournages, parce que c'est le seul instant où ils sont comparables sans dépendre de leur cadence. Cela
suppose que cette image soit la même d'un tournage à l'autre.

La première comparaison des clips de la plateforme de test a rendu le clip de `S4-47` à **20 %**
d'écart entre deux tournages du même commit. Le contenu était le même, la page n'était pas défilée au
même endroit.

La cause a été reproduite par un banc. `amenerDansLeCadre` place sa cible par un quotient calculé sur
la hauteur du contenu à cet instant, et JavaFX garde ensuite le décalage en **pixels**. Une carte qui
grandit après avoir été amenée laisse donc la page en deçà de son nouveau bas. Le scénario amenait sa
carte avant qu'elle ait reçu son état. Douze pixels suffisaient, et cinq tournages sur six étaient
tombés du même côté.

## Décision

**Un verdict qui est le dernier élément de sa page se montre par `GesteVisible.allerAuBasDeLaPage`,
appelé une fois son état arrivé.**

L'aide cale tous les panneaux de défilement de la cible sur leur maximum, puis vérifie que la cible
est dans le cadre. Le bas d'une page ne dépend pas de l'instant où un nœud a grandi.

**Elle refuse une cible qui n'est pas au bas.** Employée ailleurs, elle mettrait le verdict hors du
cadre en ayant l'air de l'y amener, et le clip finirait sur autre chose. Le refus dit que le geste ne
vaut que pour le dernier élément d'une page.

**Elle refuse aussi une cible qui ne descend d'aucun panneau de défilement** (#5982). Sans page à
caler, « tous les panneaux sont au bas » se disait d'une liste vide, et l'aide concluait dès que la
cible était dans le cadre. Ses huit appels ont été relus à cette occasion : aucun n'était dans ce cas.

`amenerDansLeCadre` n'est pas modifié : vingt-cinq scénarios l'emploient, et il reste le bon geste pour
une cible au milieu d'une page, ou pour garder une carte à l'image pendant qu'elle change.

## Ce qui a été écarté

**Corriger `amenerDansLeCadre`** pour qu'il suive une cible qui grandit. Il faudrait qu'il sache quand
elle a fini de grandir, ce que seul le scénario sait.

**Attendre plus longtemps avant la dernière image.** Le défaut n'est pas une course qu'un délai
gagne : la page reste où elle est, aussi longtemps qu'on attende.

**Se fier à la mesure.** Quatre tournages n'avaient pas vu le second mode. Ce qui établit le remède est
la cause, reproduite, et non l'absence du défaut sur quelques paires.

## Conséquences

Trois scénarios connectés finissent ainsi : le lancement, l'import et l'actualisation. Sur quinze
paires, aucun ne montre plus le mode à 20 %.

Un clip qui finit au bas de sa page ne montre pas le haut. Un changement dans le fil d'étapes de
l'écran de lot ne se voit donc pas dans ces clips, dont la dernière image est en bas : la comparaison
lit la première et la dernière image, pas le chemin.

Sept autres clips ont deux fins, pour des causes voisines, et n'ont pas encore reçu de remède
([ADR 5911](5911-un-clip-a-deux-fins-n-a-pas-de-plancher.md)).

**Mise à jour du 5 octobre 2026.** Le premier de ces sept a reçu ce remède-ci, et pour la raison que
cette page a mesurée : le clip du menu de ligne de l'import n'amenait pas du tout son compte rendu,
et sa page retombait où la recomposition la laissait - deux fois sur trois en bas, la troisième sur le
haut de l'assistant, verdict hors du cadre. `allerAuBasDeLaPage` l'a calée (#5952). Six restent.
