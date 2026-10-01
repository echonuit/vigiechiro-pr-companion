---
type: adr
title: "Une clôture sans trace ne se distingue pas d'une clôture absente"
status: stable
article: A17
chantier: "#4659 (EPIC #4650)"
decided_at: 2026-08-28
verification: probable
ratchet: 65
enforced_by:
  - ".github/scripts/verifie_cloture_consignee.py"
relations:
  completee_par: ["5335-la-profondeur-d-une-passe-ne-se-mecanise-pas"]
verified:
  - by: machine:ci
    at: 2026-08-28
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-28
---

# Une clôture sans trace ne se distingue pas d'une clôture absente

!!! warning "Ce qui fait foi aujourd'hui"
    **Complétée le 2026-10-01** par [ADR 5335](5335-la-profondeur-d-une-passe-ne-se-mecanise-pas.md) : cette ADR tient la FORME d'une clôture, et un garde la compte. La 5335 dit que sa PROFONDEUR ne se mécanise pas, parce qu'une trace est un auto-rapport dont un dispositif ne peut exiger que la forme. Trois candidats y ont été joués contre les deux traces de #5277, et aucun ne les sépare.

## Contexte

Le dépôt écrit à **trois** endroits que tout chantier se clôt par douze passes : `CONTRIBUTING.md` §5
à l'indicatif, `dev-docs/cycle-de-chantier.md` avec la raison de chaque passe, et la compétence
`clore-un-chantier` dont la loi d'airain garde leur ordre.

Rien ne le vérifiait. Mesuré le 2026-08-28 sur les EPIC clos **portant le label `epic`**, corps
**et** commentaires cherchés :

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

Le cliquet ne peut que **descendre**. Fermer un EPIC sans trace le fait monter d'un, et c'est ce
mouvement-là qui rougit. Il est déjà descendu une fois, le jour de son écriture.

**Elles sont assumées, pas rattrapées.** Rejouer quatorze passes sur un chantier clos depuis un an
n'aurait pas de sens ; le dire une fois, dans ce chiffre, en a.

## Révision du 2026-09-30 : la population était fausse d'un tiers

Le garde demandait `--label epic`, donc la **forge** filtrait pour lui, alors que le dépôt désigne
aussi un EPIC par le préfixe de son titre.

| Sur les 1 737 issues closes | |
|---|---|
| par le label, ce qu'il lisait | **96** |
| par l'union, ce qu'il devait lire | **154** |
| jamais lus | **58**, dont **23** sans trace |

D'où le cliquet à **65**. Le compte monte parce que la mesure commence à dire vrai, non parce qu'une
clôture a régressé, et les 23 sont assumées comme les 42 l'ont été.

Le contrôle : compter les manques parmi les 96 rend exactement **42**, le chiffre que cet en-tête
portait. La divergence était dans la population, pas dans la détection. Voir [ADR 4967].

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

`.github/scripts/verifie_cloture_consignee.py --auto-test` porte **28 cas, dont 5 qui doivent
refuser**. Le premier est celui qui compte : un EPIC de plus sans trace doit faire rougir le garde.
Sans lui, tous ses verts ne vaudraient rien.

Trois des cinq tiennent le refus lui-même : une ADR sans cliquet lisible, une ADR introuvable, et
la marque disparue du modèle.

Les **dix-neuf** derniers, ajoutés par #4967, éprouvent la **définition** d'un EPIC et la
**collecte** élargie, dont son refus **au plafond** : un corpus tronqué rend moins de manques, donc un
cliquet qui passe, et c'est le cinquième qui doit refuser. Ils vivent ici parce que le leurre du garde
injecte le corpus déjà constitué et court-circuite la requête.

[ADR 4967]: 4967-une-seule-definition-d-un-epic-pour-les-quatre-dispositifs.md
