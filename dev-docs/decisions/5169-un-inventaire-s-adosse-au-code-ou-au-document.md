---
type: adr
title: "Un inventaire s'adosse au code, ou au document qui fait autorité sur sa population"
status: stable
article: A5
chantier: "#5169 et #5178 (chantier #5162)"
decided_at: 2026-09-03
verification: certaine
enforced_by:
  - "DocumentationAJourTest#chaque_chiffre_balise_egale_l_inventaire_reel"
verified:
  - by: machine:ci
    at: 2026-09-03
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-03
---

# Un inventaire s'adosse au code, ou au document qui fait autorité sur sa population

## Contexte

L'ADR 2385 pose que la doc chiffrée est confrontée au code, et elle est explicite sur le point de
comparaison : « jamais une liste tenue à la main (c'est ce qui dérive) mais la vérité du câblage ».

`REMERCIEMENTS.md` annonçait 92 étudiantes et étudiants et en listait 90. Aucun code ne peut trancher
ce nombre : combien de personnes ont participé à la SAÉ 2.01 n'est écrit nulle part dans ce dépôt, et
n'y entrera pas. La seule autorité disponible est la liste nominative du fichier lui-même. L'écart a
vécu jusqu'à ce que le récit d'origine de #5163 recopie le chiffre à quatre endroits de plus.

Le même fichier annonce un nombre d'équipes, dans la même phrase, avec la même exposition.

## Décision

**Une balise d'inventaire peut s'adosser à un document du dépôt qui fait autorité sur sa propre
population, quand aucun code ne porte l'information.** Les clés `contributeurs` et `equipes` comptent
les lignes de contributeur et les sections d'équipe de `REMERCIEMENTS.md`.

**Le document dit alors ce que le chiffre compte.** L'en-tête de `REMERCIEMENTS.md` annonce le nombre
de personnes nommées sur la page, pas un effectif d'inscrits, et dit qu'un test le vérifie. Sans cette
phrase, le lecteur croirait le garde plus fort qu'il n'est.

## Pourquoi celle-là, et non l'autre

Laisser le chiffre à la main est ce qui a produit l'écart, puis sa propagation.

Le retirer supprimerait une information réelle : ce que pèse le travail des équipes fait partie de ce
que la page raconte.

Le porter dans le code, sous forme de constante, ferait du code la copie d'un fichier de prose, et
déplacerait la dérive sans la supprimer.

## Conséquences

Le garde vérifie une **cohérence interne**, pas une vérité extérieure. Une personne oubliée de la
liste fait baisser les deux nombres ensemble, et le test reste vert. Il attrape la dérive de
l'annonce, jamais l'oubli dans la liste, et c'est pourquoi le fichier le dit en toutes lettres.

L'ADR 2385 garde sa règle là où elle s'applique : quand le code sait recalculer, il fait foi, et une
liste tenue à la main ne s'y substitue pas. Ce que celle-ci ajoute est le cas qu'elle n'avait pas
rencontré, celui où le code ne sait rien.

Le message du test parle d'« inventaire réel du code » pour toutes les clés, y compris ces deux-là.
La formule est devenue approximative ; la corriger toucherait un message que quatorze clés partagent,
et c'est un travail distinct.
