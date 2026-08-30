---
type: adr
title: "Une clôture sans trace ne se distingue pas d'une clôture absente"
status: stable
article: A17
chantier: "#4659 (EPIC #4650)"
decided_at: 2026-08-28
verification: probable
ratchet: 42
enforced_by:
  - ".github/scripts/verifie-cloture-consignee.sh"
verified:
  - by: machine:ci
    at: 2026-08-28
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-28
---

# Une clôture sans trace ne se distingue pas d'une clôture absente

## Contexte

Le dépôt écrit à **trois** endroits que tout chantier se clôt par douze passes : `CONTRIBUTING.md` §5
à l'indicatif, `dev-docs/cycle-de-chantier.md` avec la raison de chaque passe, et la compétence
`clore-un-chantier` dont la loi d'airain garde leur ordre.

Rien ne le vérifiait. Mesuré le 2026-08-28 sur les EPIC clos, corps **et** commentaires cherchés :

| | |
|---|---|
| EPIC clos | **64** |
| avec clôture consignée | **21** |
| sans | **43**, ramenées à **42** en rattrapant #4671 |

## La cause n'était pas l'inattention

C'est ce que la mesure a appris, et elle vaut mieux que la supposition qu'elle remplace.

L'EPIC #4671 a été clos le jour même **par les douze passes**, avec un bilan écrit et un artefact
visuel soumis. Il figure pourtant parmi les 43.

La raison tient en une ligne : **la compétence `clore-un-chantier` ne mentionnait nulle part le modèle
à coller**. Il vivait dans `dev-docs/cycle-de-chantier.md`, une page qu'il faut penser à ouvrir. Qui
suivait la compétence à la lettre ne laissait aucune trace, sans jamais rien oublier.

Une règle que sa propre compétence ne réclame pas n'est pas une règle mal suivie : c'est une règle qui
n'est demandée nulle part au moment où elle s'applique.

## Décision

**La passe 12 colle le modèle en commentaire sur l'EPIC, et un cliquet tient l'absence de trace.**

Le bilan **raconte**, la case **atteste**. Les deux sont nécessaires, et c'est la seconde qu'on oublie
parce qu'elle ne s'écrit pas, elle se coche.

## Un cliquet, pas un butoir

Quarante-deux clôtures manquent, après le rattrapage de #4671 - le seul des 43 dont les douze passes
avaient réellement eu lieu. Refuser tout net rendrait le dépôt rouge sans qu'aucune PR soit
fautive, et le garde se ferait désactiver la première semaine - le dépôt sait déjà qu'un avertisseur
qui crie sur l'historique existant s'apprend à ignorer dès le premier jour.

Le cliquet ne peut que **descendre**. Fermer un EPIC sans trace le fait monter à 43, et c'est ce
mouvement-là qui rougit. Il est déjà descendu une fois, le jour de son écriture.

**Les 42 sont assumées, pas rattrapées.** Rejouer douze passes sur un chantier clos depuis un an
n'aurait pas de sens ; le dire une fois, dans ce chiffre, en a.

## Ce que le garde ne prétend pas

Il cherche l'en-tête `## Clôture de chantier`. C'est une **convention**, pas une preuve : un EPIC peut
la porter sans que les passes aient eu lieu.

Il mesure donc l'**absence de trace là où la documentation la demande**, et rien d'autre. C'est
suffisant pour rendre la règle vérifiable, et c'est la raison du niveau `probable` plutôt que
`certaine` : le script rend des suspects, un humain juge.

## Pourquoi il vit dans `.github/scripts/`

Il interroge la forge, comme ses voisins de ce dossier. Les cliquets de `scripts/adr/` sont hors ligne
et tournent dans la batterie locale : y mettre celui-ci ferait rougir quiconque travaille sans réseau.

Placé ici, il peut **refuser plutôt que conclure** quand la forge ne répond pas, sans que ce refus
coûte à personne.

## Comment on saurait qu'elle est rompue

`.github/scripts/verifie-cloture-consignee.sh --auto-test` porte sept cas, dont **trois qui doivent
refuser**. Le premier est celui qui compte : un EPIC de plus sans trace doit faire rougir le garde.
Sans lui, tous ses verts ne vaudraient rien.

Les deux derniers tiennent le refus lui-même : une ADR sans cliquet lisible, et une ADR introuvable,
font **refuser** et non conclure.
