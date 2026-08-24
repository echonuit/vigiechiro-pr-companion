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
