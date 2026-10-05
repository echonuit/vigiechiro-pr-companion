---
type: adr
title: "Un geste a un seul point d'action, et son résultat se lit près de lui"
status: stable
article: A23
heuristiques:
  - "nielsen-1"
  - "nielsen-4"
chantier: "#5676, lots 14 et 15 du chantier #5596 (#5676, #5682)"
decided_at: 2026-10-03
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/lot/view/LotDepotConnecteViewTest.java"
verification_note: "la classe compte les boutons de lancement après un dépôt complet, lit le titre de l étape dans les deux états, lit le résultat du lancement sous le bouton pour chaque issue, et constate le blocage d une analyse demandée, y compris à la réouverture. Elle ne juge pas la lisibilité : les aperçus `apercu-lot-lancement-*` se regardent"
relations:
  completee_par: ["5859-le-fil-d-etapes-suit-le-dernier-geste-du-depot"]
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# Un geste a un seul point d'action, et son résultat se lit près de lui

!!! warning "Ce qui fait foi aujourd'hui"
    **2026-10-05** : cette décision est **complétée** par
    [5859](5859-le-fil-d-etapes-suit-le-dernier-geste-du-depot.md). Elle donnait au titre et au bouton
    de la dernière étape le nom du geste, sans toucher au fil d'étapes, qui le nommait autrement et le
    disait fait. Le fil suit désormais le nom et l'état du bouton. Le reste fait foi.

## Contexte

Pendant le dépôt réel de #5597, la fin du dépôt a laissé l'utilisateur sans savoir quoi faire, puis
sans savoir ce qu'il avait fait. Deux boutons « Lancer la participation » coexistaient, l'un dans le
compte rendu du dépôt, l'autre à la dernière étape, dont le titre était resté « Marquer le passage
déposé ». Une fois cliqué, le bouton se grisait quelques secondes puis revenait à l'identique : le
résultat était parti dans le bandeau du haut de page. Le journal disait la participation planifiée,
et deux clics de plus ont reçu `400 Already PLANIFIE`.

## Décision

**1. Un seul bouton lance la participation : celui de la dernière étape** (choix du porteur, 3 octobre).
Le compte rendu du dépôt nomme cette étape sans offrir de bouton qui la double.

**2. Le titre de l'étape dit le geste que son bouton offre**, et suit le lien de participation quand
il change.

**3. Le résultat d'un lancement se dit sous le bouton qui l'a demandé**, avec un texte par issue :
accepté, déjà demandé, bloqué, refusé avec son motif, plateforme injoignable. Le bandeau ne le répète
pas.

**4. Une analyse demandée ne s'offre plus à être relancée.** Planifiée, en cours ou relancée par le
serveur, elle grise le bouton, et son explication renvoie à la carte du traitement.

**5. Cet état se lit dans le dernier relevé, jamais dans la réponse au clic.** Il survit ainsi à la
réouverture de l'écran, sans réseau.

## Ce qui a été écarté

**Garder le bouton du compte rendu, plus proche du regard à la fin du dépôt.** Il disparaît avec le
compte rendu ; celui de l'étape reste, y compris quand on rouvre l'écran le lendemain.

## Conséquences

Un compte rendu peut nommer la suite, il ne la porte pas. Et un état que l'application affiche après
un geste doit pouvoir se relire sans avoir fait ce geste.
