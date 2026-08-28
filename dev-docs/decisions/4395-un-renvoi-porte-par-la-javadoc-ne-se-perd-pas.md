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
floor: 3193
inv_key: plancher-renvois
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
produit la règle. La javadoc de production cite **3 111 issues distinctes**, dans 983 fichiers.

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

**Reprendre cette javadoc en bloc effacerait 1 572 de ces renvois, qui ouvrent ici de vraies issues.**
C'est la perte que l'ADR [4334](4334-ce-que-le-portage-a-decide-de-ne-pas-porter.md) a refusée au
grain du commit, revenue au grain du bloc de javadoc.

## Décision

Le nombre d'issues **distinctes** que cite la javadoc de chaque fichier de `src/main/java`, sommé
sur le dépôt, **ne descend pas**. Il est tenu par un plancher déclaré, que
`scripts/adr/4395-renvois-en-javadoc.py` vérifie à chaque passage.

Distinctes, et non occurrences : ce qui se perd est qu'un fichier **cesse d'ouvrir** une discussion,
pas qu'il l'ouvre une fois au lieu de deux.

Le plancher vaut **<!--inv:plancher-renvois-->3 193<!--/inv-->**. Il se relève de ce qu'un chantier
ajoute, et #4441 lui a rendu **quinze** renvois d'un coup : le dépôt portait cinquante-trois
`(#…)`, le caractère de suspension au lieu d'un numéro, qu'aucun motif ne voyait puisque celui de ce
garde exige des chiffres. Chacun a été retrouvé par `git blame`, le commit qui a introduit la ligne
nommant son issue dans cinquante-trois cas sur cinquante-trois.

**3 136 → 3 160** (#4646). Le plancher annonçait « à relever » depuis on ne sait quand, et personne
ne le voyait : `rapport.py` n'avait aucun motif pour les lignes de plancher, et son verdict était
jeté avec celui des trois autres scripts qu'il ne savait pas lire (#4635).

Les vingt-quatre renvois gagnés ont été triés avant d'être verrouillés, le comptage sommant les
issues **distinctes par fichier** : déplacer un bloc d'un fichier à l'autre aurait fait monter le
total sans qu'aucun renvoi n'ait été ajouté.

| Nature | Fichiers | Renvois |
|---|---:|---:|
| fichiers neufs au corpus | 6 | +9 |
| fichiers qui montent | 11 | +15 |
| fichiers qui baissent | **0** | 0 |
| fichiers disparus | **0** | 0 |

Aucun fichier n'a baissé ni disparu : le déplacement redouté ne s'est pas produit, et les
vingt-quatre sont des ajouts. Le plancher les verrouille.

**3 160 → 3 193**, et le plancher de test **996 → 1 009**, relevés au fil des lots qui ont posé des
renvois. Ces relevés-là sont restés à la main, et deux d'entre eux ont laissé la prose de l'ADR en
arrière pendant que l'en-tête suivait : c'est ce que #4683 a outillé plutôt que corrigé une fois de
plus.

L'abaisser est possible, et c'est une décision : la justifier dans cette ADR, comme on justifie un
cliquet qui monte.

## Conséquences

**Sa polarité est l'inverse de celle des cliquets du dépôt, et le champ le dit.** Un cliquet compte
ce qu'on tolère : il borne par le haut, monter est la régression, et l'article A9 demande de le
resserrer. Un plancher compte ce qu'on possède : il borne par le bas, **descendre** est la perte, et
la bonne nouvelle est de le relever. Les deux ne peuvent pas partager un champ sans qu'un lecteur
pressé lise le mauvais sens, d'où `floor` à côté de `ratchet` dans l'en-tête, et
`rapporte_plancher` à côté de `rapporte` dans le socle.

**Trois verdicts, et deux d'entre eux refusent** (#4683). `perte` refuse parce qu'on a perdu ;
`a-relever` refuse parce qu'on n'a pas encore gardé ; seul `ok` passe. La deuxième moitié manquait :
un plancher périmé annonçait sa péremption et rendait `0`. Celui-ci l'a annoncée de #4441 à #4646
sans que personne ne la lise, et pendant tout ce temps le garde n'aurait rougi qu'en dessous de
3 136 : les vingt-quatre renvois gagnés au-dessus se reperdaient sans qu'il dise rien. Un gain n'est
gardé qu'une fois le seuil relevé.

**Ce que ce refus coûte, et pourquoi c'est le bon prix.** Ajouter un renvoi fait désormais rougir la
CI jusqu'à ce que le plancher suive. C'est le reproche que cette ADR adresse plus haut à une valeur
figée par fichier : faire payer la discipline à chaque geste honnête. Il est accepté ici parce que le
relevé est un geste et non trois - `scripts/methode/releve-les-planchers.py --ecrire` lit le verdict
du garde et pose la mesure aux trois endroits où un seuil s'écrit : l'en-tête `floor:`, la balise du
corps, celle du journal. Sans lui, le refus se paierait trois fois, et il y a un précédent : deux
balises restées en arrière ont fait rougir `DocumentationAJourTest` sous #4646, parce que le chiffre
y porte une espace **insécable** que le remplacement littéral manquait - et que le `grep` de
vérification manquait pour la même raison.

**Le total est global, pas figé par fichier.** Figer 983 valeurs et les tenir à chaque édition
légitime ferait payer la discipline à chaque geste honnête, pour attraper une faute qui ne se commet
qu'en masse. Le garde ne voit donc pas dix renvois perdus dans un fichier compensés par dix ajoutés
dans un autre. Ce n'est pas la menace ; la menace est la reprise de tranche, et un plancher global la
voit au premier passage.

**Ce que la première application a corrigé.** La version posée comptait les **occurrences**. Elle a
été réfutée par le premier fichier où elle pouvait l'être, dans l'heure : `AttenteTuiles.java` citait
`#3068` deux fois dans un bloc, la version contractée le cite une fois, et le garde a rougi sur une
réécriture qui ne perdait rien. Mesuré sur les deux états : 4 076 occurrences contre 4 075, et
**3 111 renvois distincts dans les deux cas**. Le plancher est passé de 4 076 à 3 111 sans qu'aucun
renvoi ne disparaisse - les deux nombres ne mesurent pas la même chose, et il faut le dire pour qu'on
ne lise pas cette baisse comme une perte. C'est #4398, et c'est
[2941](2941-un-cliquet-s-apprend-en-l-appliquant.md) à la lettre : sa première application est ce qui
révèle sa définition.

**Seules les lignes `///` comptent.** Déplacer un renvoi d'un `///` vers un commentaire
d'implémentation sera compté comme une perte, alors que ce n'est qu'un choix de rédaction. La
contrepartie est assumée : elle pousse à garder la citation là où le lecteur du contrat la trouve,
et c'est exactement ce que A30 demande.

**`src/test/java` est hors champ**, comme pour le cliquet du même article. La javadoc de test n'a pas
encore de décision, et lui en donner une par ricochet serait légiférer sans avoir mesuré.

Le niveau est `certaine` et non `probable` : compter des renvois n'appelle aucun tri humain. Un
renvoi est là ou il n'y est pas.

## Alternatives écartées

- **Figer une valeur par fichier, 983 en tout.** Plus fin, et plus fragile : la table périmerait à
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
