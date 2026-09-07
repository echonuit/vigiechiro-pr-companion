---
type: adr
title: "Deux arbres écrivent la même question de plateforme, et les unifier coûterait un test décoratif"
status: stable
article: A11
chantier: "#5433 (onze rouges de plateforme en août, quatre en six jours), passe 7"
decided_at: 2026-09-07
verification: humaine
verification_note: "aucun motif ne distingue une duplication à résorber d'une écriture légitime dans chaque arbre ; la décision se relit, et la raison vit dans le doc-comment d'attributsDeCreation"
verified:
  - by: human:nedseb
    at: 2026-09-07
relations:
  prolonge: ["3802"]
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-07
---

# Deux arbres écrivent la même question de plateforme, et les unifier coûterait un test décoratif

## Contexte

La question « ce système de fichiers porte-t-il la vue `posix` ? » s'écrit à deux endroits :
`EcritureAtomique` en production, `fixture.SystemeDeFichiers` dans l'arbre de test. Elle s'y écrivait
en **six** avant ce chantier ; #5437 en a absorbé quatre, et ces deux-là restent.

La passe 7 a posé la question de les unifier. Elle est tentante pour une raison qui n'est pas la
cosmétique : l'[ADR 3802](3802-un-defaut-de-plateforme-se-sonde-il-ne-se-deduit-pas.md) nomme des
coutures de production créées exprès pour rendre une branche de plateforme **injectable**, et
`attributsDeCreation` porte une branche hors POSIX qu'aucun test ne joue depuis Linux.

## Décision

**On n'unifie pas, et on ne rend pas cette question injectable.**

Trois mesures l'établissent, dans cet ordre de force croissante.

**La production n'a qu'un site d'appel.** `EcritureAtomique:151`, et il se garde correctement.
Extraire une classe pour un appelant unique coûte plus que la copie.

**Le seuil de #5075 n'est pas atteint.** Cette issue compte 29 fonctions en 13 signatures dans
`scripts/`. Ici, deux écritures d'une ligne, dans deux arbres qui ne partagent pas leurs aides par
construction : `src/main` ne peut pas dépendre de `src/test`.

**Et la branche qu'une couture exposerait est un mutant équivalent.** C'est la mesure qui tranche, et
elle était déjà écrite dans le doc-comment d'`attributsDeCreation`, avant ce chantier :

> le mutant qui inverse ce test survit à PIT : sur ce JDK, les deux chemins donnent le même fichier.
> Il est équivalent par construction, pas mal couvert.

Rendre la question injectable achèterait donc un test qui exerce une branche dont les deux issues
sont **indiscernables**. C'est la définition du dispositif décoratif que
[l'ADR 5398](5398-une-exemption-se-declare-elle-ne-s-infere-pas.md) et
[l'ADR 5437](5437-une-fixture-qui-suppose-la-plateforme-se-refuse-localement.md) refusent l'une et
l'autre. Le chantier aurait construit le défaut qu'il venait de nommer.

## Ce que la décision n'autorise pas

**Elle ne dit pas que dupliquer est sans conséquence.** Elle dit qu'à deux écritures, dans deux
arbres, pour une question d'une ligne dont la branche est inobservable, le remède coûte plus que le
mal. Une troisième écriture, ou une branche qui deviendrait observable, rouvrent la question.

**Elle ne vaut pas pour les coutures que l'ADR 3802 nomme.** `ProtectionFichier`, `Deplacement` et
`GestesFichiers` existent parce que la branche qu'elles exposent **se voit** : un fichier lisible par
un autre compte, un déplacement refusé. Ce qui les justifie est exactement ce qui manque ici.

## Deux décisions voisines, et ce qui les sépare

Trois ADR du dépôt déclinent d'écrire un dispositif, pour trois raisons distinctes. Les confondre
ferait appliquer l'une là où une autre vaut.

| ADR | Ce qui manque | Le dispositif serait |
|---|---|---|
| [5414](5414-une-regle-que-rien-ne-peut-garder-se-declare.md) | l'**information**, qui n'existe nulle part | impossible |
| [5437](5437-une-fixture-qui-suppose-la-plateforme-se-refuse-localement.md) | la **population**, vide | indémontrable |
| celle-ci | l'**observabilité** de la branche | décoratif |

## Alternatives écartées

- **Descendre `SystemeDeFichiers` en production.** Elle deviendrait une classe livrée dans le jar dont
  le seul usager de production est une ligne.
- **Confronter l'arbitrage à des auditeurs externes.** La compétence `confronter-un-arbitrage` le
  refuse quand une mesure répond : ici trois répondaient, et la troisième était déjà écrite dans le
  dépôt.
