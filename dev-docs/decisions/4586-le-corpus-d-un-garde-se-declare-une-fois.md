---
type: adr
title: "Le corpus d'un garde se déclare une fois, et sa liste se dérive"
status: stable
article: A3
chantier: "#4586 (passe 7 de la clôture de #4502)"
decided_at: 2026-08-26
verification: probable
enforced_by:
  - "scripts/adr/verifie_corpus_declare.py"
ratchet: 0
verified:
  - by: machine:ci
    at: 2026-08-26
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-26
---

# Le corpus d'un garde se déclare une fois, et sa liste se dérive

## Contexte

#4488 a décidé que les gardes de code lisent les **deux** arbres de sources. La décision était
tenue : `verifie_scripts.py` vérifie pour chaque garde d'une liste que l'arbre de test est dans ses
racines, et sa docstring dit même pourquoi `2635-refus-sans-surface.py` en est exclu.

**Ce qui n'était pas tenu, c'est la liste.** Écrite à la main, elle avait dérivé. Mesuré le
2026-08-26 en fermant #4502 :

| Constat | Mesure |
|---|---:|
| entrées de `GARDES_DEUX_ARBRES` | 10 |
| scripts portant le chemin de l'arbre de test | 15 |
| **lisant les deux arbres sans figurer dans la liste** | **6** |

Six gardes n'étaient vérifiés par rien. L'un d'eux pouvait cesser de lire un arbre : son compte
aurait baissé, et **un cliquet ne se plaint pas qu'on lui retire du corpus**. La perte de couverture
se serait lue comme un progrès, le rapport proposant de resserrer.

Le dépôt connaissait le piège : `verifie_temoins_non_decoratifs.py` dérive sa liste plutôt que de
l'énumérer, « un glob vieillit, et un garde neuf passerait au travers ». La leçon valait pour les
témoins, pas encore pour le corpus.

Une seconde chose empêchait la dérivation : le corpus n'était pas lisible par le programme. Treize
scripts écrivaient les chemins en littéral, sous deux formes qu'aucun balayage naïf ne rapproche,
`pathlib.Path("src/main/java")` chez les uns et `RACINE / "src" / "main" / "java"` chez les autres.

## Décision

**Le corpus vit dans `_commun.py`**, chemins relatifs et variantes ancrées. Deux jeux de noms, un
seul endroit où les segments sont écrits : c'est la duplication du chemin qui coûtait, pas celle du
nom.

**La liste se dérive de ce que chaque garde importe.** `RACINES` déclare « je lis les deux
arbres » ; `PRODUCTION` seule déclare une exception. La liste passe de dix entrées écrites à
**quinze dérivées**, et les cinq gardes qu'elle ignorait sont désormais vérifiés.

**Une exception dit sa raison dans son propre fichier.** Elle vivait dans la docstring de
`verifie_scripts.py`, où un lecteur de `2635` ne la trouvait pas.

**Un garde ne réécrit pas un chemin d'arbre**, et `verifie_corpus_declare.py` le refuse. C'est ce
refus qui rend la dérivation fiable : sans lui, un garde neuf réécrirait le chemin et redeviendrait
invisible à la liste.

## Les deux exceptions, tranchées par la mesure

**`2635` reste sur la production, et le déclare.** Étendu aux tests il rend trois suspects, tous
dans `MoteurTraitementGroupeTest`, et tous des **mentions** : un double qui simule l'application,
deux assertions qui citent le message attendu. L'étendre interdirait aux tests d'affirmer les
chaînes que la règle produit.

**`4395` est un trou connu, pas une exception justifiée.** 976 renvois distincts vivent dans la
javadoc de `src/test` sans plancher, contre 3 134 en production. Combler demande de lire les 976
avant de relever le plancher : c'est #4587. Son en-tête dit désormais que c'est un trou et non un
choix, ce qui est la différence que cette ADR rend lisible.

## Ce qu'on perd

Un garde neuf doit savoir que le corpus s'importe. C'est ce que le refus enseigne : il nomme le
fichier, la ligne, et dit d'importer.

Le refus ne voit que les deux arbres Java. Un garde qui recopierait le chemin de
`dev-docs/decisions` passerait au travers, et c'est assumé : la composition de ces deux arbres est
la seule qui ait fait l'objet d'une décision.

**La marge sépare un corpus d'une fixture**, et cette règle a une limite : une déclaration indentée
échapperait au refus. L'alternative aurait été d'énumérer les fichiers autorisés à bâtir des
fixtures, ce qui aurait recréé la liste manuelle que cette décision supprime.

## Alternatives écartées

**Allonger la liste à la main.** C'est ce qui avait été fait, et elle avait dérivé de dix contre
quinze en quelques chantiers.

**Étendre `PatronDuCliquetTest` aux cliquets Python.** Ce test mutualise un balayage et un message
JUnit, que Python n'a pas à emprunter. Sa phrase d'exclusion est corrigée par le même lot : elle
disait que ces scripts inspectent la documentation, quand vingt-trois sur trente-deux inspectent du
Java. Le motif est le langage, pas le sujet.

**Renommer les scripts par le geste**, comme le dépôt de référence. Écarté : `_commun.py` pose que
l'identité d'une ADR est son numéro, et le renommage toucherait chaque `enforced_by:`, donc la
matrice de la constitution qui s'en engendre.
