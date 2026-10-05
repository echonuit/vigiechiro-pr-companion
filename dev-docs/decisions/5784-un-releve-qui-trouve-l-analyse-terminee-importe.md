---
type: adr
title: "Un relevé qui trouve l'analyse terminée importe les observations, sans jamais remplacer ni sonder"
status: stable
article: A15
heuristiques:
  - "nielsen-1"
  - "nielsen-7"
chantier: "#5784, lot 24 du chantier #5596"
decided_at: 2026-10-03
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/lot/viewmodel/TraitementViewModelTest.java"
  - "src/test/java/fr/univ_amu/iut/cli/commande/EtatTraitementVigieChiroTest.java"
  - "src/test/java/fr/univ_amu/iut/lot/view/LotDepotConnecteViewTest.java"
verification_note: "les trois classes tiennent l import au relevé, l absence de réimport, l échec dit sans masquer l état, l absence d import à l ouverture, et la commande. Toutes passent par un port bouchonné. Le geste contre une plateforme qui répond « terminée » est joué à part, sur la plateforme de test, par `ScenarioConnecteActualisationTest` (#5836), qu aucune demande de fusion ne lance"
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# Un relevé qui trouve l'analyse terminée importe les observations, sans jamais remplacer ni sonder
## Contexte

Une fois la participation lancée, on suit l'analyse Tadarida sur la carte « Traitement Vigie-Chiro »
de l'écran de lot. Quand « Actualiser » rendait « Analyse terminée », la carte disait seulement que
les observations étaient prêtes à être importées. Pour les avoir, il fallait ouvrir « Sons &
validation » et demander l'import au menu. Le porteur : « ce n'est pas naturel de devoir rentrer dans
la vue observations et de demander le téléchargement par le menu hamburger ».

## Décision

**1. Le relevé qui rend l'analyse terminée importe, d'office**, dans la même tâche de fond. Le bouton
reste en attente jusqu'au bout, sous son libellé « Relevé en cours… ».

**2. Jamais de remplacement.** Une nuit qui a déjà ses observations n'est pas touchée : la carte le
dit et renvoie à « Sons & validation ». Réimporter touche à ce que l'observateur a validé ; cela se
décide nuit par nuit, comme pour l'action groupée.

**3. Jamais à l'ouverture de l'écran.** Sur un état « terminée » lu du cache, la carte dit de cliquer
« Actualiser ». Importer là ferait du réseau sans geste, et l'application ne sonde pas le serveur.

**4. Un import qui échoue se dit sous l'état**, qui reste « Analyse terminée ».

**5. Une seule règle pour l'écran et la commande** : `ImportApresReleve`, que lisent la carte et
`etat-traitement-vigiechiro --importer`.

## Ce qui a été écarté

**Un bouton « Importer les observations » offert à cet instant.** Le porteur a préféré l'import
d'office : la réponse « c'est terminé » n'appelle qu'une suite.

**Deux tâches enchaînées.** Elles laisseraient un instant où le bouton est libre et l'import en vol.

**Interpréter le refus d'un import sans remplacement** pour savoir si la nuit a ses observations. Un
comportement dépendrait d'un texte d'erreur : le port du socle a gagné une lecture locale.

## Conséquences

Le relevé automatique qui suit un lancement importe aussi, si la plateforme a déjà fini. Le verrou
que prend la commande est l'objet de
l'[ADR 5851](5851-une-lectrice-peut-cesser-de-l-etre-sur-une-option.md).
