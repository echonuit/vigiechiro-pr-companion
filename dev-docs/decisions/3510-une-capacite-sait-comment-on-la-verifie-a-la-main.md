# ADR 3510 - Une capacité sait comment on la vérifie à la main

- **Statut** : Accepté - 2026-08-08
- **Chantier** : #3510, suite de l'[ADR 3505](3505-le-cycle-decrit-aussi-le-travail-pas-seulement-sa-cloture.md)
- **Vérification** : humaine - la complétude d'une recette ne se prouve pas par un scan. Le contrôle est
  la case `6b` du modèle de clôture, et le constat le plus lisible reste l'inventaire de statut de
  l'[index de la recette](../recette/index.md), qui dit ce qui n'a jamais été joué.

## Contexte

La passe 6 traitait la recette comme un **déversoir** :

> Ce qui ne peut pas être automatisé va en recette.

La formulation dit ce qu'on y met, jamais ce qu'il faut avoir fait pour que ce qu'on y met serve. Elle
fermait un mode de panne - « pas automatisable » qui devient silencieusement « pas vérifié » - et en
laissait ouvert un autre, plus difficile à voir.

## Le défaut que cette ADR ferme

**Une case écrite mais injouable a l'apparence d'une vérification prévue, et elle se coche.**

Trois façons d'y arriver, toutes atteignables sans faute de personne :

1. **la session propriétaire n'existe pas.** Un écran est déroulé en entier dans **une seule** session ;
   or S7 n'est pas écrite, S5 et S6 sont partielles, S4 et S8 sont écrites mais **jamais jouées**. Une
   capacité déposée là est réputée vérifiable par un script qui n'existe pas ;
2. **la case est déposée dans une session de transit**, où l'écran n'est pas propriétaire : elle sera
   jouée deux fois, ou jamais ;
3. **aucune fixture ne porte le cas.** La case se jouera alors sur une donnée fabriquée à la main pour
   l'occasion, qui ne reviendra pas à la campagne suivante. La vérification a lieu une fois, puis
   disparaît sans que personne ne l'ait décidé.

## Décision

**Une capacité ajoutée par un chantier n'est pas finie tant qu'on ne sait pas comment la vérifier à la
main, et que ce « comment » n'est pas écrit là où on le retrouvera.**

La recette cesse d'être ce qui reste après l'automatisation : elle est l'endroit où vit le **procédé de
vérification** d'une capacité. Le critère de complétude d'une case est la **rejouabilité** - quelqu'un
qui n'a pas fait le chantier doit pouvoir la refaire, dans six mois, autant de fois que nécessaire, et
obtenir la même chose.

Trois pièces, dont l'absence d'**une seule** ramène la case au rang d'intention :

| Pièce | Ce qu'elle répond | Ce qui arrive si elle manque |
| --- | --- | --- |
| le **geste** | qu'est-ce que je fais ? | la case se rejoue différemment à chaque campagne |
| l'**observation attendue** | qu'est-ce que je dois voir ? | on coche « ça marche » sans référence |
| la **fixture** | sur quelles données ? | la donnée se bricole, donc le résultat ne se compare pas |

La passe 6 demande donc quatre gestes : **désigner la session propriétaire** (et signaler en issue
qu'elle manque ou n'a jamais été jouée), **écrire les cases à leur place** pour un lecteur qui n'était
pas là, **fournir de quoi les jouer**, et **relire le statut de la session** dans l'index.

## Étendre le générateur de fixtures fait partie de la passe

C'est le seul point qui engage du travail au-delà de la rédaction, et il a été arbitré explicitement.

Les cartes SD de recette sont **générées** depuis une spec de quelques kilo-octets plutôt que stockées
en binaire, précisément pour revenir à l'identique. Quand un chantier introduit un cas qu'aucune fixture
existante ne porte - une nuit sans GPS, un journal qui contredit les WAV, un volume qui déborde -, la
spec s'étend dans le chantier qui a créé le besoin.

Différer la fixture reviendrait à écrire la case pour la forme : c'est exactement le « pas vérifié » que
la passe prétend éviter, avec en plus l'apparence du contraire. Le coût est faible au regard de ce
qu'il achète, la spec pesant quelques kilo-octets.

## Conséquences

- la recette devient l'**inventaire lisible** de la façon dont chaque capacité se vérifie, et non le
  registre de ce qui a échappé aux tests ;
- une capacité qui touche un écran sans session propriétaire **produit une issue** au lieu de disparaître
  dans un script inexistant. Le passif est ainsi rendu visible plutôt que réputé couvert ;
- la ligne **6b** du modèle de clôture porte cette exigence, à côté de la ligne 6 qui reste celle des
  tests automatisés. Deux lignes, parce que ce sont deux questions : *qu'est-ce qui empêche la
  régression ?* et *comment un humain confirme-t-il que ça marche ?*

## Ce que cette ADR apprend au-delà de son cas

**Un dispositif de vérification peut être vide sans être absent**, et c'est plus dangereux que son
absence : une case injouable se coche, une case manquante se voit. Le dépôt connaissait déjà cette forme
pour les tests, les scripts et les jobs de CI ([ADR 2748](2748-un-dispositif-qui-peut-ne-rien-verifier-le-dit.md)) ;
elle vaut aussi pour ce qui n'est pas exécuté par une machine.

Le critère qui la débusque est le même à chaque fois, et il porte sur les **conditions** du dispositif
plutôt que sur son verdict : *de quoi a-t-on besoin pour que ceci vérifie réellement quelque chose, et
l'a-t-on ?*
