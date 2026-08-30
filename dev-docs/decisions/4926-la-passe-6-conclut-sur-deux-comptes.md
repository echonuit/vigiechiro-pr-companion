---
type: adr
title: "La passe de tests conclut sur deux comptes, seule exception à la règle d'une mesure par passe"
status: stable
article: A3
chantier: "#4926 (chantier #4925, sous #4828)"
decided_at: 2026-08-30
verification: humaine
loupe: "aucun motif ne lit si un compte suffit à conclure : la question se pose à l'écriture de chaque passe, et une seule y échappe"
verified:
  - by: humain
    at: 2026-08-30
---

# La passe de tests conclut sur deux comptes, seule exception à la règle d'une mesure par passe

## Contexte

L'orchestrateur des passes de clôture pose une règle générale :

> Une passe se conclut sur une **mesure reproductible**, ou elle ne se conclut pas.

Elle vaut pour les quatorze. La passe 6 en demande **deux**, et ce dépassement était silencieux : la
compétence l'écrivait, l'orchestrateur l'ignorait, et un lecteur trouvant les deux règles aurait
appliqué celle qu'il aurait vue en premier.

## Le défaut qu'un seul compte laisse passer

Les deux comptes ne mesurent pas la même chose, et chacun seul se laisse satisfaire sans que la
passe ait fait son travail.

| Le compte | Ce qu'il établit | Ce qu'il laisse passer, seul |
|---|---|---|
| tests **ajoutés par famille** | qu'on a couvert ce qui manquait, et **où** | des tests qui **passent** sans **juger** |
| survivants **éliminés** | que les tests jugent vraiment | qu'on a durci ce qu'on regardait, pas ce qui manquait |

**Des tests ajoutés sans survivant éliminé ne prouvent rien.** Un test vert sur du code qu'aucune
mutation ne fait rougir n'établit pas que le comportement tient ; il établit qu'il ne s'est rien
passé.

**Des survivants éliminés sans test ajouté dans la bonne famille laissent un usage entier dehors.**
Le dépôt porte cinq familles, et une seule couvre la ligne de commande : un test Java appelle le
service, il ne lance pas la commande. Éliminer des survivants sur le service ne dit rien de la
commande.

## Décision

**La passe 6 conclut sur deux comptes, et un seul ne conclut pas.** C'est la seule exception à la
règle d'une mesure par passe, et elle est nommée ici plutôt que laissée à découvrir.

L'orchestrateur porte désormais l'exception à l'endroit où il pose la règle, pour que les deux se
lisent ensemble.

## Pourquoi cette passe et pas une autre

Les treize autres se concluent sur un fait : le delta a été lu, la parité est établie ou sans objet,
la page ne dit plus rien de faux, le sas est vide de ce chantier. Un fait, une mesure.

La passe 6 pose une question à deux dimensions, la **couverture** et la **sévérité**, et elles
varient indépendamment. Un chantier peut monter l'une en laissant l'autre au même niveau, dans les
deux sens. Aucune mesure unique ne les résume sans en cacher une.

## Conséquences

**Le rendu de la passe est un couple, pas un nombre.** « Six tests ajoutés » ne conclut pas, « douze
survivants éliminés » non plus.

**Le compte par famille est par famille**, et non global. Un total masque exactement le défaut qu'il
doit révéler, une famille restée à zéro.

**Cette exception ne s'étend pas.** Une passe qui voudrait deux mesures parce qu'elle en a deux sous
la main n'est pas dans ce cas : ce qui la justifie ici est que les deux dimensions varient
indépendamment et qu'aucune ne borne l'autre.

## Alternatives écartées

- **Un seul compte, les survivants éliminés.** Il est le plus proche de la vérité, mais il ne dit
  rien de la famille où le test manque, et le dépôt a mesuré que la famille compte.
- **Un compte composite**, un ratio ou un score. Il se lit comme une note et cache lequel des deux
  axes a bougé, ce qui est précisément ce qu'on veut voir.
- **Laisser l'exception dans la seule compétence.** C'est l'état d'où l'on vient : deux règles
  opposées dans le dépôt, et le prochain lecteur applique celle qu'il trouve en premier.
