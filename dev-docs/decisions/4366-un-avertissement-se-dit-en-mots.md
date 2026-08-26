---
type: adr
title: "Un avertissement se dit en mots"
status: stable
heuristiques: ["nielsen-2", "nielsen-8", "gestalt-similarite"]
article: A28
chantier: "#4366 (passe 9 du chantier #4334)"
decided_at: 2026-08-24
verification: probable
enforced_by:
  - "scripts/adr/4366-avertissement-en-pictogramme.py"
ratchet: 0
inv_key: cliquet-pictogramme
verified:
  - by: machine:suspects
    at: 2026-08-24
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-24
---

# Un avertissement se dit en mots

## Contexte

Le corpus porte **1967 pictogrammes d'alerte**, répartis dans **617 fichiers**. La densité, plus
que le total, dit ce qui s'est passé :

| Fichier | Pictogrammes | Densité |
| --- | ---: | --- |
| `dev-docs/decisions/index.md` | 105 | un toutes les **3 lignes** |
| `scripts/doc-video/filme-un-parcours.sh` | 119 | un toutes les 17 lignes |
| `dev-docs/ci-cd-release.md` | 46 | un toutes les 29 lignes |

Sur une page où une ligne sur trois commence par « attention », le lecteur cesse de voir le signe.
Il ne hiérarchise plus rien. Le défaut est difficile à repérer : une page saturée de marqueurs a la
même apparence qu'une page où chaque marqueur compte.

## Le défaut

Le pictogramme n'apporte pas l'information : il annonce qu'il y en a une. L'information est dans la
phrase, ou elle n'y est pas. Un relevé sur le corpus le montre : la très grande majorité des
occurrences ouvrent une phrase qui dit déjà son alerte en toutes lettres - « Ce qu'il ne vérifie
**pas**, et il faut le savoir », « Ne pas redémarrer entre S7-12 et S7-13 ». Le signe y est
redondant. Là où il ne l'est pas, c'est pire : la phrase compte sur lui pour dire ce qu'elle ne dit
pas, et elle cesse d'être lisible sans lui.

## Décision

Ce qui doit alerter se dit **dans la phrase**, ou dans l'encart que le format prévoit pour cela -
un `!!! warning` MkDocs a un titre, une couleur et une place dans la page, ce qu'un caractère n'a
pas.

La résorption se fait par tranches, sous cliquet, et non par un nettoyage : c'est l'article A9. Le
motif est trivial à trouver et le remède ne l'est pas, ce qui est exactement la situation où une
passe mécanique unique fait des dégâts. Retirer le signe suffit quand la phrase porte déjà son
alerte ; sinon la phrase se réécrit.

## Conséquences

Le pictogramme subsiste là où il est le **contenu montré**, et le garde déclare quatre cécités.
Toutes disent la même chose : effacer y serait falsifier.

- Les nœuds `<text>` et `<tspan>` des maquettes, où le signe est ce que l'écran affiche.
- Les blocs de code d'un document markdown, qui citent ce qu'un programme émet.
- Les messages d'exécution, qui relèvent des articles sur le compte rendu et non de celui-ci. Ils se
  reconnaissent à la chaîne, ou à l'**appel qui émet** - `printf '⚠️ …'` - car une chaîne à
  guillemets simples ne se compte pas en français, où l'apostrophe est une lettre.
- Le signe **cité** plutôt qu'employé. C'est la distinction de la mention et de l'usage : « les
  libellés commençaient par un ⚠ » parle du caractère, il ne s'en sert pas pour alerter. L'effacer
  ne raccourcirait pas la phrase, il la rendrait fausse.

Le garde en retient **<!--inv:cliquet-pictogramme-->0<!--/inv-->**. Le reste est du contenu
montré, des blocs de code, des messages émis par le programme ou le caractère cité, et il ne les
compte pas. Le cliquet a ouvert à 1 539 ; il descend au fil des tranches que d'autres chantiers
résorbent.

**Il est monté une fois, et pour une raison qui n'est pas de la dette.** Il valait 1 468 tant que la
règle des mentions se contentait d'« un délimiteur de chaque côté ». Cette formulation laissait
passer la forme **la plus courante** d'un avertissement réel : `⚠️ **texte**` a un côté gauche vide,
que la règle acceptait, et un `*` à droite. En javadoc c'était pire, `///` fournissant le `/` à
gauche. **264 avertissements échappaient ainsi au compte.**

Une mention est **encadrée** : le même délimiteur ouvre et ferme, `« ⚠️ »` ou `` `⚠️` ``. C'est cette
règle-là qui s'applique désormais, et la montée à 1 799 est de la dette qui devient visible, jamais
de la dette ajoutée (#4464).

Cette valeur est celle de **cet** arbre. La décision vient d'un dépôt où le nettoyage avait déjà eu
lieu et où le cliquet valait zéro ; elle a été remesurée en arrivant ici, comme
[4334](4334-ce-que-le-portage-a-decide-de-ne-pas-porter.md) le demande de tout dispositif porté.

Deux bornes tiennent ces cécités, parce qu'une exemption sans borne devient une zone franche. Le
voisinage d'un autre marqueur se mesure à **32 caractères** de part et d'autre, et non sur la ligne
entière : une ligne qui porte une flèche quelque part ne doit pas cesser d'être gardée. Et un appel
qui émet n'exempte pas la ligne : sur `echo "⚠️ …"  # ⚠️ et ceci est de la prose`, le second signe
reste compté.

Le niveau est `probable`, pas `certaine`, pour la raison ordinaire : le script rend des suspects,
la relecture tranche.

Cette décision ne porte pas sur les pictogrammes de l'interface, qui relèvent de l'ADR
[« Un pictogramme est une icône, pas un caractère »](0035-un-pictogramme-est-une-icone-pas-un-caractere.md).
Les deux demandent la même chose : un signe qui porte du sens doit être un objet qu'on peut nommer,
mesurer et remplacer.

## La jurisprudence du cliquet

Le cliquet de cette décision suit deux ADR antérieures.
[2867](2867-une-dette-se-tient-par-un-cliquet.md) pose qu'une dette se tient par un compteur qui ne
remonte pas, plutôt que par un nettoyage qu'on remet.
[2941](2941-un-cliquet-s-apprend-en-l-appliquant.md) ajoute que sa valeur d'ouverture se mesure, et
que le resserrer est un geste distinct de le poser.

[3540](3540-un-cliquet-qui-compte-n-est-pas-la-preuve-de-la-regle.md) dit la limite : un compteur qui
ne monte pas prouve que rien ne s'ajoute, pas que la règle est comprise. C'est pourquoi le garde rend
des suspects et non des fautes.
