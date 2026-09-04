---
type: adr
title: "Une capacité se demande à la chose, elle ne se reconnaît pas à sa forme"
status: stable
article: A5
chantier: "#5102 (sous-chantier de #5006)"
decided_at: 2026-09-04
verification: humaine
verification_note: "aucun motif ne distingue un garde qui MESURE une capacité d'un garde qui la RECONNAÎT : les deux s'écrivent en quelques lignes de Python et se ressemblent. Les deux instances connues sont tenues par des témoins nommés dans le corps ; la règle elle-même se relit en revue"
verified:
  - by: human:nedseb
    at: 2026-09-04
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-04
---

# Une capacité se demande à la chose, elle ne se reconnaît pas à sa forme

## Contexte

Ce chantier existe parce qu'un relevé **devinait** ce qu'un garde était, au lieu de le lui demander.
Il a corrigé cela en donnant aux gardes un `--contrat`. Mais le même défaut est revenu **six fois
pendant le chantier**, dans les outils écrits pour le combattre :

| # | ce qui était mesuré | ce qui l'a faussé |
|---|---|---|
| 5032 | un garde « porte » `--auto-test` | une **mention** comptée pour un dispatch |
| 5103 | l'ADR qu'un garde rend | une **chaîne de fixture** lue comme un appel |
| 5128 | les noms qu'un module importe | un import **replié** par le formateur, invisible d'un motif de ligne |
| 5134 | un témoin déclaré | vérifié **existant**, jamais éprouvé |
| 5144 | la population qui déclare un contrat | un **grep** sur `--contrat`, non la déclaration |
| — | le groupe d'un outil | un `rapporte()` **homonyme**, défini dans le fichier même |

La clôture en a ajouté quatre de la même forme, dans ses propres mesures : `python3 scripts` compté
comme « 3 scripts », `dev-docs/` compté comme `docs/`, « gardent leur numéro » compté comme
« 9 gardes », et un contraste conclu sur un script **non déterministe**.

Six occurrences dans les outils qui existent pour ça, et quatre de plus en les mesurant, ne sont pas
six inattentions.

## Décision

**Quand un dispositif a besoin de savoir ce qu'une chose FAIT, il le lui demande.** Concrètement,
dans l'ordre de préférence :

1. **le lancer** et lire sa réponse, ce que fait `verifie_contrats_tiennent.py` sur ses 68 porteurs ;
2. à défaut, **lire sa déclaration par `ast`**, jamais son texte : un import replié, une docstring et
   une chaîne de fixture cessent alors d'être du code ;
3. et si aucun des deux n'est possible, **le dire** plutôt que d'estimer. La cécité déclarée d'un
   garde vaut mieux qu'un vert qui n'a rien lu.

**Une reconnaissance de forme reste permise pour RÉDUIRE des candidats, jamais pour conclure.** Une
heuristique d'écriture a rendu 15 générateurs sur 24 dans ce chantier, puis 7, puis 1 une fois
mesuré. Elle réduit ; c'est la lecture ou le lancement qui tranche.

## Ce que cette décision protège, et qu'on défera sans elle

`verifie_contrats_tiennent.py` lance **68 sous-processus** à chaque exécution, avec une barrière de
sûreté (`dispatche_en_code`) qui refuse de lancer un script ne portant pas la branche. C'est lent,
et c'est visiblement remplaçable par un `grep` sur `CONTRAT = {`.

**Ce `grep` serait plus rapide et faux**, exactement comme celui de #5144 : il compterait les
fichiers qui *parlent* d'un contrat. Sans cette ADR, l'optimisation se fera, parce qu'elle a l'air
d'un nettoyage.

Le coût est assumé et borné : la barrière de sûreté évite qu'un générateur interrogé ne réécrive des
fichiers, ce qui est arrivé - demander son contrat à `synchronise-adaptateurs.py` le faisait écrire
dans le dépôt avant #5154, et à `capture-screenshots.sh` lancer un build Maven.

## Pourquoi `humaine`

Aucun motif ne distingue un garde qui **mesure** d'un garde qui **reconnaît** : les deux s'écrivent
en quelques lignes et se ressemblent. Un contrôle mécanique de cette règle devrait lui-même juger de
l'intention d'un bout de code.

Les deux instances connues sont tenues, elles : le harnais porte un cas témoin pour l'homonyme
(#5103), et `dispatche_en_code` porte le sien pour la mention (#5032). La règle générale se relit en
revue, et cette ADR est ce que la revue cite.

## Ce qu'elle ne tranche pas

**Le coût.** Rien ne dit à partir de quelle population lancer devient déraisonnable. À 68 porteurs
la question ne se pose pas ; à mille elle se poserait, et la réponse ne serait pas de revenir au
`grep` mais de réduire la population.
