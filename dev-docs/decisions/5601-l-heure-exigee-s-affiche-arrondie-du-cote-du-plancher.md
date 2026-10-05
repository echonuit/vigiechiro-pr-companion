---
type: adr
title: "L'heure exigée s'affiche arrondie du côté du plancher, et se compare à la seconde"
status: stable
article: A16
chantier: "#5601, lot 5 du chantier #5596"
decided_at: 2026-10-01
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/diagnostic/PariteCoherenceHoraireTest.java"
verification_note: "le cas `programmer_la_fin_affichee_tient_la_fenetre` programme un arrêt sur l heure affichée et constate qu il tient la fenêtre comparée à la seconde ; la classe tient aussi que l écran et la commande lisent le même arrondi"
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# L'heure exigée s'affiche arrondie du côté du plancher, et se compare à la seconde
## Contexte

Le protocole exige que l'enregistrement couvre une fenêtre, par exemple jusqu'à 30 minutes après le
lever du soleil. Le lever vient de l'éphéméride, avec ses secondes : une fin exigée peut tomber à
06:28:15. L'écran du diagnostic et `diagnostiquer` l'affichaient en `HH:mm`, donc **tronquée** à
06:28. Samuel a programmé son enregistreur sur l'heure affichée, et a reçu l'alerte « horaires non
respectés » (2.193.0).

Deux règles étaient possibles : comparer à la minute, ou afficher autrement.

## Décision

**1. La comparaison reste à la seconde.** Comparer à la minute accepterait un arrêt jusqu'à
59 secondes avant l'heure exigée, contre le plancher que l'ADR 4984 a posé.

**2. L'heure affichée s'arrondit du côté qui tient le protocole** : le début exigé à la minute
inférieure, la fin exigée à la minute supérieure. Qui programme l'heure qu'il lit respecte la fenêtre.

**3. Un seul endroit porte l'arrondi.** `CoherenceHoraire` expose `debutExigeAffiche` et
`finExigeeAffichee` ; l'écran (`PlagesHoraires`) et la commande, en texte comme en JSON, les lisent.

## Ce que cette décision n'autorise pas

Elle n'autorise pas à arrondir toutes les heures de l'écran. Le lever lui-même reste affiché tronqué :
ce n'est pas une heure à programmer. Sur un aperçu, qui ajoute 30 minutes au lever lit donc une minute
de moins que la fin affichée, et c'est voulu.

## Conséquences

Une heure affichée n'est plus la troncature de l'heure comparée. Qui ajoute une heure « à programmer »
à cet écran lui donne son sens d'arrondi, au lieu de reprendre le format par défaut.
