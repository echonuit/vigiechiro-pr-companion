---
type: adr
title: "Un dépôt entamé se régénère, sauf pendant qu'un téléversement tourne"
status: stable
article: A15
heuristiques:
  - "nielsen-5"
chantier: "#5599, lot 3 du chantier #5596"
decided_at: 2026-09-30
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/lot/RegenerationPendantUnDepotTest.java"
  - "src/test/java/fr/univ_amu/iut/lot/model/TeleversementsEnCoursTest.java"
verification_note: "la première classe joue le parcours par le vrai service, refus compris ; la seconde tient le registre armé, levé après succès et levé après exception. Aucune ne joue deux processus : la course entre l application et une commande lancée en même temps est fermée par le verrou du dossier de travail, pas par ce registre"
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# Un dépôt entamé se régénère, sauf pendant qu'un téléversement tourne
## Contexte

Quand Vigie-Chiro refuse le contenu d'une archive, le compte rendu conseille de régénérer les
archives puis de relancer (#3946). Sur un dépôt entamé, la génération répondait « préparez-le
d'abord » : elle n'était admise qu'à l'état « Prêt à déposer ». Le geste que l'application conseillait
était donc celui qu'elle refusait (#5599, capture 4 de Samuel).

Lever ce refus sans précaution ouvrait une course : le téléversement produit lui-même ses archives,
dans le même dossier `depot/`, et les supprime une fois en ligne.

## Décision

**1. La génération est admise sur un dépôt entamé.** L'écran et `exporter-lot` la permettent à l'état
« Dépôt en cours », sans nouvelle préparation.

**2. Elle est refusée pendant qu'un téléversement tourne**, avec un message qui dit d'attendre sa fin
ou de l'annuler.

**3. « Un téléversement tourne » se sait par un registre en mémoire.** `TeleversementsEnCours` est un
`@Singleton` de `lot/model`. `DepotVigieChiro.deposer` s'y inscrit par un jeton `AutoCloseable`, donc
dans un `try` : le retrait ne peut pas être oublié, qu'il aboutisse, échoue ou soit annulé.

## Ce qui a été écarté

**Un drapeau en base.** Il survivrait à un plantage et bloquerait la génération à tort jusqu'à un
nettoyage manuel : exactement le coincement que ce lot corrige.

**Faire dépendre `ServiceLot` de `DepotVigieChiro`.** La liaison est optionnelle : une installation
sans dépôt connecté ne pourrait plus construire le service de lot.

**Laisser l'écran seul griser le bouton.** La commande et tout autre appelant resteraient sans garde.

## Conséquences

Le registre ne voit que son processus. Deux processus sur le même dossier de travail sont déjà exclus
par le verrou de l'ADR 2731.
