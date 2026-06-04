# 🧩 [extension] Lot : produire les ZIP de dépôt Tadarida (≤ 700 Mo)

> Extension **optionnelle** de la feature **lot**. À tenter après l'avoir terminée.

La plateforme Vigie-Chiro / Tadarida attend des **archives ZIP** conformes. Aujourd'hui M-Lot prépare
le lot mais ne produit pas les ZIP : ajoutez-les.

## Objectif

Générer une ou plusieurs archives ZIP du lot, respectant les contraintes Tadarida :
- **taille max 700 Mo par ZIP** (découper en plusieurs archives si nécessaire) ;
- **nom** = le **préfixe** des fichiers (ex. `Car640380-2026-Pass1-A1`) **+ un numéro croissant**
  (`…-1.zip`, `…-2.zip`, …) ;
- répartir séquences / originaux en respectant le plafond.

## Pistes

- Côté service (un nouveau service ou une extension de `ServiceLot`) pour la logique de découpage.
- Côté M-Lot, une action « Générer les ZIP » qui produit les archives dans le dossier de session.

## Critères d'acceptation

- [ ] Un lot volumineux produit **plusieurs** ZIP, chacun ≤ 700 Mo, nommés `<préfixe>-<n>.zip`.
- [ ] Le contenu réuni des ZIP couvre exactement le lot attendu.

## Definition of Done

- [ ] Couvert par des tests (découpage, nommage) ; suite verte ; PR relue. Voir la DoD commune (`e0`).
