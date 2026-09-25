---
type: adr
title: "Un gage inerte est un défaut de l'ADR, pas un quatrième niveau de vérification"
status: stable
article: A6
chantier: "EPIC #5483"
decided_at: 2026-09-25
verification: certaine
enforced_by:
  - "scripts/adr/verifie_okf.py"
verified:
  - by: machine:ci
    at: 2026-09-25
relations:
  prolonge: ["2465"]
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-25
---

# Un gage inerte est un défaut de l'ADR, pas un quatrième niveau de vérification

## Contexte

L'[ADR 2465](2465-une-adr-declare-comment-elle-est-verifiee.md) donne trois niveaux, et réserve
`certaine` à un dispositif qui **échoue en CI quand la règle est violée**. Le seul contrôle qui
tenait cette promesse vérifiait que le fichier nommé en `enforced_by` **existe**. Exister n'est pas
juger.

Trois ADR se déclaraient `certaine` en nommant un gage incapable de refuser quoi que ce soit : la
3802 un détecteur hebdomadaire, la 4993 cent dix-neuf lignes de prose, la 5239 le code que la
décision régit. Aucune n'avait été trouvée par un dispositif.

La question s'est alors posée, et elle est légitime : certaines propriétés ne se prouvent que sur une
machine qu'on n'a pas, ou pas à chaque demande. **Fallait-il un quatrième niveau, ou une
qualification de `certaine`**, pour dire « tenu, mais en différé » ?

## Décision

**Non. Le vocabulaire reste à trois niveaux, et un gage qui ne peut pas rougir est un défaut de
l'ADR qui le nomme.**

La mesure a tranché. Sur les 187 ADR `certaine` du dépôt, **trois** nommaient un gage inerte, et
elles le faisaient de **trois façons sans rapport entre elles** : un atelier qui ne se déclenche sur
aucune demande, un document qui ne s'exécute pas, une bibliothèque qui est le sujet de la décision.
Trois cas isolés aux causes distinctes ne sont pas une catégorie qui manque au vocabulaire : ce sont
trois erreurs de rédaction.

Un quatrième niveau leur aurait donné un abri. Chacune des trois avait en réalité un autre niveau
juste : la 3802 est passée en `probable` sur un garde qui joue à chaque demande, la 4993 et la 5239
en `humaine`, leur règle étant de méthode.

## Conséquences

- **Le niveau se choisit par ce que le gage RÉPOND**, non par ce à quoi il ressemble. Un
  `enforced_by` se relit en demandant : qu'est-ce qui rougit, sur quelle demande ?
- **Le refus est mécanique depuis #5484** : `verifie_okf.py` refuse quatre formes de gage inerte, et
  la quatrième - un script qu'aucun atelier de demande n'invoque - a été trouvée en écrivant le garde.
- **Une décision que rien ne peut tenir se déclare `humaine`**, ce que
  l'[ADR 5414](5414-une-regle-que-rien-ne-peut-garder-se-declare.md) prescrivait déjà. Descendre de
  niveau n'est pas un renoncement : c'est cesser d'emprunter la solidité d'un gage qui n'existe pas.
- **Le vocabulaire clos est lui-même gardé** : `verifie_okf.py` refuse tout `verification:` hors des
  trois valeurs. Ajouter un quatrième niveau ferait rougir la CI, ce qui rend cette décision opposable
  plutôt que déclarative.

## Alternatives écartées

- **Un quatrième niveau, `differee`.** Il aurait rendu vrai ce que les trois ADR disaient, au prix
  d'une catégorie que trois cas ne justifient pas, et qui aurait servi de refuge au prochain gage
  inerte plutôt que de le faire corriger.
- **Une qualification de `certaine`**, du genre `certaine (hebdomadaire)`. Même effet, et elle aurait
  brouillé la seule chose que `certaine` promet : un refus sur la demande fautive.
- **Ne rien décider, et corriger les trois cas.** C'est ce qui a été fait du travail, mais la question
  se reposera à la prochaine mesure. Sans cette page, la réponse serait à retrouver.
