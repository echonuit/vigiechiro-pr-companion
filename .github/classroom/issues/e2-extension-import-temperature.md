# 🧩 [extension] Import/Passage : température de début de nuit (optionnelle)

> Extension **optionnelle** des features **importation** et **passage**.

## Objectif

Permettre de saisir une **température en début de nuit**, **facultative**, rattachée au passage, et de
l'afficher.

## Pistes

- Saisie : au **rattachement** (M-Import) et/ou en édition (M-Passage).
- Stockage : un champ **nullable** (table `passage` ou relevé climatique).
- Affichage : M-Passage et/ou M-Diagnostic.

## Critères d'acceptation

- [ ] Le champ est **optionnel** : un passage sans température fonctionne exactement comme avant.
- [ ] Une température saisie est persistée et réaffichée.

## Definition of Done

- [ ] Couvert par des tests ; aucune régression ; MVVM respecté ; PR relue. Voir la DoD commune (`e0`).
