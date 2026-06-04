# 🧩 [extension] Import : autoriser l'import sans journal LogPR (mode dégradé)

> Extension **optionnelle** de la feature **importation**. Touche aussi le service (fourni).

## Objectif

Aujourd'hui l'import **échoue** si le journal `LogPR` est absent. Le rendre **possible en mode
dégradé** : un journal manquant devient un **avertissement non bloquant** (même esprit que les
avertissements « mélange » / « incohérence »).

## Pistes

- Remplacer le refus par un avertissement ; tolérer des paramètres d'acquisition nuls.
- Définir l'identité de l'enregistreur en l'absence de journal (valeur de repli ou saisie).

## Critères d'acceptation

- [ ] Un dossier **sans `LogPR`** s'importe avec un **avertissement** (et non un blocage).
- [ ] Un dossier **avec** `LogPR` continue de fonctionner comme avant.

## Definition of Done

- [ ] Couvert par des tests (avec / sans journal) ; suite verte ; PR relue. Voir la DoD commune (`e0`).
