---
type: adr
title: "Un original se lit par morceaux, seule une séquence bornée se lit entière"
status: stable
article: A5
chantier: "#6161 (l'import tient une nuit de plusieurs Go), lot #104"
decided_at: 2026-10-12
verification: certaine
enforced_by:
  - "ArchitectureTest#seule_une_sequence_bornee_se_lit_entiere"
  - "TransformationParMorceauxTest#sequences_identiques_a_la_lecture_complete"
  - "FichierWavTest#plus_de_deux_gio_ne_deborde_pas"
verified:
  - by: machine:ci
    at: 2026-10-14
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-14
---

# Un original se lit par morceaux, seule une séquence bornée se lit entière

## Contexte

Le brief en fait une conséquence d'O3 : « lecture par chunks, jamais de chargement complet d'un
fichier en mémoire ». L'import faisait pourtant `Files.readAllBytes` sur chaque WAV
(`FichierWav.lire`). Le pic restait borné tant que chaque fichier était petit, parce que le découpage
parallèle plafonne le nombre d'originaux en vol. Un enregistreur qui ne découpe pas produit des WAV
de plusieurs Gio, et au-delà de 2 Gio l'import tombait : la longueur tenait dans un `int`.

## Décision

**La transformation lit l'en-tête, puis le signal tranche par tranche, sans matérialiser le
fichier** (#104, PR #6164). Les séquences produites sont identiques au bit près à celles de
l'ancienne voie.

**`FichierWav.lire` reste, pour une seule raison** : la vérification d'identité
(`VerificationIdentiteAudio`) lit une séquence candidate de 5 s, bornée par construction, dont
l'analyse acoustique a besoin entière. Aucun autre appelant n'est admis.

## Ce qui la tient

- `ArchitectureTest#seule_une_sequence_bornee_se_lit_entiere` refuse tout appel de
  `FichierWav.lire` hors de `VerificationIdentiteAudio`. Vu rouge sur un appel ajouté dans
  `TransformationAudio`.
- `TransformationParMorceauxTest` compare chaque séquence à la tranche que le test découpe lui-même ;
  vu rouge sur une frontière avancée d'un octet, un octet relu, un tampon pas vidé.
- `FichierWavTest#plus_de_deux_gio_ne_deborde_pas`, vu rouge sur la longueur en `int`.

## Mesure

`BancImport` aux mêmes réglages (8 cœurs, 16 WAV de 120 s) : 904 Mo occupés après GC sous `-Xmx2g`
avant, 68 Mo après, et ce chiffre ne croît plus avec la durée d'un WAV. Le 2026-10-14, sur le second
poste, un WAV unique de 2,15 Gio (3000 s à 384 kHz) s'importe en 7,8 s pour 600 séquences, avec
314 Mo de mémoire au plus haut.

## Conséquences

- Un nouveau chemin qui aurait besoin du signal entier d'un fichier se pose la question de sa borne,
  et l'écrit ici avant d'être admis par le garde.
- La persistance par lots, seconde case de #104, a été écartée par le porteur : la transaction unique
  (O7) reste.
- L'empreinte SHA-256 de l'original se calcule par une seconde lecture complète. Ce n'est pas un
  chargement en mémoire, et son coût est écrit dans le bilan de #6161.
