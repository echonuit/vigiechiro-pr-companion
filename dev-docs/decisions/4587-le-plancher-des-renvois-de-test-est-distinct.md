---
type: adr
title: "Le plancher des renvois de test est distinct de celui de production"
status: stable
article: A3
chantier: "#4587 (suites du portage, après #4646)"
decided_at: 2026-08-28
verification: certaine
enforced_by:
  - "scripts/adr/4395-renvois-en-javadoc.py"
floor: 1008
inv_key: plancher-renvois-test
verified:
  - by: machine:ci
    at: 2026-08-28
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-28
---

# Le plancher des renvois de test est distinct de celui de production

## Contexte

L'ADR 4395 pose qu'un renvoi porté par la javadoc ne se perd pas, et son plancher garde les renvois
de `src/main/java`. Il n'a jamais lu `src/test/java`.

Mesuré le 2026-08-28 : **<!--inv:plancher-renvois-test-->1 008<!--/inv--> renvois distincts** y vivent, dans 607 fichiers sur 824, et rien ne les
garde. Le trou a été trouvé en passe 7 de la clôture de #4502, en mesurant pourquoi deux gardes sur
treize lisaient la production seule. L'autre, `2635`, était une exception justifiée ; celui-ci était
un oubli.

**Ce que l'arbre de test apporte, et qu'aucun autre ne porte.** Sur ces 990 renvois, **274** pointent
vers une issue que la production ne cite nulle part : 151 discussions dont le seul lien dans le code
est un test. Les perdre ne renvoie pas le lecteur ailleurs, cela coupe le fil.

Les 716 autres doublent un renvoi de production, et c'est voulu : le comptage somme les issues
distinctes **par fichier**, parce qu'un lecteur qui ouvre un test a besoin du lien dans ce test, pas
dans une classe voisine.

## Décision

**Deux planchers, un par arbre, et surtout pas un seul sur les deux.**

Un plancher unique à 4 155 laisserait une perte d'un côté se compenser par un gain de l'autre : total
stable, verdict vert, et un renvoi perdu en production payé par un renvoi ajouté dans un test. C'est
le défaut des populations réunies sous un compteur, et il est le même que celui des populations
emboîtées, mesuré en confrontant l'arbitrage du portage.

Les deux populations sont donc **disjointes**. Chacune a son ADR, son seuil et sa ligne de verdict,
et le garde rend le pire des deux codes : une perte dans un arbre fait rougir, même si l'autre a
gagné.

## Ce qui le prouve

La mutation se fait **dans les deux sens** : retirer un renvoi de production fait rougir le plancher
4395 et laisse le 4587 vert ; retirer un renvoi de test fait l'inverse. C'est cette réciprocité qui
démontre la disjonction, et elle ne se déduit pas du code.

## Ce qu'on perd

Deux seuils à relever au lieu d'un, chacun à sa mesure. Le plancher monte à chaque lot qui pose des
renvois, et il monte maintenant deux fois plus souvent. C'est le prix de la disjonction, et il est
préféré à un compteur qui se tiendrait tout seul en se compensant.

## Alternatives écartées

**Un plancher unique sur les deux arbres.** Écarté pour la compensation ci-dessus.

**Étendre le plancher 4395 à `src/test`.** Même défaut sous un autre nom : une seule valeur, deux
populations, et rien qui empêche l'une de payer pour l'autre.

**Ne rien garder côté test.** C'était l'état, et 274 renvois y sont les seuls liens vers leur
discussion.
