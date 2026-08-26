---
type: adr
title: "Un effacement dit son contrat dans son nom"
status: stable
article: A17
chantier: "#3574, suite de la clôture du lot 1 (#3559)"
decided_at: 2026-08-11
verification: certaine
enforced_by:
  - "ArborescenceFichiersTest#efface_au_mieux_continue_apres_un_echec"
verified:
  - by: machine:ci
    at: 2026-08-11
---

# Un effacement dit son contrat dans son nom

## Contexte

**Sept** implémentations d'un effacement récursif vivaient dans le dépôt, toutes bâties sur
`Files.walk(...).sorted(reverseOrder())`. Ce n'est pas la duplication qui gênait, c'est que le même
nom ne voulait pas dire la même chose. Relevé en les ouvrant une par une :

| Comportement en cas d'échec | Où |
|---|---|
| **lève** une `IOException` : l'appelant décide | `ArborescenceFichiers`, `BancImport` |
| **lève** une `UncheckedIOException` : échoue fort, sans le déclarer | `SupprimerSauvegarde`, `DecoupageParallele` |
| **avale** : best-effort assumé | `ExtracteurZip`, `DossierTemporaire` |
| **rend un compte rendu** de ce qui a résisté | `NettoyageDossiersOrphelins` |

Quatre comportements, pas deux comme l'issue le supposait. Et `supprimerRecursivement` désignait à
la fois le premier et le troisième : **une copie faite depuis le mauvais modèle changeait le
comportement en cas de panne sans rien casser de visible.**

## Décision

**Deux contrats, deux noms, une seule implémentation du parcours.**

- `supprimerRecursivement` **lève**. L'appelant décide - une bascule de restauration qui ne parvient
  pas à retirer l'ancien dossier ne doit pas enchaîner sur le renommage comme si de rien n'était.
- `effacerAuMieux` **ne lève jamais**, et **rend ce qui a résisté, avec la raison**. Un nettoyage de
  temporaire ne doit pas transformer une opération réussie en échec ; mais se taire laisserait
  l'appelant sans rien à dire.

### Deux noms plutôt qu'un paramètre de politique

Le nom se lit **au site d'appel**, là où le doute existe. Un paramètre s'y lit aussi, mais se laisse
recopier sans qu'on y pense - et c'est exactement par recopie que les quatre comportements se sont
installés.

### Pas de troisième contrat pour l'enveloppe non déclarée

Envelopper une `IOException` dans une `UncheckedIOException` est une **décision de l'appelant**, pas
une politique de l'utilitaire : `SupprimerSauvegarde` et `DecoupageParallele` le font désormais
explicitement, chacun avec son propre message. Trois lignes visibles valent mieux qu'un troisième nom
à retenir.

### La raison fait partie du contrat « au mieux »

`NettoyageDossiersOrphelins` est le seul appelant dont l'utilisateur attend une **explication** : il
retire des données, pas des temporaires. Rendre seulement les chemins l'aurait obligé à refaire le
parcours pour retrouver la raison - c'est-à-dire à réécrire ce que cette classe existe pour n'écrire
qu'une fois.

## Conséquences

- De **sept** implémentations à **deux** : la source unique, et `BancImport`, outillage de performance
  exclu du binaire livré ([ADR 2746](2746-le-produit-ne-depend-pas-de-son-outillage.md)) - l'y rattacher
  ferait dépendre l'outillage du socle pour rien.
- `ArborescenceFichiers` devient **publique** : quatre features l'utilisent désormais, ce qui est le
  sens normal de la dépendance (une feature dépend du socle).
- Le contrôle porte sur le **geste** - `Files.walk(...).sorted(reverseOrder())` - et non sur le nom de
  méthode : c'est justement le nom qui mentait.

**Le premier test « continue après un échec » était creux**, et c'est la mutation qui l'a dit : il
opérait sur deux dossiers **séparés**, donc il ne prouvait rien du parcours. Arrêter au premier échec le
laissait vert. Refait sur un arbre mixte - un fichier effaçable et un sous-dossier verrouillé dans la
même arborescence - il tombe.
