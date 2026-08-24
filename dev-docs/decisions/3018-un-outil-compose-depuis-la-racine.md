---
type: adr
title: "Un outil compose son injecteur depuis la racine, jamais à la main"
status: stable
article: A20
chantier: "#3018, finit #2669 et #333"
decided_at: 2026-07-31
verification: certaine
enforced_by:
  - "CliquetInjecteurALaMainTest#la_dette_ne_peut_que_retrecir"
verified:
  - by: machine:ci
    at: 2026-07-31
---

# Un outil compose son injecteur depuis la racine, jamais à la main

## Contexte

Un outil - capture d'écran, banc de mesure, graine de données - a besoin d'un injecteur Guice. Chacun
énumérait jusqu'ici les modules dont il croyait avoir besoin.

Le même défaut a été diagnostiqué **trois fois** :

- **#333** (clos le 2026-06-27) : `CaptureEcrans` échouait sur `RechercheGlobale was not bound`, parce
  que son injecteur ignorait `RechercheModule`. Le remède prescrit, mot pour mot : « utiliser le **même
  assemblage que l'application** ». Il a été appliqué **à ce fichier-là** ;
- **#2669** : quatre écrans photographiés **sans leur ligne Campagne**, partis dans la documentation
  sans que rien ne rougisse ;
- **#3018** : seize outils au total.

Trois diagnostics, un fichier corrigé. La règle existait, mais elle était écrite dans le commentaire
d'un correctif - pas dans une décision qu'on puisse retrouver.

## Ce qui rend le défaut invisible

Un injecteur amputé et une **fonctionnalité désactivée** produisent le **même écran**. Le contrôleur
masque sa surface : c'est exactement ce qu'on lui demande quand le flag est coupé (EPIC #1057).

Une capture amputée n'a donc pas l'air cassée. Elle a l'air d'une capture d'un produit configuré
autrement. Aucune exception, aucun test rouge, une image plausible.

> C'est la raison pour laquelle cette règle ne peut pas tenir par la vigilance, et pourquoi il a fallu
> un cliquet pour la faire tenir.

## Décision

1. Un outil compose depuis **`RacineInjecteur.modules()`**, la même source que l'application.
2. Il s'adapte par **`Modules.override(racine).with(…)`**, jamais en retirant de la liste.
3. Les surcharges légitimes sont celles qui rendent l'exécution **déterministe ou observable**
   (exécuteurs synchrones, horloge figée), et celle qui **est le sujet** de la capture.
4. Le mot « partiel » disparaît des doc-commentaires. Il y était écrit **quinze fois et justifié zéro
   fois** : la seule justification jamais rédigée est celle de #333, et elle conclut l'inverse.

## Ce que la décision n'autorise pas

Composer depuis la racine puis **substituer un service entier** pour fabriquer les données annule tout
le bénéfice : l'outil compose le produit complet et n'en montre rien.

Les deux aperçus de la Synthèse remplaçaient `ServiceSynthese` par une sous-classe à lignes fixes, sur
une source **nulle**. Ce que leur migration a fait apparaître :

- **deux états d'écran que le produit ne peut pas produire** - une Barbastelle marquée « (indicatif) »
  alors que le référentiel lui donne une déclinaison nationale d'été fiable ; une Sauterelle verte dite
  « hors référentiel » alors qu'elle y a une déclinaison Corse/été ;
- **un contexte à trois voix contradictoires** - carré `640380` (Nouvelle-Aquitaine), seuils affichant
  « region Corse », site nommé « Étang de Biguglia » - contradiction indolore tant que le service
  bouchonné **ignorait le carré qu'on lui passait**.

Une substitution reste acceptable quand elle est **étroite** et qu'elle **est le sujet** de l'aperçu :
c'est le cas du référentiel vide dans `CaptureSyntheseSansReferentiel`. Elle appelle alors souvent une
couture côté production - ici un constructeur qui fait du référentiel un **collaborateur** au lieu d'une
dépendance cachée, pour que l'état « référentiel absent » soit atteignable autrement qu'en retirant une
ressource du jar.

## Conséquences

- **Une capture dépend désormais des feature-flags.** C'est le but : une capture doit montrer le produit
  tel qu'il est **configuré**. Un flag qui change change les images, et ce changement se **regarde**.
- **Coût borné, et mesuré** : 61,7 ms par composition (16,5 ms de découverte `ServiceLoader`, 45,3 ms
  de création, 29 modules). 42 outils dans `capture-screenshots.sh`, soit un majorant de **2,6 s** sur
  un job mesuré à **304 s** - 0,85 %, quarante fois sous la variance du runner (±100 s). Le majorant
  se déduit sans connaître l'ancienne valeur : un injecteur partiel ne coûte pas moins que zéro.
- **Le cliquet reste**, liste vide et **sans aucune exclusion** : son rôle passe de compter une dette à
  empêcher qu'elle renaisse ([ADR 2867](2867-une-dette-se-tient-par-un-cliquet.md)).

## Alternatives écartées

- **Corriger au cas par cas**, ce qu'a fait #333. Trois diagnostics successifs ont montré que le
  correctif ne voyage pas tout seul jusqu'aux fichiers voisins.
- **Exclure les outils sans persistance.** C'était l'hypothèse du palier 3 pour les deux `ModuleDemo`,
  qui n'ouvrent aucune base et fabriquaient leurs données en mémoire. Écartée : ce sont précisément eux
  qui cachaient le plus, et une exclusion aurait laissé leurs deux états impossibles dans la
  documentation. [ADR 2951](2951-une-exclusion-nomme-son-repreneur.md) demandait d'ailleurs de nommer
  leur repreneur - il n'y en avait aucun.
