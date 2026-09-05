---
type: adr
title: "Le dispositif de désignation d'un fichier se choisit en un endroit, pas dans chaque écran"
status: stable
article: A9
chantier: "#5282 (convergence des deux bancs filmés), lot #5307"
decided_at: 2026-09-06
verification: certaine
enforced_by:
  - "scripts/adr/5307-designation-hors-fabrique.py"
ratchet: 0
verified:
  - by: machine:suspects
    at: 2026-09-06
generated:
  by: "process:assistance-par-agents"
---

# Le dispositif de désignation d'un fichier se choisit en un endroit, pas dans chaque écran

## Contexte

`SelecteurFichier` est un port : trois méthodes, une implémentation réelle qui ouvre les dialogues
natifs de JavaFX, et un porteur `SelecteurFichierModifiable` que les tests remplacent. La couture de
**testabilité** existe donc depuis longtemps, et elle a une raison mécanique : un `showAndWait()`
natif fige un test TestFX headless.

La couture de **configuration**, elle, n'existait pas. Mesure du 2026-09-06 : le choix du dispositif
était écrit **douze fois** en dur, sous la forme `new SelecteurFichierModifiable(new
SelecteurFichierJavaFx(...))`, dans douze contrôleurs et actions de six paquets. Il n'y avait donc
aucun endroit où le changer.

Le changement `selecteur-de-repli` ajoute un second dispositif, dessiné par l'application, et un
réglage qui tranche. Sans couture, il aurait fallu écrire ce choix douze fois de plus.

## Décision

Le dispositif de désignation se construit dans **`Selecteurs`**, et nulle part ailleurs.

Les écrans demandent leur porteur à cette fabrique en lui passant leur fenêtre. Ils ne nomment plus
l'implémentation qu'ils obtiennent.

La fabrique ne prend pas encore le choix en paramètre : tant qu'un seul dispositif existe, un
paramètre à une seule valeur possible serait du décor, et son test un cas qui ne peut pas échouer.
Le lot qui apporte le réglage l'ajoutera là, avec un seul endroit à toucher.

## Conséquences

**Ce qu'on gagne.** Un endroit où le choix se lit et se change. Les douze écrans cessent de connaître
l'implémentation, ce qui est le sens du port qu'ils employaient déjà à moitié.

**Ce qu'on paie.** Une classe dont l'unique méthode tient en une ligne, et dont l'intérêt n'est pas
ce qu'elle fait mais le fait qu'elle soit seule. Elle paraîtra superflue à qui la lit sans son
histoire, et sa javadoc porte donc cette histoire.

**Pourquoi un cliquet à zéro plutôt que la discipline.** Manquer un des douze endroits **ne casse
rien** : l'écran oublié ouvre le dialogue du système, qui est le comportement par défaut. Il a l'air
juste. Le défaut ne se verrait qu'en filmant ce parcours-là, ou en le recevant du terrain, c'est-à-dire
trop tard et loin de sa cause.

C'est la forme de dette que ce dépôt tient par un compte qui ne remonte pas, et non par l'attention
de qui relit.

**La limite déclarée.** Le garde compte les constructions de `SelecteurFichierJavaFx` hors de la
fabrique, dans `src/main/java`, commentaires retirés. Une construction obtenue par réflexion, ou une
fabrique tierce qui la recopierait sous un autre nom, lui échapperaient. La première n'existe pas
dans ce dépôt ; la seconde se verrait en relecture, puisqu'elle demanderait d'écrire à nouveau ce que
`Selecteurs` écrit déjà.
