---
type: adr
title: "Un temporaire volumineux naît sous l'espace de travail, jamais dans le dossier temporaire du système"
status: stable
article: A5
chantier: "#6161 (l'import tient une nuit de plusieurs Go), lot #6174"
decided_at: 2026-10-12
verification: certaine
enforced_by:
  - "ArchitectureTest#aucun_temporaire_du_produit_dans_le_dossier_du_systeme"
  - "ServiceReactivationPassageTest#voie_bruts_regenere_sous_l_espace_de_travail"
  - "ServiceReactivationPassageTest#hydratation_regenere_sous_l_espace_de_travail"
verified:
  - by: machine:ci
    at: 2026-10-14
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-14
---

# Un temporaire volumineux naît sous l'espace de travail, jamais dans le dossier temporaire du système

## Contexte

Le 2026-09-15, la réactivation d'une nuit a figé un poste jusqu'à ce que le processus s'arrête faute
de mémoire. Le banc `BancReactivation` a établi la cause en ne faisant varier qu'une chose
(#6174) : la régénération écrivait ses temporaires dans `java.io.tmpdir`, et sur ce poste `/tmp` est
un tmpfs. Ce qui s'y écrit est de la mémoire vive, que la JVM ne voit pas : huit bruts de 20 min
régénérés à la fois y ont pris 7031 Mo, et la mémoire du poste est montée de 7,8 Gio. Le même tir
avec le temporaire sur disque : 1,0 Gio. Le heap, lui, restait à 15 Mo dans les deux cas.

## Décision

**Un temporaire que le produit remplit naît sous un dossier qu'il nomme, l'espace de travail en
général.** `DossierTemporaire` le fait pour la réactivation depuis #6182 ; `ExtracteurZip` le faisait
déjà pour l'extraction d'une archive, pour la même raison.

**Au démarrage, le produit efface ce qu'un plantage y a laissé** (`DossierTemporaire.nettoyerResiduels`,
décision du porteur du 2026-10-13) : sous l'espace de travail, un temporaire oublié reste sur le disque,
là où `/tmp` se vidait au redémarrage.

Les outils de capture et les bancs, sous un paquet `outils`, restent libres d'écrire dans
`java.io.tmpdir` : ils ne sont pas le produit (ADR 2746).

## Ce qui la tient

- `ArchitectureTest#aucun_temporaire_du_produit_dans_le_dossier_du_systeme` refuse, hors des
  paquets `outils`, `Files.createTempDirectory` et `Files.createTempFile` sans dossier parent. Vu
  rouge en remettant `createTempDirectory(prefixe)` dans `DossierTemporaire`.
- Les deux tests de `ServiceReactivationPassageTest`, un par voie, enregistrent le dossier remis au
  port de régénération ; vus rouges sur le code d'avant (`/tmp/vc-regen-…`, `/tmp/vc-hydrate-…`).
- `DossierTemporaireTest` garde le filet du démarrage, vu rouge avec un filet au corps vide.

## Conséquences

- Écrire sur disque coûte du temps : la réactivation de la nuit hydratée passe de 29,9 à 59,4 s sur le
  premier poste. C'est le prix de ne plus saturer la mémoire.
- Un temporaire sous l'espace de travail compte dans l'occupation disque que l'utilisateur voit ; il
  est effacé aussitôt, un par brut.
