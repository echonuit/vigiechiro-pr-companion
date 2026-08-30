---
type: adr
title: "Ce que la clôture a décidé de ne pas faire, et pourquoi"
status: stable
article: A11
chantier: "#3667, à la clôture de l'EPIC"
decided_at: 2026-08-16
verification: humaine
verification_note: "deux abstentions de méthode : ni la portée de PIT ni la fusion de deux auto-tests ne s'observent dans le code, et un test qui prétendrait les prouver mesurerait autre chose"
verified:
  - by: human:nedseb
    at: 2026-08-16
---

# Ce que la clôture a décidé de ne pas faire, et pourquoi

## Contexte

La passe 11 demande d'écrire les décisions du chantier, en rappelant qu'**une décision de ne pas
faire en est une**. Deux abstentions sont sorties de cette clôture. Sans trace, chacune se
re-débattra, et probablement dans l'autre sens.

## Décision 1 : la logique pure de la recette reste en portée test, sans couverture de mutation

`RepartitionDesCas` est un tri sans état ni E/S - exactement ce que la mutation éprouve bien. La
passe 6 exige un PIT ciblé sur les classes pures livrées.

**Il n'a pas pu tourner.** Trois lancements, trois fois `Created 0 mutation test units in pre scan`,
y compris après avoir ajouté `${project.build.testOutputDirectory}` aux `mutableCodePaths` du profil.
Le journal donne la cause probable dès sa première ligne : `Mutating from target/classes`.

**Ce qui n'est pas établi** : que ce soit la seule cause. Le troisième essai aurait dû lever
l'obstacle s'il n'y avait que celui-là. Écrire « PIT ne mute pas le code de test » serait une
hypothèse plausible présentée comme un fait.

**Ce qui est décidé** : ne pas déplacer cette logique vers `src/main` pour la rendre mutable. Elle
n'appartient pas au produit - c'est de l'outillage de recette - et l'y mettre pour satisfaire un
outil de mesure inverserait l'ordre des raisons.

**Ce que ça coûte, et il faut le dire** : l'assurance de cette logique repose sur 8 tests unitaires
et un **témoin passé à la main** - rétablir l'ancienne règle fait tomber 2 de ces 8. C'est moins que
ce que la passe demande. Sur un autre chantier, PIT avait trouvé 14 survivants qu'une mutation
manuelle n'avait pas vus : *elle ne trouve que ce à quoi on pense*.

## Décision 2 : les deux `essai()` ne sont pas factorisés

Deux scripts sur dix-huit portent un lanceur de cas nommé `essai()`. Les extraire dans un helper
partagé était le réflexe.

**Refusé, pour deux raisons mesurées.**

Ce ne sont pas des doublons : l'un **construit un manifeste** depuis le corps du cas et le soumet au
verdict, l'autre **lance une commande** quelconque. Le partagé se réduit à huit lignes de comptage.

Et surtout, [ADR 3661](3661-un-garde-de-ci-porte-ses-propres-cas.md) a délibérément **remis les cas
à l'intérieur de leur garde**, en supprimant un fichier partagé. Un helper sourcé réintroduirait
exactement la dépendance qu'elle venait de retirer.

Ici, abstraire coûte plus que la duplication. La duplication et l'abstraction sont des outils, pas
des vertus.

## Conséquences

- Toute reprise de ces deux sujets part de ces motifs, et non de zéro. Les rouvrir demande un fait
  neuf : une portée de PIT établie, ou un **troisième** usager de `essai()` qui rendrait le partage
  moins théorique.
- La convention de restitution (« N cas, dont M qui DOIVENT rougir »), elle, **est** retenue pour
  généralisation - hors du delta de ce chantier, en #3886. La différence tient à ce qu'elle ajoute
  une information à chaque garde, là où factoriser n'en ajoute aucune.
