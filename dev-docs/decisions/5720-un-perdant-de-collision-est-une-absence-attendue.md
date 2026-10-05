---
type: adr
title: "Un perdant de collision est une absence attendue, qui se reconnaît à son nom et se compte à part"
status: stable
article: A14
heuristiques:
  - "nielsen-9"
chantier: "#5720, lots 21 à 23 du chantier #5596 (#5717, #5719, #5720)"
decided_at: 2026-10-01
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/commun/model/PerdantDeCollisionTest.java"
  - "src/test/java/fr/univ_amu/iut/passage/model/CompteRenduPerdantsDeCollisionTest.java"
verification_note: "la première classe tient la reconnaissance par le nom, témoins compris (un nom sans horodatage n est pas un perdant) ; la seconde tient le compte rendu. Ce qu aucune ne tient : qu une nuit déposée avec Kaleidoscope n ait réellement aucune observation sur ces séquences, dit au conditionnel faute de l avoir observé"
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# Un perdant de collision est une absence attendue, qui se reconnaît à son nom et se compte à part
## Contexte

Quand deux tranches d'enregistrements qui se chevauchent veulent le même nom de séquence, l'import
garde `_000` pour la plus ancienne et écrit l'autre en `_001`. Kaleidoscope, lui, ne produit que des
`_000`. Une nuit réactivée depuis un dossier qu'il a découpé ne retrouve donc aucun `_001`, et le
compte rendu les rangeait parmi les séquences introuvables, motif « aucun fichier de ce nom dans le
dossier ». L'utilisateur cherchait un fichier qui n'a jamais existé chez lui.

## Décision

**1. Un perdant se reconnaît à son nom.** `NommageSequences.perdantDeCollision` exige un horodatage
**et** un suffixe d'au moins `_001`. La règle vit à côté de celle qui produit ces noms.

**2. Il se compte à part, et reste dans `manquantes`.** `BilanReactivation` gagne la liste des
perdants ; chacun incrémente aussi `manquantes`. Décision du porteur du 1er octobre : sortir les
perdants de `manquantes` changerait le sens d'une clé JSON sans que rien ne rougisse chez un script
qui la lit.

**3. Les deux comptes rendus les nomment, chacun dans sa forme** : un segment de barre d'une teinte à
part et une mention à l'écran, un constat en avertissement dans le terminal. Tous deux conseillent de
réactiver depuis les enregistrements bruts.

**4. À l'import de séquences déjà découpées, un nom horodaté d'index supérieur à zéro est son propre
original** (#5719) : il n'est plus lu comme une tranche d'un autre fichier.

## Ce qui a été écarté

**Tester le seul suffixe.** Il prendrait pour perdant la deuxième tranche d'un nom sans horodatage,
où `_001` est un index.

**Vérifier en base qu'un `_000` du même nom existe.** La preuve serait plus forte, pour une requête
par absence, sur un cas que le nommage garantit déjà.

## Conséquences

Une absence a désormais deux natures dans un compte rendu : celle qu'on peut réparer en retrouvant un
fichier, et celle qui s'explique. La seconde ne se présente pas comme la première.
