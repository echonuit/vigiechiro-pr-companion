---
type: adr
title: "Un renvoi porté par la javadoc ne se perd pas"
status: stable
article: A30
chantier: "#4395 (EPIC #4394, lot 2 du portage)"
decided_at: 2026-08-24
verification: certaine
enforced_by:
  - "scripts/adr/4395-renvois-en-javadoc.py"
floor: 4076
verified:
  - by: machine:ci
    at: 2026-08-24
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-24
---

# Un renvoi porté par la javadoc ne se perd pas

## Contexte

L'article A30 pose que l'ADR dit pourquoi, et que **la javadoc la cite au lieu de la redire**. Le
renvoi `#N` est cette citation : il donne au lecteur d'un contrat l'accès à la discussion qui a
produit la règle. La javadoc de production en porte **4 076**, dans 983 fichiers.

Le même article ouvre un cliquet de 3 641 lignes de prose narrative, sur 713 blocs. Les résorber
demande de raccourcir ces blocs, et c'est là que les deux exigences se croisent : **la citation vit
dans la prose qu'on coupe.**

## Le défaut

Un renvoi perdu ne casse rien. Il ne fait pas échouer la compilation, ne rougit aucun test, et
n'apparaît dans aucun diff comme autre chose qu'une ligne de prose en moins. Il cesse simplement
d'ouvrir, et personne ne s'en aperçoit avant de chercher pourquoi une règle est ce qu'elle est.

La mesure d'ouverture du lot 2 chiffre le danger. La ligne d'origine a déjà résorbé sa propre dette
de javadoc, et **367 fichiers** porteurs de dette ici ont là-bas un code identique à l'octet près :
sa javadoc contractée est donc une référence de rédaction directement applicable, pour 55 % de la
dette. Mais elle ne porte **aucun** renvoi. Sa rupture les a tous retirés, parce qu'ils n'ouvraient
plus rien dans un dépôt détaché de ses issues.

**Reprendre cette javadoc en bloc effacerait 1 882 renvois qui, ici, ouvrent de vraies issues.**
C'est la perte que l'ADR [4334](4334-ce-que-le-portage-a-decide-de-ne-pas-porter.md) a refusée au
grain du commit, revenue au grain du bloc de javadoc.

## Décision

Le nombre de renvois `#N` portés par les lignes `///` de `src/main/java` **ne descend pas**. Il est
tenu par un plancher déclaré, que `scripts/adr/4395-renvois-en-javadoc.py` vérifie à chaque passage.

Le plancher ouvre à **4 076**, mesuré sur `9da19a6f7`. Il se relève de ce qu'un chantier ajoute.
L'abaisser est possible, et c'est une décision : la justifier dans cette ADR, comme on justifie un
cliquet qui monte.

## Conséquences

**Sa polarité est l'inverse de celle des cliquets du dépôt, et le champ le dit.** Un cliquet compte
ce qu'on tolère : il borne par le haut, monter est la régression, et l'article A9 demande de le
resserrer. Un plancher compte ce qu'on possède : il borne par le bas, **descendre** est la perte, et
la bonne nouvelle est de le relever. Les deux ne peuvent pas partager un champ sans qu'un lecteur
pressé lise le mauvais sens, d'où `floor` à côté de `ratchet` dans l'en-tête, et
`rapporte_plancher` à côté de `rapporte` dans le socle.

**Le compte est global, pas par fichier.** Un plancher par fichier demanderait de figer 983 valeurs
et de les tenir à chaque édition légitime de javadoc : la discipline se paierait à chaque geste
honnête, pour attraper une faute qui ne se commet qu'en masse. Le garde ne verrait donc pas dix
renvois perdus dans un fichier compensés par dix ajoutés dans un autre. Ce n'est pas la menace ; la
menace est la reprise de tranche, et un plancher global la voit au premier passage.

**Seules les lignes `///` comptent.** Déplacer un renvoi d'un `///` vers un commentaire
d'implémentation sera compté comme une perte, alors que ce n'est qu'un choix de rédaction. La
contrepartie est assumée : elle pousse à garder la citation là où le lecteur du contrat la trouve,
et c'est exactement ce que A30 demande.

**`src/test/java` est hors champ**, comme pour le cliquet du même article. La javadoc de test n'a pas
encore de décision, et lui en donner une par ricochet serait légiférer sans avoir mesuré.

Le niveau est `certaine` et non `probable` : compter des renvois n'appelle aucun tri humain. Un
renvoi est là ou il n'y est pas.

## Alternatives écartées

- **Compter par fichier, en figeant 983 valeurs.** Plus fin, et plus fragile : la table périmerait à
  chaque édition légitime, et une table qu'on met à jour sans la lire ne garde plus rien.
- **Comparer au commit de base plutôt qu'à un plancher.** Cela verrait la perte au fichier près, mais
  ferait dépendre le garde d'une référence git que le poste local n'a pas toujours. Un garde qui ne
  se lance pas en local n'est lancé qu'en CI, c'est-à-dire trop tard pour celui qui coupe.
- **Ne rien garder et relire.** C'est ce qui a produit le défaut que cette décision prévient : la
  relecture voit une prose plus courte, elle ne voit pas un renvoi absent.

## La jurisprudence

[4334](4334-ce-que-le-portage-a-decide-de-ne-pas-porter.md) refuse la perte des renvois au grain du
commit, et chiffre ce qu'elle coûterait : 1 589 renvois. Celle-ci applique le même refus à la prose.

[2867](2867-une-dette-se-tient-par-un-cliquet.md) et
[2941](2941-un-cliquet-s-apprend-en-l-appliquant.md) posent le cliquet et disent que sa valeur
d'ouverture se mesure. Le plancher leur emprunte la mécanique et lui rend le sens inverse.
