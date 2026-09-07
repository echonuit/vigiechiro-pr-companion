---
type: adr
title: "Une règle que rien ne peut garder se déclare, au lieu de se donner un garde décoratif"
status: stable
article: A11
chantier: "#5414 (la méthode est écrite pour une session, et il en travaille huit), lot #5412"
decided_at: 2026-09-07
verification: humaine
enforced_by: []
verified:
  - by: humain
    at: 2026-09-07
generated:
  by: "process:assistance-par-agents"
---

# Une règle que rien ne peut garder se déclare, au lieu de se donner un garde décoratif

## Le contexte

Huit sessions d'agent travaillent ce dépôt en même temps, et la méthode a été écrite pour une. Le
6 septembre 2026, deux d'entre elles ont pris le **même** défaut à vingt-trois secondes d'écart, puis
une demande a été ouverte **neuf secondes** avant qu'une autre session ne fusionne le même correctif.

Le commentaire de prise et l'assignee étaient posés dans les deux cas, et n'ont rien empêché.

## La décision

Le chantier ajoute quatre gestes à la cérémonie : prévenir les pairs à la prise, relire l'état
distant avant de committer, recontrôler la tête distante à l'instant de pousser, et prévenir de ce
qui vient d'arriver sur `main` après une fusion.

**Ces quatre gestes ne sont tenus par aucun garde, et ce n'est pas un oubli.**

## Pourquoi aucun garde ne peut les tenir

**Un message entre sessions ne laisse aucune trace dans le dépôt.** Rien à lire, donc rien à
confronter.

**Et la forge n'enregistre pas qui a agi.** Toutes les sessions écrivent sous le même compte : ni
`gh pr list`, ni `gh issue view --json assignees`, ni `git log --format=%an` ne distinguent deux
sessions du même utilisateur. Une collision entre sessions est donc **indétectable en aval**, non par
manque d'ingéniosité, mais parce que l'information n'existe nulle part.

Écrire un garde ici produirait un dispositif qui n'observe rien - le témoin décoratif que ce dépôt
refuse partout ailleurs.

## Ce que cette décision contredit, et pourquoi c'est délibéré

L'[ADR 4829](4829-le-rattachement-d-une-issue-est-une-donnee-pas-de-la-prose.md) a établi qu'écrire
une règle **une fois de plus en prose** ne suffit pas : la règle du rattachement vivait déjà à trois
endroits quand trois sessions l'ont enfreinte le même jour. Sa réponse fut de la faire porter par une
**donnée** que la forge tient.

Ce chantier fait l'inverse, et la différence est la seule qui compte : **4829 avait une donnée
disponible**, le lien natif de sous-issue. Ici il n'en existe aucune. Le choix n'est pas entre la
prose et une donnée, il est entre la prose et rien.

L'article A11 le nomme : ce qui est assumé et écrit reste discutable, ce qui est contourné en silence
ne l'est plus.

## Ce que le protocole a fait, mesuré dans les deux sens

Il n'aurait **pas** empêché la première collision : vingt-trois secondes séparaient les deux prises,
et deux annonces se seraient croisées en vol. Il aurait vraisemblablement empêché la seconde, séparée
de soixante-douze minutes, qui est aussi la plus coûteuse - une branche en retard dont la fusion
aurait annulé un correctif déjà sur `main`.

Compter les deux ensemble dirait faux. Ce que le protocole change n'est pas la probabilité d'une
collision simultanée, c'est la **latence de découverte** de toutes les autres.

## Le signal partiel qui existe déjà

`git worktree list` distingue les sessions, là où l'assignee ne le peut pas : quarante-six worktrees
sur ce poste le 2026-09-07, dont onze à la session qui mesurait, les autres portant des branches
nommées par leur numéro d'issue. Il ne voit qu'une machine, et qu'une session ayant déjà créé son
worktree. C'est #5454.

## Ce qui périmerait cette décision

Une donnée qui distinguerait les sessions - un identifiant dans les commits, une marque sur les
demandes. Le jour où elle existe, cette ADR se relit : la prose redeviendrait un contournement.
