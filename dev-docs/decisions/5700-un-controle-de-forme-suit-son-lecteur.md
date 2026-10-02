---
type: adr
title: "Un contrôle de forme suit les lecteurs d'un tableau, et ne balaie pas les 651 du dépôt"
status: stable
article: A3
chantier: "#5700, passe 7 de la clôture de #5584 (651 tableaux, deux lus par un dispositif)"
decided_at: 2026-10-02
verification: certaine
enforced_by:
  - "scripts/adr/verifie_okf.py"
  - ".github/scripts/verifie_inventaires_ci.py"
verified:
  - by: machine:ci
    at: 2026-10-02
relations:
  complete: ["3348-ce-qui-se-cherche-doit-pouvoir-se-balayer"]
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-02
---

# Un contrôle de forme suit les lecteurs d'un tableau, et ne balaie pas les 651 du dépôt

## Contexte

L'issue #5700 a trouvé six lignes de `dev-docs/decisions/index.md` sans leur compte de cellules, et
son contrôle ne jugeait que ce fichier. La passe 0 de la clôture de #5584 a balayé le dépôt avec le
même motif : **697 fichiers markdown, 651 tableaux, quatre lignes amputées**, toutes dans
`dev-docs/ci-cd-release.md`. Trois des cinq pipes nus avaient été écrits le jour même par la session
qui livrait le contrôle, dont un dans la phrase qui expliquait qu'un pipe de prose doit être échappé.

Un accent grave ne protège pas un pipe dans un tableau markdown. Le rendu perd la colonne, et rien ne
le signalait.

## Décision

**Le contrôle vit dans `scripts/_commun/tableaux.py`, et il suit les dispositifs qui lisent déjà un
tableau.** Ils sont deux : `verifie_okf.py` sur l'index des ADR, `verifie_inventaires_ci.py` sur la
page des ateliers et des gardes, qui la parcourait pour juger sa **présence** sans lire sa **forme**.

**Et il ne devient pas un garde des 651 tableaux du dépôt.** C'est une décision de ne pas faire, et
elle se motive par trois mesures.

**Le dépôt est propre.** Après les cinq réparations, zéro ligne amputée sur 699 fichiers. Un garde
général naîtrait vert, donc sans rien à résorber, et son cliquet n'aurait aucune marge à relever.

**Je n'ai pas de témoin négatif pour les formes que je n'ai pas rencontrées.** Les 651 tableaux vivent
dans `docs/`, le brief, les compétences et les spikes ; un garde qui les juge tous peut refuser une
forme légitime dont je n'ai aucun exemplaire sous la main. Un garde qui crie sur du bon travail est un
garde qu'on apprend à ignorer (ADR 4002), et je ne peux pas écarter ce risque par une mesure.

**Les deux tableaux retenus sont ceux dont la forme a une CONSÉQUENCE lisible.** Une ligne amputée de
l'index des ADR fait perdre le chantier d'une décision ; une ligne amputée du tableau des gardes fait
perdre l'atelier où un garde tourne. Les deux sont confrontés par un dispositif qui les lit déjà, donc
le contrôle ne coûte aucune lecture de plus.

## Conséquences

**Le concept a une maison, et deux appelants.** Sa `verifie_grammaire()` est jouée depuis les deux,
comme `epics.verifie_grammaire` depuis #4967 : un gage qu'aucun harnais n'appelle est inerte, et
`verifie_gages_joues.py` le refuse (ADR 5483). Treize cas, cinq mutations, chacune nommant ses morts.

**La référence est l'en-tête le plus PROCHE au-dessus**, jamais le premier du fichier. Une session pair
a vu sa ligne atterrir 760 lignes plus bas dans un tableau plus large : son compte ne l'a attrapée que
parce que les largeurs différaient.

**La ligne de tirets se juge comme les autres.** Elle était écartée, et muter l'exception ne tuait
aucun cas : sur 651 tableaux, aucune ligne de tirets ne porte un compte différent de son en-tête. La
branche est partie après cette mesure, et non sur le seul constat qu'aucun cas ne mourait - retirer là
aurait pu enlever une protection réelle dont le cas manquait.

**Ce que cette décision laisse ouvert.** Le balayage complet existe, il a servi à la passe 0, et il
reste reproductible : `tableaux.amputees` sur `git ls-files -z '*.md'`. Qui voudra un garde général a
l'instrument et le chiffre d'ouverture ; ce qui lui manquera est le témoin négatif, et c'est ce qu'il
devra produire.
