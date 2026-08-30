---
type: adr
title: "La place d'un contrôle visuel se déduit de l'ordre des passes, pas de la destination de ce qu'il produit"
status: stable
article: A3
chantier: "#4903 (chantier #4882, sous #4828)"
decided_at: 2026-08-30
verification: humaine
loupe: "aucun motif ne lit une chaîne de dépendances : la question se pose quand une passe reçoit un contrôle nouveau, et se tranche en demandant de quoi il dépend"
verified:
  - by: humain
    at: 2026-08-30
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-30
---

# La place d'un contrôle visuel se déduit de l'ordre des passes, pas de la destination de ce qu'il produit

## Contexte

Le dépôt publie **76 clips de recette** sur trois pages du site développeur, indexés par cas
`Sxx-NN`. Aucune passe de clôture ne les regardait, et `revoir-les-ecrans` ne contenait pas une
occurrence de « clip », « asserté » ou « perceptif ».

La question s'est posée de savoir quelle passe devait les porter, et deux raisonnements s'opposaient.

## Le raisonnement par la destination, et pourquoi il se trompe

Le garde `check-doc-videos.sh` vise les pages de `docs/`, la documentation **utilisateur**. La passe
qui écrit `docs/` est la 4. Il paraît donc naturel que les clips lui reviennent.

C'était la proposition écrite dans l'issue, et elle est fausse.

## Décision

**La place d'un contrôle visuel se déduit de ce dont il dépend, pas de l'endroit où son produit
atterrit.**

Un clip est une **visualisation de l'état de l'application**, au même titre qu'une capture. Deux
dépendances en découlent :

- les actions sur le code doivent avoir eu lieu, donc il vient **après la passe 7**, l'harmonisation,
  pour la raison même qui place déjà la revue visuelle là ;
- il s'appuie sur le **cas de recette que la passe 6b pose**, sans quoi il n'y a rien à filmer.

```
6b  pose le cas Sxx-NN dans sa session propriétaire
 7  l'harmonisation fait ses derniers changements de code
 8  on regarde : les captures, ET le clip du cas
```

La passe 4 est en position 4, avant les deux. Elle filmerait un état que l'harmonisation va changer,
à partir de cas qui n'existent pas encore. **La passe 8 est la seule position possible.**

## Ce que cela donne à la passe 8, et qui n'est pas ce qu'elle faisait

La passe gagne un **second objet**. L'écran se juge sur ce qu'on peut y **lire**, et sa capture le
montre. Le geste se juge sur ce qu'il **fait**, et son clip le montre.

Un cas de recette **promettait** un geste et son observation attendue. Regarder le clip vérifie que
la promesse tient, pas seulement que l'écran est correct. C'est une boucle que la capture ne ferme
pas, et elle relie la passe 8 à la 6b.

## Conséquences

**Les deux familles ne s'ouvrent pas pour la même raison.** Pour un cas **perceptif**, le verdict
revient à qui regarde : ne pas ouvrir le clip, c'est n'avoir aucun verdict. Pour un cas **asserté**,
le test a tranché, et le clip vérifie qu'il joue ce que son nom annonce.

**Un clip noir n'est pas un clip cassé.** Un test qui n'ouvre aucune fenêtre en produit un, et c'est
le résultat juste : il s'audite en lisant le test. Rouvrir des clips sains est le meilleur moyen de
faire abandonner la passe, et la compétence le dit.

**Le même raisonnement vaut pour le prochain contrôle visuel.** La question à poser n'est pas « où
son produit sera-t-il lu » mais « de quoi dépend-il pour être juste ».

## Alternatives écartées

- **La passe 4**, par la destination. Écartée par l'ordre : elle précède les deux dépendances.
- **#4416**, le chantier des clips de recette. Il porte la **couverture** des cas par des clips, pas
  le moment où la clôture les regarde. Deux objets différents sur le même outillage.
- **Une passe dédiée aux clips.** Elle relirait les mêmes écrans que la 8, au même moment, avec le
  même geste. Deux passes pour un moment ne se justifient que si les gestes diffèrent (ADR 4902).
