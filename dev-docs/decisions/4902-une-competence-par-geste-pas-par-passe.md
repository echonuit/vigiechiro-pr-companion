---
type: adr
title: "Une compétence par geste, pas par passe : ce qui décide est si les deux moments font la même chose"
status: stable
article: A3
chantier: "#4902 (chantier #4882, sous #4828)"
decided_at: 2026-08-30
verification: humaine
loupe: "aucun motif ne distingue deux bouts d'un geste de deux gestes distincts : la question se pose à l'écriture de chaque compétence servant plus d'une passe, et il y en a une"
verified:
  - by: humain
    at: 2026-08-30
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-30
---

# Une compétence par geste, pas par passe : ce qui décide est si les deux moments font la même chose

## Contexte

Le chantier #4828 loge chacune des quatorze passes de clôture dans une compétence. Deux passes
nommaient une compétence déjà convoquée **à un autre moment du cycle**, et la question s'est posée
deux fois le même jour, avec deux réponses opposées.

| Passe | La compétence nommée | Son autre moment |
|---|---|---|
| 9 · suites | `trier-les-issues` | l'étape 0 de l'ouverture d'un chantier |
| 0 · relecture des ADR | `ecrire-une-adr` | la passe 11, l'écriture |

Dans les deux cas, la compétence portait de vraies étapes numérotées taillées pour **l'autre**
moment, et se lisait comme si elle servait les deux.

## Le défaut, plus retors que celui d'une compétence vide

Les passes 3, 4, 5 et 12 nommaient `humaniser`, qui ne porte aucune passe. Cela se voyait dès qu'on
l'ouvrait, et le chantier #4874 l'a corrigé.

Ici la compétence porte quelque chose, et l'on ne remarque pas que c'est **autre chose**. Un lecteur
qui l'ouvre à la passe 9 y trouve six étapes numérotées et les suit, sans voir qu'elles répondent à
la question de l'ouverture.

## Décision

**Ce qui décide n'est pas le nombre de passes, c'est si les deux moments font le même geste.**

**Deux gestes différents, deux compétences.** `trier-les-issues` décide **s'il y a lieu d'ouvrir** ;
la passe 9 **vide** ce qui s'est accumulé et porte une condition de sortie que le triage n'a pas. Le
verdict de l'une est un jugement d'opportunité, celui de l'autre est binaire. `vider-le-sas` a donc
été écrite, et `trier-les-issues` n'a pas été touchée (#4912).

**Deux bouts du même geste, une compétence, deux fonctions de garde.** Les passes 0 et 11 sont les
deux extrémités d'une même conversation avec le corpus des décisions : ce que la passe 0 trouve d'un
dépassement délibéré, la passe 11 l'écrit des deux côtés, et son étape 4 est littéralement la
conséquence de la passe 0. `ecrire-une-adr` les garde ensemble, avec **deux fonctions de garde
nommées et séparées** (#4902).

Ce qui manquait dans les deux cas était la même chose : que le lecteur sache **quelles étapes sont
les siennes**.

## Conséquences

**Une compétence qui sert deux passes le déclare en tête.** Sa section « deux moments » dit lequel
fait quoi, avant toute étape numérotée.

**Sa `description:` nomme les deux passes.** Celle d'`ecrire-une-adr` en annonçait une seule, et
fausse depuis la renumérotation de #4518. Ce défaut est distinct et suivi par #4918.

**Le critère se pose à l'écriture, jamais après.** Une compétence écrite pour un moment puis
convoquée à un second se lit comme si elle servait les deux, et c'est exactement ainsi que les deux
défauts sont nés.

## Alternatives écartées

- **Couper systématiquement, une compétence par passe.** Les passes 0 et 11 auraient alors deux
  compétences qui se renvoient l'une à l'autre à chaque décision trouvée, et la règle du dépassement
  s'écrirait dans les deux.
- **Grouper systématiquement, une compétence par domaine.** C'est l'état d'où l'on vient : deux
  compétences convoquées à deux moments dont les étapes ne servaient qu'un seul.
- **Un garde qui refuserait une compétence servant plus d'une passe.** Il refuserait `ecrire-une-adr`,
  qui est le bon cas. Ce qui se juge est la nature du geste, et aucun motif ne la lit.
