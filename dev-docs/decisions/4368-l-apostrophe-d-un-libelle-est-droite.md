---
type: adr
title: "L'apostrophe d'un libellé est droite, et le garde ne regarde que les libellés"
status: stable
article: A23
heuristiques: ["nielsen-4"]
chantier: "#4368 (passe 9 du chantier #4334)"
decided_at: 2026-08-24
verification: probable
enforced_by:
  - "scripts/adr/4368-apostrophe-en-libelle.py"
ratchet: 0
verified:
  - by: machine:suspects
    at: 2026-08-24
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-24
---

# L'apostrophe d'un libellé est droite, et le garde ne regarde que les libellés

## Contexte

`CONTRIBUTING.md` énonce la règle depuis #4343 : une seule apostrophe, la droite. Rien ne la tenait.

Mesure du 2026-08-24 : **188 apostrophes courbes dans 68 fichiers**.

| Où | Combien | Sort du dépôt ? |
|---|---:|---|
| `brief` | 97 | non |
| `src/main/java`, commentaires et javadoc | 65 | non |
| `src/main/java`, chaînes littérales | **12** | **oui** |
| `src/test/java` | 13 | non |
| `.github` | 1 | non |

Douze sur cent quatre-vingt-huit atteignaient un écran, réparties sur trois fichiers. Deux écrans
affichaient alors deux apostrophes différentes pour le même mot, et c'est le seul endroit où le
défaut se voit hors du dépôt.

## Décision

Les douze sont corrigées, et un garde tient la zone à **zéro** : une apostrophe courbe dans une
chaîne littérale de `src/main/java` est refusée.

Le garde ne regarde **que** cela. Il ne lit ni les commentaires, ni la javadoc, ni le brief, ni les
tests. La règle du registre vaut pour eux, et c'est la relecture qui la tient, comme le dit
l'article A31.

## Alternatives écartées

**Un cliquet à 188 sur tout le dépôt.** Il aurait fait porter une relecture de 68 fichiers à un
défaut qui n'en concernait que trois, et il aurait mis sur le même plan une apostrophe de commentaire
et une apostrophe d'écran. Un garde qui coûte plus qu'il ne rapporte se désactive.

**Ne rien faire.** Les douze restaient, et la règle de `CONTRIBUTING.md` restait un souhait. La
matrice de la constitution l'aurait compté comme tel.

## Conséquences

Ce garde vaut par ce qu'il **n'examine pas**. Son intérêt tient à une distinction que sa fixture
éprouve : la même apostrophe, dans une chaîne et dans le commentaire de la même ligne, doit compter
une fois et pas deux.

Les 176 restantes ne sont pas une dette tenue par un cliquet : elles sont hors du champ de cette
décision, et le disent.

## Amendement du 2026-08-24 : la règle vaut partout

La décision ci-dessus bornait le garde aux chaînes littérales de `src/main/java`, au motif que douze
apostrophes seulement atteignaient un écran et qu'un cliquet à 188 aurait fait porter une relecture
de 68 fichiers à un défaut qui n'en concernait que trois.

**Sébastien a tranché autrement le jour même : ce dépôt n'écrit que l'apostrophe ASCII, partout.**
L'alternative « un cliquet à 188 » reste écartée pour la raison donnée, mais la conclusion change :
il ne s'agit plus de tenir une dette, il s'agit de normaliser d'un coup et de tenir zéro.

172 occurrences normalisées dans 67 fichiers, plus un nom de fichier,
« Capacité d'analyse.md », dont la citation suit.

Trois exemptions restent, et aucune n'est un renoncement :

| Exempté | Pourquoi |
|---|---|
| `CHANGELOG.md` | engendré par semantic-release ; le corriger falsifierait le compte rendu, et sa **source** est le titre de PR, désormais gardé |
| les trois SVG de Mocodo | mesuré : leurs sources `.mcd` portent l'apostrophe **droite**, et l'outil substitue au rendu. Corriger le rendu serait défait à la régénération |
| le signe **cité** plutôt qu'employé | `COURBE = "’"`, une classe de caractères, « choisir la droite ou la courbe » : ces lignes parlent du caractère. L'effacer les rendrait fausses ([ADR 3645](3645-un-detecteur-textuel-s-exclut-de-son-corpus.md)) |

Le titre de PR est gardé à son tour, par `verifie-titre-pr.sh` : c'est la seule source du CHANGELOG,
et le raisonnement est celui que l'ADR 2843 tient déjà pour le cadratin.
