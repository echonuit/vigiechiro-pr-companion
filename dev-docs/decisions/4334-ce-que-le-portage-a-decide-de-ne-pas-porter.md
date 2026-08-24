---
type: adr
title: "Ce que le portage de la méthode a décidé de ne pas porter"
status: stable
article: A5
chantier: "EPIC #4334, décision de clôture"
decided_at: 2026-08-24
verification: humaine
verification_note: "une décision de ne pas faire ne laisse pas de code derrière elle, et rien ne peut donc la garder ; elle se tient par ce document et par la mesure qu'il cite"
verified:
  - by: human:nedseb
    at: 2026-08-24
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-24
---

# Ce que le portage de la méthode a décidé de ne pas porter

## Contexte

La méthode de ce dépôt a été reprise depuis une ligne parallèle : `AGENTS.md`, la constitution, les
compétences, les cliquets. Cette ligne contenait **127 commits** depuis leur base de fusion commune,
`f03fd4a7` du 2026-08-20.

Une fusion à trois branches, jouée dans un clone jetable avant d'ouvrir le chantier, a rendu **54
fichiers en conflit sur environ 3 000**. Elle aurait été techniquement possible.

## Décision

**Vingt et un de ces commits ne sont pas portés, et ne le seront pas.**

Ils ne servent qu'à couper le lien avec le dépôt d'origine de cette ligne, qui a disparu. Ici ce lien
est **vivant**. Les reprendre aurait :

- effacé **1 589 renvois `#N`** de `dev-docs`, qui ouvrent de vraies issues de ce dépôt ;
- supprimé **35 ADR** et `CHANGELOG.md` ;
- renommé **toutes** les ADR, d'un numéro vers un slug.

`cliquet-renvois-a-un-depot-disparu.py` n'est pas porté non plus : il tient un compteur de renvois à
zéro, et les nôtres résolvent.

**L'article A27 n'est pas porté**, pour la même raison. Il interdit un renvoi qui ne résout que dans
un dépôt disparu, et n'a pas d'objet ici. La constitution saute donc de A26 à A28, et son préambule
dit pourquoi : un numéro qui change de sens est pire qu'un numéro absent.

**`dev-docs/registre-editorial.md` n'a pas été porté non plus, mais il a été réécrit.** L'original
auditait trente-cinq motifs sur un **autre corpus** ; l'importer aurait installé une mesure étrangère
sous une signature locale, ce que l'article A5 refuse. Toutes ses mesures ont été refaites ici
(#4367), et l'une d'elles a changé de valeur en changeant de corpus.

## Le principe, au-delà de ce chantier

**Un dispositif porté remesure ses seuils sur l'arbre qui le reçoit.**

Trois cliquets l'ont exigé, et l'un d'eux le montre bien : le cliquet de la javadoc valait **2 470**
dans la ligne d'origine, mesuré sur un arbre déjà nettoyé. Posé tel quel ici, il aurait rougi au
premier passage. Sa valeur mesurée sur cet arbre est **3 641**.

Le sens du portage n'est donc pas « copier ce qui marchait ailleurs », mais « poser le dispositif, et
lui demander ce qu'il mesure ici ». C'est aussi vrai des mesures écrites en prose : les chiffres de
la section « Le registre » de `CONTRIBUTING.md` ont tous été refaits.

## Comment cette décision est tenue

Elle ne l'est pas, et c'est déclaré. Une décision **de ne pas faire** ne laisse pas de code derrière
elle : aucun garde ne peut rougir sur ce qui n'a pas été écrit.

Ce qui la tient est ce document, et la mesure qu'il cite. Quiconque envisagerait de reprendre ces
vingt et un commits trouvera ici le nombre de renvois qu'il effacerait.

## Alternatives écartées

**Porter la ligne entière et revenir sur les 21 commits ensuite.** La fusion aurait été plus simple,
et le retour en arrière aurait porté sur 1 589 renvois déjà effacés. Un `revert` sur du texte
réécrit ne rend pas le texte d'avant.

**Ne rien porter.** L'ancien dépôt aurait gardé une méthode en prose dans un fichier ignoré par git,
et c'est précisément le défaut que #4335 a mesuré.
