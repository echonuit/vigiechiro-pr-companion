# 🧩 [extension] Import : gérer les fichiers déjà préfixés

> Extension **optionnelle** de la feature **importation**.

## Objectif

Importer correctement un dossier dont les WAV sont **déjà renommés** (préfixés) :
- **ne pas re-préfixer** (éviter un double préfixe type `Car…-Car…`) ;
- **avertir** si le préfixe présent **ne concorde pas** avec le préfixe attendu (carré / année / n° /
  point du rattachement choisi) — avertissement **non bloquant**.

## Pistes

- L'inspection détecte déjà l'état de nommage « préfixé » : appuyez-vous dessus.

## Critères d'acceptation

- [ ] Un dossier déjà préfixé s'importe **sans double préfixe**.
- [ ] Un préfixe **discordant** déclenche un **avertissement** clair, sans blocage.

## Definition of Done

- [ ] Couvert par des tests (préfixé concordant / discordant) ; suite verte ; PR relue. DoD commune (`e0`).
