---
type: adr
title: "Un tag de version sans publication se signale, et sa borne est la version et non le graphe"
status: stable
article: A3
chantier: "#5987, lot 2 du chantier #6005 (une publication peut s'arrêter en chemin)"
decided_at: 2026-10-06
verification: certaine
enforced_by:
  - ".github/scripts/signale_une_version_sans_publication.py"
  - ".github/scripts/resout_le_tag_de_version.py"
verification_note: "le garde lit les tags de version et les publications non brouillonnes, et signale ceux dont la version depasse la plus haute publiee ; huit cas dont deux sur la decision seule, et trois mutations jouees avec leur appariement annonce d avance, trois conformes - inverser la decision fait tomber le cas de decision et lui seul, retirer les noms du message fait tomber les deux cas qui les exigent, retirer le renvoi a la conduite a tenir fait tomber le cinquieme. Le calcul de la borne vient de la resolution du tag, eprouvee par sept cas et quatre mutations, pour n exister qu une fois"
relations:
  complete: ["4079-une-condition-de-job-nomme-l-etat-qu-elle-attend"]
verified:
  - by: machine:ci
    at: 2026-10-06
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-06
---

# Un tag de version sans publication se signale, et sa borne est la version et non le graphe

## Contexte

Une publication peut s'arrêter en chemin. Le train écrit quatre choses dans l'ordre - commit du
journal, tag, note git, Release en brouillon - et un échec à n'importe lequel de ces pas laisse une
version **à moitié faite**.

Cet état ne se voit pas. `main` porte un tag et une entrée de `CHANGELOG.md` d'apparence normale ;
il faut interroger la forge pour découvrir qu'il n'y a pas de publication. Le job est rouge, mais un
job rouge se lit « le train a échoué », pas « une version existe à moitié ».

Mesuré le 2026-10-06 : **17** tags de version sans publication, sur 512 tags et 495 publications.
Aucun garde ne le disait.

## Décision

Un garde signale, en fin de train, tout tag de version dont la **version** dépasse la plus haute
version **publiée**.

**Il ne signale pas « un tag sans publication »**, qui serait vrai 17 fois et donc inutile. La borne
distingue une version en cours d'une séquelle : seize des dix-sept forment un bloc du 20 au 22
juillet 2026, et le dix-septième est conservé par décision (#4083).

**La borne est la version, et non le graphe.** Deux règles plausibles ont été écartées par la mesure :

| Règle essayée | Ce que la mesure en dit |
|---|---|
| le plus haut tag sans publication | refuserait dès aujourd'hui : les 17 y entrent |
| les tags qui ne sont pas ancêtres de la dernière publiée | les sélectionnerait **toutes les 17** |

La seconde échoue pour une raison qui vaut au-delà de ce garde : les commits de ces tags **ne sont pas
sur `main`**, zéro sur dix-sept étant des ancêtres, une réécriture d'historique les ayant orphelinés.
Une mesure de graphe sur ces tags est donc **datée** - ce qui réconcilie le constat avec #4083, qui
mesurait en août une ancestralité alors vraie.

**Le calcul n'existe qu'une fois.** La résolution du tag de l'atelier pose la même question et en tire
l'autre lecture : un candidat y est le tag qu'une reprise doit reprendre, ici une version à signaler.
Le garde réemploie sa fonction plutôt que de recopier la borne, qui a demandé trois essais dont deux
faux.

## Conséquences

**Il s'éteint de lui-même.** Après un train réussi, la plus haute version publiée est la dernière :
aucun tag ne la dépasse, le garde est vert, et aucune liste d'exceptions n'est à tenir.

**Il n'est pas dans la porte des demandes.** Il interroge la forge, là où les gardes du dépôt jugent un
diff hors réseau. C'est **autant une alerte qu'un garde** : il ne bloque aucune fusion, il dit qu'une
version est à moitié faite.

**Sa condition porte `!cancelled()`**, et non l'état de `publish`. Sans fonction d'état, GitHub
l'envelopperait en `success()` sur tout le graphe amont et le sauterait dès que `release` rougit -
c'est-à-dire exactement dans le cas qu'il existe pour signaler. C'est le piège de l'ADR 4079, qui a
coûté les installeurs de la 2.186.0.

**Son silence n'est pas un verdict.** Une forge muette refuse, par un code de sortie **distinct** de
l'alerte, parce qu'ici une réponse vide signifierait « aucune publication », c'est-à-dire l'alerte
même. C'est l'inverse du sens que le même vide a pour le relevé des bancs instables, où il signifie
« rien à signaler » : le sens du vide appartient à l'appelant, et aucun module ne le tranche à sa
place.

## Ce qui n'est pas décidé ici

Lancer le train sur le tag plutôt que sur `main` : non essayé, et ce que semantic-release rend quand
la référence n'est pas une branche reste à établir. Les seize séquelles de juillet restent aussi en
place, les publier demanderait des artefacts construits hors de la chaîne, ce que #4083 a écarté.
